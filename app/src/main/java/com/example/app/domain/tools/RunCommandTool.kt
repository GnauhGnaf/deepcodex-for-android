package com.example.app.domain.tools

import com.example.app.data.api.models.ToolResult
import com.example.app.domain.LinuxEnvironment
import com.example.app.domain.Tool
import java.io.File

class RunCommandTool(
    private val workspaceDir: File,
    private val linuxEnv: LinuxEnvironment
) : Tool {
    override val name = "run_command"

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val command = arguments["command"] ?: return ToolResult("", name, false, "缺少参数: command")

        if (!linuxEnv.isReady) {
            return ToolResult("", name, false, "Linux 环境尚未初始化，请稍后再试")
        }

        return try {
            val result = linuxEnv.execCommand(command, workspaceDir)
            if (!result.success) {
                ToolResult("", name, false, "退出码: ${result.exitCode}\n${result.output}")
            } else {
                val display = result.output.ifEmpty { "(执行完毕，无输出)" }
                ToolResult("", name, true, display)
            }
        } catch (e: Exception) {
            ToolResult("", name, false, "执行失败: ${e.message}")
        }
    }
}
