package com.example.app.util

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal POSIX ustar TAR extractor — no external dependencies.
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
                // Read next block — if also all zeros, we're done
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

            // Handle GNU long name extensions
            val actualName: String
            val actualSize: Long
            val actualType: Int
            val actualLink: String

            if (typeFlag == 'L'.code) {
                // GNU long name — read name from data block
                val nameBuf = ByteArray(size.toInt())
                dataIn.readFully(nameBuf)
                val longName = nameBuf.readCString(0, nameBuf.size)
                skipPadding(dataIn, size)
                // Read next header (the actual entry)
                read = dataIn.read(buf)
                if (read < 512) break
                actualName = longName
                actualSize = buf.readOctal(124, 12)
                actualType = buf[156].toInt() and 0xFF
                actualLink = buf.readCString(157, 100)
            } else {
                actualName = name
                actualSize = size
                actualType = typeFlag
                actualLink = linkName
            }

            val destFile = File(destDir, actualName)

            when {
                actualType == '5'.code || actualType == 0 && actualName.endsWith("/") -> {
                    // Directory
                    destFile.mkdirs()
                }
                actualType == '2'.code -> {
                    // Symbolic link
                    destFile.parentFile?.mkdirs()
                    symlinks.add(destFile to actualLink)
                }
                actualType == '1'.code -> {
                    // Hard link — treat as symlink
                    destFile.parentFile?.mkdirs()
                    symlinks.add(destFile to actualLink)
                }
                else -> {
                    // Regular file (type '0' or '\0')
                    destFile.parentFile?.mkdirs()
                    val remaining = actualSize
                    FileOutputStream(destFile).use { out ->
                        var left = remaining
                        while (left > 0) {
                            val toRead = minOf(left, buf.size.toLong()).toInt()
                            dataIn.readFully(buf, 0, toRead)
                            out.write(buf, 0, toRead)
                            left -= toRead
                        }
                    }

                    // Restore execute bits for binaries/libs
                    if (mode.toInt() and 0b001_001_001 != 0
                        || actualName.contains("/bin/") || actualName.contains("/sbin/")
                        || actualName.endsWith(".so") || actualName.contains(".so.")
                        || actualName == "bin/busybox" || actualName.startsWith("bin/")
                        || actualName.startsWith("sbin/") || actualName.startsWith("lib/")
                    ) {
                        destFile.setExecutable(true)
                    }
                }
            }

            // Skip padding after file data
            if (actualType != 'L'.code) {
                skipPadding(dataIn, actualSize)
            }

            count++
        }

        return Pair(count, symlinks)
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
