package com.example.app.data.api.models

data class ToolResult(
    val toolCallId: String,
    val name: String,
    val success: Boolean,
    val output: String
)
