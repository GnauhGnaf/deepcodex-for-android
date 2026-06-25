package com.example.app.data.repository

import com.example.app.data.api.DeepSeekClient
import com.example.app.data.api.models.*
import com.example.app.domain.LinuxEnvironment
import com.example.app.domain.ToolDefinitions
import com.example.app.domain.ToolExecutor
import com.example.app.util.ConversationManager
import kotlinx.coroutines.delay
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
    private val toolExecutorProvider: () -> ToolExecutor,
    private val conversationManager: ConversationManager,
    private val linuxEnv: LinuxEnvironment
) {
    companion object {
        val SYSTEM_PROMPT = """
You are a coding agent running on Android, powered by Codeeps. You are precise, safe, and helpful.

# Language
Always respond in Chinese (简体中文). All explanations, descriptions, and chat messages must be in Chinese. Code and technical identifiers remain in English.

# Programming Environment
You are working inside an Alpine Linux (proot) environment on Android. The workspace directory `/workspace` maps to the app's workspace. The shell is `/bin/sh` (BusyBox ash with full scripting support).

Available runtimes:
- **Shell scripts**: Write .sh files and run with `run_command`. Full sh scripting.
- **Python 3**: Pre-installed. Write .py files and run with `run_command python3 script.py`.
- **pip install**: Run `run_command pip install <package>`. The system is pre-configured with Tsinghua mirror and allows system-wide installs. If `pip install <pkg>` fails, try `pip install <pkg> --break-system-packages` as fallback. NO virtual environments needed.
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

# Skills
You have access to **skills** — curated guides for common tasks. Skills are stored as directories under `.codex/skills/`, each containing a `SKILL.md` file plus optional scripts and references.
- List available skills: use `list_files` on `.codex/skills` — each directory is a skill
- Read a skill: use `read_file` on `.codex/skills/<skill-name>/SKILL.md`
- Skill scripts: Python/Shell scripts inside skill directories can be run via `run_command`. Example: `run_command python3 /skills/ocr-image/scripts/ocr.py image.jpg`
- Install new skills: use `run_command sh /skills/skills.sh list` to browse, then `run_command sh /skills/skills.sh install <name>` to install from skills.sh registry
- Create a new skill: use `write_file` to create `.codex/skills/<skill-name>/SKILL.md`
- When starting a task, check if any relevant skill exists and read it first
- Skills provide environment-specific best practices and templates
- If no skill matches the task, proceed normally with your own expertise

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

    fun initSession(convId: String? = null, messages: List<Message>? = null) {
        if (convId != null && messages != null) {
            conversationManager.loadConversation(convId, "", messages)
        } else {
            conversationManager.clear()
            if (convId != null) {
                conversationManager.newSession(convId)
            }
        }
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

        var turnCount = 0
        val maxTurns = 50

        while (turnCount < maxTurns) {
            turnCount++
            val request = buildRequest(model)
            val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
            val accumulatedText = StringBuilder()
            var isThinking = false

            // Retry wrapper for network errors
            var lastError: Throwable? = null
            var success = false
            for (attempt in 1..3) {
                try {
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
                                    // Emit accumulated builder state — raw delta may lack id during streaming
                                    emit(StreamEvent(toolCallStart = ToolCallDelta(
                                        index = tc.index,
                                        id = builder.id,
                                        function = FunctionDelta(name = builder.name, arguments = builder.args.toString())
                                    )))
                                }
                            }
                        }
                    }
                    success = true
                    break
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < 3) {
                        val backoff = (attempt * 1000).coerceAtMost(3000).toLong()
                        delay(backoff)
                    }
                }
            }
            if (!success) {
                emit(StreamEvent(error = "请求失败: ${lastError?.message}"))
                break
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
                    try {
                        val result = toolExecutorProvider().execute(tc.function.name, tc.function.arguments)
                        emit(StreamEvent(toolResult = result.copy(toolCallId = tc.id)))
                        conversationManager.addToolResult(tc.id, result.name, result.output)
                    } catch (e: Exception) {
                        emit(StreamEvent(toolResult = ToolResult(
                            toolCallId = tc.id,
                            name = tc.function.name,
                            success = false,
                            output = "工具执行异常: ${e.message}"
                        )))
                        conversationManager.addToolResult(tc.id, tc.function.name, "工具执行异常: ${e.message}")
                    }
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
        if (turnCount >= maxTurns) {
            emit(StreamEvent(error = "工具调用次数已达上限（${maxTurns}），已停止执行。"))
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
