package com.example.app.data

import com.example.app.data.api.models.Message
import kotlinx.serialization.Serializable

@Serializable
data class PersistedConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<Message>
)

@Serializable
data class ConversationMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)

@Serializable
data class ConversationIndex(
    val conversations: List<ConversationMeta>
)
