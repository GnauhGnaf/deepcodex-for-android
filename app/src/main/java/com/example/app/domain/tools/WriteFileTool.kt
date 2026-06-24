package com.example.app.domain.tools

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.Tool
import com.example.app.domain.tools.PathUtil.stripWorkspacePrefix
import java.io.File

class WriteFileTool(private val workspaceDir: File) : Tool {
    override val name = "write_file"

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val rawPath = arguments["path"] ?: return ToolResult("", name, false, "缺少参数: path")
        val content = arguments["content"] ?: return ToolResult("", name, false, "缺少参数: content")
        val path = stripWorkspacePrefix(rawPath)
        val file = File(workspaceDir, path).canonicalFile
        if (!file.path.startsWith(workspaceDir.canonicalPath)) {
            return ToolResult("", name, false, "路径越界: $path")
        }
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult("", name, true, "文件已写入: $path (${content.length} 字符)")
        } catch (e: Exception) {
            ToolResult("", name, false, "写入失败: ${e.message}")
        }
    }
}
