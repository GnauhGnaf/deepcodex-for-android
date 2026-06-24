package com.example.app.util

import com.example.app.data.api.models.ChatResponse
import kotlinx.serialization.json.Json

object SseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(line: String): ChatResponse? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("data:")) return null
        val data = trimmed.removePrefix("data:").trim()
        if (data == "[DONE]") return null
        return try {
            json.decodeFromString<ChatResponse>(data)
        } catch (_: Exception) {
            null
        }
    }
}
