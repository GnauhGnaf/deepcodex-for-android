package com.example.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileExportUtil {

    /**
     * Export a file to the public Downloads folder and show a Toast with the path.
     * On emulators there's no share target, so saving to Downloads is the reliable option.
     */
    fun exportFile(context: Context, source: File) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val dest = File(downloadsDir, source.name)
            source.inputStream().use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(context, "已保存: ${dest.absolutePath}", Toast.LENGTH_LONG).show()

            // Also try to share via intent, in case a file manager is available
            tryShare(context, dest)
        } catch (e: Exception) {
            // Fallback: share from app-private storage via FileProvider
            tryShare(context, source)
        }
    }

    private fun tryShare(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出文件: ${file.name}"))
        } catch (_: Exception) { }
    }

    /**
     * Zip an entire workspace directory and save to Downloads.
     */
    suspend fun exportWorkspace(context: Context, workspaceDir: File) {
        val zipFile = withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "export")
            cacheDir.mkdirs()
            val zip = File(cacheDir, "workspace_${System.currentTimeMillis()}.zip")

            ZipOutputStream(FileOutputStream(zip)).use { zos ->
                val basePath = workspaceDir.canonicalPath
                workspaceDir.walkTopDown().forEach { file ->
                    if (file == zip) return@forEach
                    val relativePath = file.canonicalPath.removePrefix(basePath).removePrefix("/")
                    if (relativePath.isEmpty()) return@forEach
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$relativePath/"))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(relativePath))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            zip
        }

        exportFile(context, zipFile)
    }
}
