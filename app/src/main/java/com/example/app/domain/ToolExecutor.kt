package com.example.app.domain

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.tools.*
import kotlinx.serialization.json.Json
import java.io.File

class ToolExecutor(workspaceDir: File, linuxEnv: LinuxEnvironment) {

    private val tools: Map<String, Tool> = listOf(
        ReadFileTool(workspaceDir),
        WriteFileTool(workspaceDir),
        ListFilesTool(workspaceDir),
        SearchFilesTool(workspaceDir),
        RunCommandTool(workspaceDir, linuxEnv)
    ).associateBy { it.name }

    suspend fun execute(name: String, arguments: String): ToolResult {
        val tool = tools[name] ?: return ToolResult("", name, false, "未知工具: $name")
        val args = parseArgs(arguments)
        return tool.execute(args).copy(name = name)
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parseArgs(raw: String): Map<String, String> {
        return try {
            val obj = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(raw)
            obj.mapValues { (_, v) ->
                if (v is kotlinx.serialization.json.JsonPrimitive) v.content else v.toString()
            }
        } catch (e: Exception) {
            mapOf("_raw" to raw)
        }
    }
}
