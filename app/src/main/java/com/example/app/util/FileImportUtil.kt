package com.example.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileImportUtil {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Import files from URIs into a subfolder of the target directory.
     * Returns the import folder name.
     */
    suspend fun importFiles(
        context: Context,
        uris: List<Uri>,
        targetDir: File
    ): String = withContext(Dispatchers.IO) {
        val folderName = "导入_${dateFormat.format(Date())}"
        val importDir = File(targetDir, folderName)
        importDir.mkdirs()

        var success = 0
        var failed = 0

        for (uri in uris) {
            val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val destFile = uniqueFile(importDir, fileName)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                success++
            } catch (e: Exception) {
                failed++
            }
        }

        withContext(Dispatchers.Main) {
            val msg = if (failed == 0) {
                "已导入 $success 个文件到 $folderName/"
            } else {
                "导入 $success 个文件，$failed 个失败 → $folderName/"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        folderName
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path?.substringAfterLast('/')
        }
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        val suffix = if (ext.isNotEmpty() && base != name) ".$ext" else ""
        val stem = if (ext.isNotEmpty() && base != name) base else name
        var i = 1
        while (file.exists()) {
            file = File(dir, "${stem}_($i)$suffix")
            i++
        }
        return file
    }
}
