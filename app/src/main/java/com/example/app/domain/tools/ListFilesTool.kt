package com.example.app.domain.tools

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.Tool
import java.io.File

class ListFilesTool(private val workspaceDir: File) : Tool {
    override val name = "list_files"

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val subPath = arguments["path"]?.takeIf { it.isNotBlank() } ?: ""
        val dir = if (subPath.isEmpty()) workspaceDir else File(workspaceDir, subPath).canonicalFile
        if (!dir.path.startsWith(workspaceDir.canonicalPath)) {
            return ToolResult("", name, false, "路径越界: $subPath")
        }
        if (!dir.exists()) {
            return ToolResult("", name, false, "目录不存在: $subPath")
        }
        if (!dir.isDirectory) {
            return ToolResult("", name, false, "不是目录: $subPath")
        }
        return try {
            val entries = dir.listFiles()?.toList()?.sortedBy { it.name } ?: emptyList()
            val sb = StringBuilder()
            sb.appendLine("目录: ${dir.absolutePath}")
            sb.appendLine("共 ${entries.size} 项:")
            for (f in entries) {
                val prefix = if (f.isDirectory) "[D]" else "[F]"
                val size = if (f.isFile) " (${formatSize(f.length())})" else ""
                sb.appendLine("  $prefix ${f.name}$size")
            }
            ToolResult("", name, true, sb.toString())
        } catch (e: Exception) {
            ToolResult("", name, false, "列目录失败: ${e.message}")
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
