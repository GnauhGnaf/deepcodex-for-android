package com.example.app.domain.tools

import java.io.File

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

    private const val SKILLS_PATH = ".codex/skills"

    /**
     * Resolve a tool path to a File. If the path targets .claude/skills and a shared
     * skills directory is configured, the file is resolved against that shared directory
     * so that skills are common to all workspaces.
     */
    fun resolvePath(rawPath: String, workspaceDir: File, sharedSkillsDir: File?): File {
        val stripped = stripWorkspacePrefix(rawPath)
        if (sharedSkillsDir != null) {
            if (stripped == SKILLS_PATH) return sharedSkillsDir.canonicalFile
            if (stripped.startsWith("$SKILLS_PATH/")) {
                val rel = stripped.removePrefix("$SKILLS_PATH/")
                return File(sharedSkillsDir, rel).canonicalFile
            }
        }
        return File(workspaceDir, stripped).canonicalFile
    }

    /**
     * Check whether a resolved file is inside the workspace or an allowed shared root.
     */
    fun isPathSafe(file: File, workspaceDir: File, sharedSkillsDir: File?): Boolean {
        if (file.path.startsWith(workspaceDir.canonicalPath)) return true
        if (sharedSkillsDir != null && file.path.startsWith(sharedSkillsDir.canonicalPath)) return true
        return false
    }
}
