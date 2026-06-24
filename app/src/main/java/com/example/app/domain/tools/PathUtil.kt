package com.example.app.domain.tools

object PathUtil {
    /** Strip leading /workspace/ or /workspace prefix so tools resolve paths consistently. */
    fun stripWorkspacePrefix(path: String): String {
        val trimmed = path.trim()
        return when {
            trimmed == "/workspace" -> ""
            trimmed.startsWith("/workspace/") -> trimmed.removePrefix("/workspace/")
            else -> trimmed
        }
    }
}
