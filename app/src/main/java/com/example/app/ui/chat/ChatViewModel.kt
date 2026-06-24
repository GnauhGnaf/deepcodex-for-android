package com.example.app.ui.chat

import android.app.Application
import android.util.Log
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
    val reasoning: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false,
    val toolCallHistory: List<UIToolCall> = emptyList()
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
    private var hasContentSinceReasoning = false
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
        hasContentSinceReasoning = false
        pendingToolCalls.clear()

        currentJob = viewModelScope.launch {
            try {
                repo.sendMessage(text).collect { event ->
                    when {
                        event.thinking -> setAssistantThinking(true)

                        event.reasoningDelta != null -> {
                            setAssistantThinking(true)
                            appendToReasoning(event.reasoningDelta)
                        }

                        event.error != null -> {
                            appendToAssistant("\n\n**❌ 错误：** ${event.error}")
                            _isLoading.value = false
                        }

                        event.textDelta != null -> {
                            if (currentAssistantMsg?.isThinking == true) {
                                setAssistantThinking(false)
                            }
                            hasContentSinceReasoning = true
                            appendToAssistant(event.textDelta)
                        }

                        event.toolCallStart != null -> {
                            hasContentSinceReasoning = true
                            val tc = event.toolCallStart
                            pendingToolCalls[tc.index] = UIToolCall(
                                id = tc.id ?: "pending",
                                name = tc.function?.name ?: "unknown"
                            )
                            syncToolCallsToAssistant()
                        }

                        event.toolResult != null -> {
                            hasContentSinceReasoning = true
                            val result = event.toolResult
                            Log.d("ChatVM", "ToolResult: id=${result.toolCallId} name=${result.name} success=${result.success} outputLen=${result.output.length}")
                            pendingToolCalls.values.find { it.id == result.toolCallId || it.name == result.name }?.let { existing ->
                                Log.d("ChatVM", "  Matched existing: id=${existing.id} name=${existing.name}")
                                val updated = existing.copy(
                                    status = if (result.success) "done" else "error",
                                    result = result.output
                                )
                                // Find and update the correct key
                                pendingToolCalls.entries.find { it.value.id == existing.id }?.let { entry ->
                                    pendingToolCalls[entry.key] = updated
                                }
                            } ?: run {
                                Log.d("ChatVM", "  Fallback: no match found, adding new entry")
                                // Fallback: add result directly
                                val idx = pendingToolCalls.size
                                pendingToolCalls[idx] = UIToolCall(
                                    id = result.toolCallId,
                                    name = result.name,
                                    status = if (result.success) "done" else "error",
                                    result = result.output
                                )
                            }
                            syncToolCallsToAssistant()
                        }

                        event.done -> {
                            finalizeAssistant()
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                appendToAssistant("\n\n**❌ 网络错误：** ${e.message}")
                _isLoading.value = false
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

    private fun ensureAssistant(): UIMessage {
        val current = currentAssistantMsg
        if (current != null) return current
        val msg = UIMessage(id = nextId(), role = "assistant", content = "", isStreaming = true)
        currentAssistantMsg = msg
        _messages.value = _messages.value + msg
        return msg
    }

    private fun appendToAssistant(text: String) {
        val current = ensureAssistant()
        val updated = current.copy(content = current.content + text)
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun appendToReasoning(text: String) {
        val current = ensureAssistant()
        val updated = if (hasContentSinceReasoning) {
            hasContentSinceReasoning = false
            current.copy(reasoning = current.reasoning + text)
        } else {
            val blocks = current.reasoning.toMutableList()
            if (blocks.isEmpty()) {
                blocks.add(text)
            } else {
                val last = blocks.lastIndex
                blocks[last] = blocks[last] + text
            }
            current.copy(reasoning = blocks)
        }
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun setAssistantThinking(thinking: Boolean) {
        val current = ensureAssistant()
        val updated = current.copy(isThinking = thinking, isStreaming = true)
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun syncToolCallsToAssistant() {
        val current = ensureAssistant()
        val allCalls = pendingToolCalls.values.toList()
        val updated = current.copy(toolCallHistory = allCalls)
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun finalizeAssistant() {
        val current = currentAssistantMsg ?: return
        val updated = current.copy(isStreaming = false)
        currentAssistantMsg = null
        pendingToolCalls.clear()
        updateInList(updated)
    }

    private fun updateInList(msg: UIMessage) {
        _messages.value = _messages.value.map { if (it.id == msg.id) msg else it }
    }

    fun clearChat() {
        repo.clearHistory()
        currentAssistantMsg = null
        _messages.value = emptyList()
    }

    private fun nextId() = ++messageCounter
}
