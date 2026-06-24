package com.example.app.data.repository

import com.example.app.data.api.DeepSeekClient
import com.example.app.data.api.models.*
import com.example.app.domain.LinuxEnvironment
import com.example.app.domain.ToolDefinitions
import com.example.app.domain.ToolExecutor
import com.example.app.util.ConversationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

data class StreamEvent(
    val textDelta: String? = null,
    val reasoningDelta: String? = null,
    val toolCallStart: ToolCallDelta? = null,
    val toolResult: ToolResult? = null,
    val thinking: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
)

class ChatRepository(
    private val settingsRepository: SettingsRepository,
    private val client: DeepSeekClient,
    private val toolExecutor: ToolExecutor,
    private val conversationManager: ConversationManager,
    private val linuxEnv: LinuxEnvironment
) {
    companion object {
        val SYSTEM_PROMPT = """
You are a coding agent running on Android, powered by DeepSeek. You are precise, safe, and helpful.

# Language
Always respond in Chinese (简体中文). All explanations, descriptions, and chat messages must be in Chinese. Code and technical identifiers remain in English.

# Programming Environment
You are working inside an Alpine Linux (proot) environment on Android. The workspace directory `/workspace` maps to the app's workspace. The shell is `/bin/sh` (BusyBox ash with full scripting support).

Available runtimes:
- **Shell scripts**: Write .sh files and run with `run_command`. Full sh scripting.
- **Python 3**: Pre-installed. Write .py files and run with `run_command python3 script.py`. Use pip freely — mirror is Tsinghua (https://pypi.tuna.tsinghua.edu.cn/simple).
- **pip install**: Available! Use `run_command pip install <package>` to install any Python package.
- **C/C++**: Install build tools if needed with `apk add build-base`.
- **Any Alpine package**: `apk add <pkg>` via `run_command`.

Think like a programmer: write code to files, then execute them. Combine tools to build, test, and debug. You have a real Linux environment — use it.

# How you work
You communicate efficiently, always keeping the user informed. Be concise and direct. When solving problems:
1. Explore the project structure with list_files or search_files
2. Read relevant files
3. Write code changes with write_file
4. Execute and verify with run_command

# File path rules (CRITICAL)
ALL file tools (read_file, write_file, list_files) use relative paths. The workspace root is already set for you. Do NOT prefix paths with /workspace/ — just use the filename or relative path directly.
  ✓ Correct: "fibonacci.py", "src/main.py"
  ✗ Wrong:   "/workspace/fibonacci.py", "/workspace/src/main.py"
run_command executes inside /workspace (proot Linux), so shell commands see files at /workspace/fibonacci.py. This is the only tool where you use /workspace paths when running commands.

# Tools at your disposal
- read_file: Read a file in the workspace. path is relative (e.g. "fibonacci.py"), do NOT add /workspace
- write_file: Write a file in the workspace. path is relative, do NOT add /workspace
- list_files: List directory contents in the workspace. path is relative, do NOT add /workspace
- search_files: Find files matching a glob pattern (e.g., **/*.kt)
- run_command: Execute a shell command inside the /workspace directory in proot Linux

# Style
- Use tools to gather information before answering
- Write complete, runnable code files rather than snippets
- Explain your actions briefly before using tools
- Report results clearly after tool execution
- Be proactive: if you can solve it with tools, do it without asking
""".trimIndent()
    }

    fun initSession() {
        conversationManager.clear()
        conversationManager.addSystem(SYSTEM_PROMPT)
    }

    fun sendMessage(userInput: String): Flow<StreamEvent> = flow {
        conversationManager.addUser(userInput)

        val apiKey = settingsRepository.apiKey.first()
        val baseUrl = settingsRepository.baseUrl.first()
        val model = settingsRepository.model.first()

        if (apiKey.isBlank()) {
            emit(StreamEvent(error = "请先在设置中配置 API Key"))
            return@flow
        }

        // Ensure Linux environment is ready before processing
        if (!linuxEnv.isReady) {
            emit(StreamEvent(textDelta = "正在初始化 Linux 环境...\n"))
            val setupResult = linuxEnv.setup()
            if (setupResult.isFailure) {
                emit(StreamEvent(error = "Linux 环境初始化失败: ${setupResult.exceptionOrNull()?.message}"))
                return@flow
            }
            emit(StreamEvent(textDelta = "${setupResult.getOrThrow()}\n\n"))
        }

        // No hard turn limit — same as Codex's `loop {}`.
        // The model breaks naturally via finish_reason:stop, or the
        // context window bounds it.
        while (true) {
            val request = buildRequest(model)
            val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
            val accumulatedText = StringBuilder()
            var isThinking = false

            client.streamChat(apiKey, baseUrl, request).collect { chunk ->
                if (chunk.choices.isEmpty()) return@collect
                val choice = chunk.choices[0]
                val delta = choice.delta

                if (delta.reasoningContent != null && delta.content == null) {
                    if (!isThinking) {
                        isThinking = true
                        emit(StreamEvent(thinking = true))
                    }
                    emit(StreamEvent(reasoningDelta = delta.reasoningContent))
                    return@collect
                }
                if (delta.content != null) {
                    if (isThinking) {
                        isThinking = false
                        emit(StreamEvent(thinking = false))
                    }
                    accumulatedText.append(delta.content)
                    emit(StreamEvent(textDelta = delta.content))
                }

                if (delta.toolCalls != null) {
                    for (tc in delta.toolCalls) {
                        val builder = toolCalls.getOrPut(tc.index) { ToolCallBuilder() }
                        if (tc.id != null) builder.id = tc.id
                        if (tc.function?.name != null) builder.name = tc.function.name
                        if (tc.function?.arguments != null) builder.args.append(tc.function.arguments)
                        if (builder.id != null && builder.name != null) {
                            emit(StreamEvent(toolCallStart = tc))
                        }
                    }
                }
            }

            if (toolCalls.isNotEmpty()) {
                val assistantToolCalls = toolCalls.values.map { builder ->
                    ToolCallMsg(
                        id = builder.id!!,
                        function = FunctionCall(name = builder.name!!, arguments = builder.args.toString())
                    )
                }
                val text = accumulatedText.toString().trim().ifEmpty { null }
                conversationManager.addAssistant(content = text, toolCalls = assistantToolCalls)

                for (tc in assistantToolCalls) {
                    val result = toolExecutor.execute(tc.function.name, tc.function.arguments)
                    emit(StreamEvent(toolResult = result.copy(toolCallId = tc.id)))
                    conversationManager.addToolResult(tc.id, result.name, result.output)
                }
                continue
            } else {
                val text = accumulatedText.toString().trim()
                if (text.isNotEmpty()) {
                    conversationManager.addAssistant(content = text)
                } else {
                    val fallback = "（模型思考完毕，但未生成回复，请重试）"
                    emit(StreamEvent(textDelta = fallback))
                    conversationManager.addAssistant(content = fallback)
                }
                break
            }
        }

        emit(StreamEvent(done = true))
    }

    private fun buildRequest(model: String): ChatRequest {
        return ChatRequest(
            model = model,
            messages = conversationManager.history,
            tools = ToolDefinitions.allTools,
            toolChoice = "auto"
        )
    }

    fun clearHistory() {
        conversationManager.clear()
        conversationManager.addSystem(SYSTEM_PROMPT)
    }
}

private class ToolCallBuilder {
    var id: String? = null
    var name: String? = null
    val args = StringBuilder()
}
