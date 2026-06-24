package com.example.app.domain

import com.example.app.data.api.models.ToolResult

interface Tool {
    val name: String
    suspend fun execute(arguments: Map<String, String>): ToolResult
}
