package com.example.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.App
import com.example.app.data.api.models.ToolResult
import com.example.app.data.repository.StreamEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UIMessage(
    val id: Long,
    val role: String,
    val content: String = "",
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false,
    val toolCalls: List<UIToolCall> = emptyList()
)

data class UIToolCall(
    val id: String,
    val name: String,
    val status: String = "running",
    val result: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val repo = app.container.chatRepository

    private val _messages = MutableStateFlow<List<UIMessage>>(emptyList())
    val messages: StateFlow<List<UIMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var messageCounter = 0L
    private var currentAssistantMsg: UIMessage? = null
    private val pendingToolCalls = mutableMapOf<Int, UIToolCall>()
    private var currentJob: Job? = null

    init {
        repo.initSession()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return

        val userMsg = UIMessage(id = nextId(), role = "user", content = text)
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        currentJob?.cancel()
        currentAssistantMsg = null
        pendingToolCalls.clear()

        currentJob = viewModelScope.launch {
            var hasError = false
            try {
                repo.sendMessage(text).collect { event ->
                    when {
                        event.thinking -> {
                            setAssistantThinking(true)
                        }
                        event.error != null -> {
                            hasError = true
                            addOrUpdateAssistant("错误: ${event.error}")
                            _isLoading.value = false
                        }
                        event.textDelta != null -> {
                            if (currentAssistantMsg?.isThinking == true) {
                                setAssistantThinking(false)
                            }
                            appendToAssistant(event.textDelta)
                        }
                        event.toolCallStart != null -> {
                            val tc = event.toolCallStart
                            val idx = tc.index
                            pendingToolCalls[idx] = UIToolCall(
                                id = tc.id ?: "pending",
                                name = tc.function?.name ?: "unknown"
                            )
                            updateAssistantToolCalls()
                        }
                        event.toolResult != null -> {
                            val result = event.toolResult
                            val tc = UIToolCall(
                                id = result.toolCallId,
                                name = result.name,
                                status = if (result.success) "done" else "error",
                                result = result.output
                            )
                            val msg = UIMessage(
                                id = nextId(),
                                role = "tool",
                                content = formatToolResult(result),
                                toolCalls = listOf(tc)
                            )
                            _messages.value = _messages.value + msg
                        }
                        event.done -> {
                            finalizeAssistant()
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                hasError = true
                addOrUpdateAssistant("网络错误: ${e.message}")
                _isLoading.value = false
            }

            if (hasError) {
                finalizeAssistant()
            }
            currentJob = null
        }
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
        finalizeAssistant()
        _isLoading.value = false
    }

    private fun addOrUpdateAssistant(text: String) {
        val current = currentAssistantMsg
        if (current == null) {
            val msg = UIMessage(id = nextId(), role = "assistant", content = text, isStreaming = true)
            currentAssistantMsg = msg
            _messages.value = _messages.value + msg
        } else {
            val updated = current.copy(content = current.content + text)
            currentAssistantMsg = updated
            _messages.value = _messages.value.map { if (it.id == current.id) updated else it }
        }
    }

    private fun appendToAssistant(text: String) {
        val current = currentAssistantMsg
        if (current == null) {
            val msg = UIMessage(id = nextId(), role = "assistant", content = text, isStreaming = true)
            currentAssistantMsg = msg
            _messages.value = _messages.value + msg
        } else {
            val updated = current.copy(content = current.content + text)
            currentAssistantMsg = updated
            _messages.value = _messages.value.map { if (it.id == current.id) updated else it }
        }
    }

    private fun setAssistantThinking(thinking: Boolean) {
        val current = currentAssistantMsg
        if (current == null) {
            val msg = UIMessage(id = nextId(), role = "assistant", content = "", isStreaming = true, isThinking = thinking)
            currentAssistantMsg = msg
            _messages.value = _messages.value + msg
        } else {
            val updated = current.copy(isThinking = thinking, isStreaming = true)
            currentAssistantMsg = updated
            _messages.value = _messages.value.map { if (it.id == current.id) updated else it }
        }
    }

    private fun updateAssistantToolCalls() {
        val current = currentAssistantMsg ?: return
        val toolCallList = pendingToolCalls.values.toList()
        val updated = current.copy(toolCalls = toolCallList)
        currentAssistantMsg = updated
        _messages.value = _messages.value.map { if (it.id == current.id) updated else it }
    }

    private fun finalizeAssistant() {
        val current = currentAssistantMsg ?: return
        val updated = current.copy(isStreaming = false)
        currentAssistantMsg = null
        pendingToolCalls.clear()
        _messages.value = _messages.value.map { if (it.id == current.id) updated else it }
    }

    fun clearChat() {
        repo.clearHistory()
        currentAssistantMsg = null
        _messages.value = emptyList()
    }

    private fun nextId() = ++messageCounter

    private fun formatToolResult(result: ToolResult): String {
        val prefix = if (result.success) "✓" else "✗"
        val summary = if (result.output.length > 500) {
            result.output.take(500) + "\n... (已截断)"
        } else result.output
        return "$prefix ${result.name}: $summary"
    }
}
