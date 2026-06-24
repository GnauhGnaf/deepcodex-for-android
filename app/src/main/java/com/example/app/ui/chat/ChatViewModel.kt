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

sealed class ContentBlock {
    data class Reasoning(val text: String) : ContentBlock()
    data class Text(val text: String) : ContentBlock()
    data class ToolCalls(val calls: List<UIToolCall>) : ContentBlock()
}

data class UIMessage(
    val id: Long,
    val role: String,
    val blocks: List<ContentBlock> = emptyList(),
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false
) {
    val content: String get() = blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
    val toolCallHistory: List<UIToolCall> get() = blocks.filterIsInstance<ContentBlock.ToolCalls>().flatMap { it.calls }
}

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
        startNewConversation()
    }

    fun startNewConversation() {
        val newId = app.container.conversationStore.newId()
        app.container.switchConversation(newId)
        repo.initSession(convId = newId)
        _messages.value = emptyList()
        messageCounter = 0L
        currentAssistantMsg = null
        pendingToolCalls.clear()
    }

    suspend fun loadConversation(id: String) {
        val persisted = app.container.conversationStore.loadConversation(id) ?: return
        app.container.switchConversation(id)
        repo.initSession(convId = id, messages = persisted.messages)
        _messages.value = rebuildUIMessages(persisted.messages)
        messageCounter = _messages.value.size.toLong()
        currentAssistantMsg = null
        pendingToolCalls.clear()
    }

    private fun rebuildUIMessages(apiMessages: List<com.example.app.data.api.models.Message>): List<UIMessage> {
        val uiMessages = mutableListOf<UIMessage>()
        for (msg in apiMessages) {
            when (msg.role) {
                "user" -> {
                    val text = msg.content ?: ""
                    uiMessages.add(UIMessage(
                        id = nextId(),
                        role = "user",
                        blocks = listOf(ContentBlock.Text(text))
                    ))
                }
                "assistant" -> {
                    val blocks = mutableListOf<ContentBlock>()
                    if (!msg.content.isNullOrBlank()) {
                        blocks.add(ContentBlock.Text(msg.content))
                    }
                    if (!msg.toolCalls.isNullOrEmpty()) {
                        val uiToolCalls = msg.toolCalls.map { tc ->
                            UIToolCall(
                                id = tc.id,
                                name = tc.function.name,
                                status = "done",
                                result = ""
                            )
                        }
                        blocks.add(ContentBlock.ToolCalls(uiToolCalls))
                    }
                    if (blocks.isNotEmpty()) {
                        uiMessages.add(UIMessage(id = nextId(), role = "assistant", blocks = blocks))
                    }
                }
                "tool" -> {
                    val toolCallId = msg.toolCallId ?: continue
                    val result = msg.content ?: ""
                    for (i in uiMessages.indices.reversed()) {
                        val uiMsg = uiMessages[i]
                        val toolCallsBlock = uiMsg.blocks.filterIsInstance<ContentBlock.ToolCalls>().lastOrNull()
                        if (toolCallsBlock != null) {
                            val updatedCalls = toolCallsBlock.calls.map { call ->
                                if (call.id == toolCallId) call.copy(result = result) else call
                            }
                            val updatedBlocks = uiMsg.blocks.map { block ->
                                if (block is ContentBlock.ToolCalls) ContentBlock.ToolCalls(updatedCalls) else block
                            }
                            uiMessages[i] = uiMsg.copy(blocks = updatedBlocks)
                            break
                        }
                    }
                }
            }
        }
        return uiMessages
    }

    private suspend fun persistCurrentConversation() {
        app.container.saveCurrentConversation()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return

        val userMsg = UIMessage(id = nextId(), role = "user", blocks = listOf(ContentBlock.Text(text)))
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        currentJob?.cancel()
        currentAssistantMsg = null
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
                            appendToAssistant(event.textDelta)
                        }

                        event.toolCallStart != null -> {
                            val tc = event.toolCallStart
                            pendingToolCalls[tc.index] = UIToolCall(
                                id = tc.id ?: "pending",
                                name = tc.function?.name ?: "unknown"
                            )
                            syncToolCallsToAssistant()
                        }

                        event.toolResult != null -> {
                            val result = event.toolResult
                            Log.d("ChatVM", "ToolResult: id=${result.toolCallId} name=${result.name} success=${result.success} outputLen=${result.output.length}")
                            pendingToolCalls.values.find { it.id == result.toolCallId || it.name == result.name }?.let { existing ->
                                Log.d("ChatVM", "  Matched existing: id=${existing.id} name=${existing.name}")
                                val updated = existing.copy(
                                    status = if (result.success) "done" else "error",
                                    result = result.output
                                )
                                pendingToolCalls.entries.find { it.value.id == existing.id }?.let { entry ->
                                    pendingToolCalls[entry.key] = updated
                                }
                            } ?: run {
                                Log.d("ChatVM", "  Fallback: no match found, adding new entry")
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
                            persistCurrentConversation()
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
        val msg = UIMessage(id = nextId(), role = "assistant", isStreaming = true)
        currentAssistantMsg = msg
        _messages.value = _messages.value + msg
        return msg
    }

    private fun appendToAssistant(text: String) {
        val current = ensureAssistant()
        val mutableBlocks = current.blocks.toMutableList()
        val last = mutableBlocks.lastOrNull()
        if (last is ContentBlock.Text) {
            mutableBlocks[mutableBlocks.lastIndex] = ContentBlock.Text(last.text + text)
        } else {
            mutableBlocks.add(ContentBlock.Text(text))
        }
        val updated = current.copy(blocks = mutableBlocks)
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun appendToReasoning(text: String) {
        val current = ensureAssistant()
        val mutableBlocks = current.blocks.toMutableList()
        val last = mutableBlocks.lastOrNull()
        if (last is ContentBlock.Reasoning) {
            mutableBlocks[mutableBlocks.lastIndex] = ContentBlock.Reasoning(last.text + text)
        } else {
            mutableBlocks.add(ContentBlock.Reasoning(text))
        }
        val updated = current.copy(blocks = mutableBlocks)
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
        val mutableBlocks = current.blocks.toMutableList()
        val allCalls = pendingToolCalls.values.toList()
        val last = mutableBlocks.lastOrNull()
        if (last is ContentBlock.ToolCalls) {
            mutableBlocks[mutableBlocks.lastIndex] = ContentBlock.ToolCalls(allCalls)
        } else {
            mutableBlocks.add(ContentBlock.ToolCalls(allCalls))
        }
        val updated = current.copy(blocks = mutableBlocks)
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
        startNewConversation()
    }

    private fun nextId() = ++messageCounter
}
