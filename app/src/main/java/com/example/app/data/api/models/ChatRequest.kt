package com.example.app.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<ToolDef>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = "auto",
    val stream: Boolean = true,
    @SerialName("max_tokens")
    val maxTokens: Int = 8192
)

@Serializable
data class Message(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallMsg>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolCallMsg(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class ToolDef(
    val type: String = "function",
    val function: FunctionSpec
)

@Serializable
data class FunctionSpec(
    val name: String,
    val description: String,
    val parameters: JsonElement
)
