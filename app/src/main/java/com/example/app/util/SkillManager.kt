package com.example.app.util

import android.content.Context
import java.io.File

object SkillManager {

    private const val SKILLS_DIR_NAME = "skills"

    /** Absolute path to the shared skills directory. */
    fun sharedSkillsDir(filesDir: File): File = File(filesDir, SKILLS_DIR_NAME)

    /**
     * Sync bundled skills from assets to the shared skills directory.
     * Recursively copies directory trees — preserves existing files (user-installed skills).
     */
    fun initSkills(context: Context, filesDir: File) {
        val skillsDir = sharedSkillsDir(filesDir)
        skillsDir.mkdirs()
        try {
            copyAssetDir(context, "skills", skillsDir)
        } catch (_: Exception) { }
    }

    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        val entries = context.assets.list(assetPath) ?: return
        for (entry in entries) {
            val subPath = "$assetPath/$entry"
            val dest = File(destDir, entry)
            val children = context.assets.list(subPath)
            if (children != null && children.isNotEmpty()) {
                dest.mkdirs()
                copyAssetDir(context, subPath, dest)
            } else {
                if (dest.exists()) continue
                dest.parentFile?.mkdirs()
                context.assets.open(subPath).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
