package com.example.app.domain.tools

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.Tool
import java.io.File
import java.nio.file.FileSystems

class SearchFilesTool(private val workspaceDir: File) : Tool {
    override val name = "search_files"

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val pattern = arguments["pattern"] ?: return ToolResult("", name, false, "缺少参数: pattern")
        return try {
            val workspacePath = workspaceDir.canonicalPath.replace("\\", "/")
            val patternFull = "glob:$workspacePath/$pattern"
            val matcher = FileSystems.getDefault().getPathMatcher(patternFull)

            val matches = mutableListOf<String>()
            workspaceDir.walkTopDown().forEach { file ->
                val relPath = file.toRelativeString(workspaceDir)
                val absPath = file.toPath().toString().replace("\\", "/")
                if (matcher.matches(file.toPath())) {
                    val prefix = if (file.isDirectory) "[D]" else "[F]"
                    matches.add("  $prefix $relPath")
                }
            }

            if (matches.isEmpty()) {
                ToolResult("", name, true, "未找到匹配 '${pattern}' 的文件")
            } else {
                ToolResult("", name, true, "找到 ${matches.size} 个匹配:\n${matches.joinToString("\n")}")
            }
        } catch (e: Exception) {
            ToolResult("", name, false, "搜索失败: ${e.message}")
        }
    }
}
