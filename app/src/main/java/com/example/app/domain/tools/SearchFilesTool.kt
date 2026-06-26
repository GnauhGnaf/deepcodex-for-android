package com.example.app.domain.tools

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.Tool
import java.io.File

class SearchFilesTool(private val workspaceDir: File, private val sharedSkillsDir: File? = null) : Tool {
    override val name = "search_files"

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val pattern = arguments["pattern"] ?: return ToolResult("", name, false, "缺少参数: pattern")
        return try {
            // If pattern targets .codex/skills, redirect to shared directory
            val searchRoot = if (sharedSkillsDir != null && pattern.startsWith(".codex/skills")) {
                sharedSkillsDir
            } else {
                workspaceDir
            }
            val patternSuffix = if (sharedSkillsDir != null && pattern.startsWith(".codex/skills")) {
                pattern.removePrefix(".codex/skills/")
            } else {
                pattern
            }
            val regex = globToRegex(patternSuffix)

            val matches = mutableListOf<String>()
            searchRoot.walkTopDown().forEach { file ->
                val relPath = file.toRelativeString(searchRoot)
                if (regex.matches(relPath)) {
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

    private fun globToRegex(glob: String): Regex {
        var sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        sb.append(".*")
                        i += 2
                        if (i < glob.length && glob[i] == '/') i++
                        continue
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.' -> sb.append("\\.")
                '{' -> {
                    val end = glob.indexOf('}', i)
                    if (end > i) {
                        sb.append("(")
                        glob.substring(i + 1, end).split(',').joinTo(sb, "|")
                        sb.append(")")
                        i = end
                    } else {
                        sb.append("\\{")
                    }
                }
                '[' -> sb.append('[')
                ']' -> sb.append(']')
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '+' -> sb.append("\\+")
                '\\' -> sb.append("\\\\")
                else -> sb.append(c)
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString())
    }
}
