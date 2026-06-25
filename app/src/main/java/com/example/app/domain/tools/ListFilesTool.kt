package com.example.app.domain.tools

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.Tool
import com.example.app.domain.tools.PathUtil.isPathSafe
import com.example.app.domain.tools.PathUtil.resolvePath
import com.example.app.domain.tools.PathUtil.stripWorkspacePrefix
import java.io.File

class ListFilesTool(private val workspaceDir: File, private val sharedSkillsDir: File? = null) : Tool {
    override val name = "list_files"

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val rawPath = arguments["path"]?.takeIf { it.isNotBlank() } ?: ""
        val subPath = stripWorkspacePrefix(rawPath)
        val dir = if (subPath.isEmpty()) workspaceDir else resolvePath(rawPath, workspaceDir, sharedSkillsDir)
        if (!isPathSafe(dir, workspaceDir, sharedSkillsDir)) {
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
            sb.appendLine("目录: $subPath")
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
