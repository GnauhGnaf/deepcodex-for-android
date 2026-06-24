package com.example.app

import android.content.Context
import com.example.app.data.api.DeepSeekClient
import com.example.app.data.local.SettingsStore
import com.example.app.data.repository.ChatRepository
import com.example.app.data.repository.SettingsRepository
import com.example.app.domain.LinuxEnvironment
import com.example.app.domain.ToolExecutor
import com.example.app.util.ConversationManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val workspaceDir = appContext.filesDir.resolve("workspace").also { it.mkdirs() }

    val linuxEnvironment = LinuxEnvironment(appContext)
    val settingsStore = SettingsStore(appContext)
    val settingsRepository = SettingsRepository(settingsStore)
    val conversationManager = ConversationManager()
    val deepSeekClient = DeepSeekClient()
    val toolExecutor = ToolExecutor(workspaceDir, linuxEnvironment)
    val chatRepository = ChatRepository(
        settingsRepository = settingsRepository,
        client = deepSeekClient,
        toolExecutor = toolExecutor,
        conversationManager = conversationManager,
        linuxEnv = linuxEnvironment
    )
}
