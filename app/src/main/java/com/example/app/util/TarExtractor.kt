package com.example.app.util

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal POSIX ustar TAR extractor — no external dependencies.
 * Supports GNU long-name, ustar prefix, and PAX extended headers.
 * Replaces Apache Commons Compress to avoid java.nio.file.LinkOption on API < 26.
 */
object TarExtractor {

    data class TarEntry(
        val name: String,
        val isDirectory: Boolean,
        val isSymbolicLink: Boolean,
        val linkName: String,
        val mode: Int,
        val size: Long
    )

    fun extract(input: InputStream, destDir: File): Pair<Int, List<Pair<File, String>>> {
        // Auto-detect gzip compression
        val buffered = BufferedInputStream(input)
        buffered.mark(2)
        val isGz = buffered.read() == 0x1F && buffered.read() == 0x8B
        buffered.reset()
        val dataIn = DataInputStream(if (isGz) GZIPInputStream(buffered) else buffered)
        val symlinks = mutableListOf<Pair<File, String>>()
        val buf = ByteArray(512)
        var count = 0
        var paxOverrides: Map<String, String>? = null

        while (true) {
            // Read header (512 bytes)
            var read = 0
            while (read < 512) {
                val n = dataIn.read(buf, read, 512 - read)
                if (n < 0) break
                read += n
            }
            if (read < 512) break

            // Check for end-of-archive (two zero blocks)
            if (buf.all { it == 0.toByte() }) {
                read = 0
                while (read < 512) {
                    val n = dataIn.read(buf, read, 512 - read)
                    if (n < 0) break
                    read += n
                }
                break
            }

            // Parse header fields
            val name = buf.readCString(0, 100)
            if (name.isEmpty()) break

            val mode = buf.readOctal(100, 8)
            val size = buf.readOctal(124, 12)
            val typeFlag = buf[156].toInt() and 0xFF
            val linkName = buf.readCString(157, 100)

            // Read ustar prefix field (offset 345, 155 bytes)
            val ustarMagic = buf.readCString(257, 6)
            val prefix = if (ustarMagic == "ustar") buf.readCString(345, 155) else ""

            // Handle PAX extended header (type 'x') — stores full path for the NEXT entry
            if (typeFlag == 'x'.code) {
                paxOverrides = parsePax(dataIn, size.toInt())
                skipPadding(dataIn, size)
                continue
            }

            // Handle GNU long name (type 'L') — stores full name for the NEXT entry
            if (typeFlag == 'L'.code) {
                val nameBuf = ByteArray(size.toInt())
                dataIn.readFully(nameBuf)
                val longName = nameBuf.readCString(0, nameBuf.size)
                skipPadding(dataIn, size)
                // Read next header (the actual entry)
                read = dataIn.read(buf)
                if (read < 512) break
                val actualName = longName
                val actualSize = buf.readOctal(124, 12)
                val actualType = buf[156].toInt() and 0xFF
                val actualLink = buf.readCString(157, 100)
                val actualMode = buf.readOctal(100, 8)
                extractEntry(dataIn, destDir, actualName, actualType, actualLink,
                    actualMode, actualSize, buf, symlinks)
                count++
                continue
            }

            // Build actual name: PAX path > ustar prefix > header name
            val actualName = paxOverrides?.get("path") ?: if (prefix.isNotEmpty()) "$prefix/$name" else name
            val actualSize = paxOverrides?.get("size")?.toLongOrNull() ?: size
            val actualLink = paxOverrides?.get("linkpath") ?: linkName

            extractEntry(dataIn, destDir, actualName, typeFlag, actualLink,
                mode, actualSize, buf, symlinks)

            paxOverrides = null
            count++
        }

        return Pair(count, symlinks)
    }

    private fun extractEntry(
        dataIn: DataInputStream,
        destDir: File,
        name: String,
        typeFlag: Int,
        linkName: String,
        mode: Long,
        size: Long,
        buf: ByteArray,
        symlinks: MutableList<Pair<File, String>>
    ) {
        val destFile = File(destDir, name)

        when {
            typeFlag == '5'.code || typeFlag == 0 && name.endsWith("/") -> {
                destFile.mkdirs()
            }
            typeFlag == '2'.code -> {
                destFile.parentFile?.mkdirs()
                symlinks.add(destFile to linkName)
            }
            typeFlag == '1'.code -> {
                destFile.parentFile?.mkdirs()
                symlinks.add(destFile to linkName)
            }
            else -> {
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { out ->
                    var left = size
                    while (left > 0) {
                        val toRead = minOf(left, buf.size.toLong()).toInt()
                        dataIn.readFully(buf, 0, toRead)
                        out.write(buf, 0, toRead)
                        left -= toRead
                    }
                }

                if (mode.toInt() and 0b111_111_111 != 0
                    || name.contains("/bin/") || name.contains("/sbin/")
                    || name.endsWith(".so") || name.contains(".so.")
                    || name == "bin/busybox" || name.startsWith("bin/")
                    || name.startsWith("sbin/") || name.startsWith("lib/")
                    || name.contains("/lib/") || name.contains("/libexec/")
                ) {
                    destFile.setExecutable(true)
                }
            }
        }

        // Skip padding after file data (only for types that have data: file, not dir/symlink)
        if (typeFlag != '5'.code && typeFlag != '2'.code && typeFlag != '1'.code) {
            skipPadding(dataIn, size)
        }
    }

    /** Parse PAX extended header data: "len key=value\n" records. */
    private fun parsePax(dataIn: DataInputStream, dataSize: Int): Map<String, String> {
        val paxData = ByteArray(dataSize)
        dataIn.readFully(paxData)
        val text = paxData.toString(Charsets.UTF_8)
        val map = mutableMapOf<String, String>()
        var pos = 0
        while (pos < text.length) {
            val space = text.indexOf(' ', pos)
            if (space < 0) break
            val recLen = text.substring(pos, space).toIntOrNull() ?: break
            if (recLen <= 0 || pos + recLen > text.length) break
            val record = text.substring(space + 1, pos + recLen - 1) // -1 for trailing \n
            val eq = record.indexOf('=')
            if (eq > 0) {
                map[record.substring(0, eq)] = record.substring(eq + 1)
            }
            pos += recLen
        }
        return map
    }

    private fun skipPadding(input: DataInputStream, size: Long) {
        val padding = ((size + 511) / 512 * 512 - size).toInt()
        if (padding > 0) input.skipBytes(padding)
    }

    private fun ByteArray.readCString(offset: Int, maxLen: Int): String {
        val end = (offset until offset + maxLen).firstOrNull { this[it] == 0.toByte() } ?: (offset + maxLen)
        return String(this, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun ByteArray.readOctal(offset: Int, len: Int): Long {
        var result = 0L
        for (i in offset until offset + len) {
            val b = this[i].toInt() and 0xFF
            if (b == 0 || b == ' '.code) break
            if (b in '0'.code..'7'.code) {
                result = (result shl 3) or (b - '0'.code).toLong()
            }
        }
        return result
    }
}
