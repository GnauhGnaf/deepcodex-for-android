package com.example.app.util

import com.example.app.data.api.models.Message
import com.example.app.data.api.models.ToolCallMsg
import com.example.app.data.api.models.FunctionCall

class ConversationManager {

    private val messages = mutableListOf<Message>()

    val history: List<Message> get() = messages.toList()

    fun addSystem(content: String) {
        messages.add(Message(role = "system", content = content))
    }

    fun addUser(content: String) {
        messages.add(Message(role = "user", content = content))
    }

    fun addAssistant(content: String? = null, toolCalls: List<ToolCallMsg>? = null) {
        if (!content.isNullOrBlank() || toolCalls != null) {
            messages.add(Message(role = "assistant", content = content, toolCalls = toolCalls))
        }
    }

    fun addToolResult(toolCallId: String, toolName: String, output: String) {
        messages.add(
            Message(
                role = "tool",
                content = output,
                toolCallId = toolCallId,
                name = toolName
            )
        )
    }

    fun clear() {
        messages.clear()
    }

    fun removeLastIfAssistantEmpty() {
        val last = messages.lastOrNull()
        if (last?.role == "assistant" && last.content.isNullOrBlank() && last.toolCalls == null) {
            messages.removeAt(messages.lastIndex)
        }
    }

    fun estimatedTokens(): Int = messages.sumOf { m ->
        (m.content?.length ?: 0) / 3 + 4
    }
}
