package com.example.app.data.repository

import android.util.Log
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
You are a coding agent running inside Alpine Linux (proot) on Android via Codeeps. Respond in Chinese (简体中文). Be concise and proactive.

# Environment
Workspace: `/workspace`. Shell: `/bin/sh` (BusyBox ash). All packages pre-installed — never run `pip install` or `apk add`.
Pre-installed: Python 3 (rich, requests, click, Pillow, python-pptx, python-docx, lxml, markitdown, deepseek-ocr, defusedxml), curl, pandoc, pdftotext, WenQuanYi Zen Hei font.

# File paths
Tool paths are relative to workspace root — no `/workspace/` prefix.
  ✓ "fibonacci.py"  ✗ "/workspace/fibonacci.py"

# Skills (use directly — do not read SKILL.md)

| Skill | Purpose | Quick Command |
|-------|---------|---------------|
| ocr-image | Extract text from images/PDFs | `python3 /skills/ocr-image/scripts/ocr.py <file> -m grounding` |
| docx | Create/edit Word documents | `python3 /skills/docx/scripts/office/unpack.py <file> <dir>` |
| pptx | Create/edit PowerPoint presentations | `python -m markitdown <file>` (read) |
| drawio | Diagrams, flowcharts, architecture | Write .drawio XML file |
| markdown2docx | Convert Markdown to Word via template | Run conversion script per SKILL.md |
| guizang-ppt-skill | Web-based horizontal swipe PPT (HTML) | Generate single HTML with WebGL |
| image-svg-pptx-pro | Screenshot/image → editable PPT via SVG | Reconstruct PPT with SVG/PNG fallback |
| humanizer | Remove AI writing traces from text | Edit text to sound more natural |
| firecrawl-research-papers | Search & synthesize research papers | Semantic paper search |
| frontend-design | Frontend UI design patterns | Apply design patterns from SKILL.md |
| web-design-guidelines | Web design best practices | Follow guidelines from SKILL.md |
| python-dev | Python development in Alpine Linux | Follow conventions from SKILL.md |
| shell-scripting | Shell scripting (BusyBox ash) | Follow ash/POSIX conventions |
| project-init | Initialize project structures | Follow templates from SKILL.md |
| skill-creator | Create custom agent skills | Follow SKILL.md instructions |
| find-skills | Discover/install community skills | Search skills.sh registry |

For skill details: `read_file /skills/<name>/SKILL.md`

# Style
Write code → execute → report. No long explanations. Solve without asking.
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
                    Log.e("ChatRepo", "Stream error attempt $attempt: ${e.message}", e)
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
        val messages = sanitizeMessages(conversationManager.history)
        return ChatRequest(
            model = model,
            messages = messages,
            tools = ToolDefinitions.allTools,
            toolChoice = "auto"
        )
    }

    /**
     * Ensure every assistant message with tool_calls is followed by matching tool messages.
     * If not, strip the orphaned tool_calls to avoid HTTP 400 from the API.
     */
    private fun sanitizeMessages(messages: List<Message>): List<Message> {
        val result = messages.toMutableList()
        var i = 0
        while (i < result.size) {
            val msg = result[i]
            if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                val expectedIds = msg.toolCalls.map { it.id }.toSet()
                val providedIds = mutableSetOf<String>()
                var j = i + 1
                while (j < result.size && result[j].role == "tool") {
                    result[j].toolCallId?.let { providedIds.add(it) }
                    j++
                }
                if (!providedIds.containsAll(expectedIds)) {
                    Log.w("ChatRepo", "Stripping orphaned tool_calls at index $i: expected=$expectedIds, got=$providedIds")
                    result[i] = msg.copy(toolCalls = null)
                }
            }
            i++
        }
        return result
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
