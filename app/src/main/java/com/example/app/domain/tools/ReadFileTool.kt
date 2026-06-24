package com.example.app.domain.tools

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.Tool
import com.example.app.domain.tools.PathUtil.stripWorkspacePrefix
import java.io.File

class ReadFileTool(private val workspaceDir: File) : Tool {
    override val name = "read_file"

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val rawPath = arguments["path"] ?: return ToolResult("", name, false, "缺少参数: path")
        val path = stripWorkspacePrefix(rawPath)
        val file = File(workspaceDir, path).canonicalFile
        if (!file.path.startsWith(workspaceDir.canonicalPath)) {
            return ToolResult("", name, false, "路径越界: $path")
        }
        if (!file.exists()) {
            return ToolResult("", name, false, "文件不存在: $path")
        }
        if (!file.isFile) {
            return ToolResult("", name, false, "不是文件: $path")
        }
        return try {
            ToolResult("", name, true, file.readText())
        } catch (e: Exception) {
            ToolResult("", name, false, "读取失败: ${e.message}")
        }
    }
}
