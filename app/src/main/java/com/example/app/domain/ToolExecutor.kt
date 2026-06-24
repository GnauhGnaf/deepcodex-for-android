package com.example.app.domain

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.tools.*
import kotlinx.serialization.json.Json
import java.io.File

class ToolExecutor(initialWorkspaceDir: File, private val linuxEnv: LinuxEnvironment) {

    @Volatile
    var workspaceDir: File = initialWorkspaceDir
        private set

    private val toolsRef = java.util.concurrent.atomic.AtomicReference(createTools(initialWorkspaceDir))

    fun switchWorkspace(newDir: File) {
        workspaceDir = newDir
        newDir.mkdirs()
        toolsRef.set(createTools(newDir))
    }

    private fun createTools(dir: File): Map<String, Tool> = listOf(
        ReadFileTool(dir),
        WriteFileTool(dir),
        ListFilesTool(dir),
        SearchFilesTool(dir),
        RunCommandTool(dir, linuxEnv)
    ).associateBy { it.name }

    suspend fun execute(name: String, arguments: String): ToolResult {
        val tool = toolsRef.get()[name] ?: return ToolResult("", name, false, "未知工具: $name")
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
