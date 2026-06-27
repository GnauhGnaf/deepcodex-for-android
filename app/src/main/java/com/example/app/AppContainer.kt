package com.example.app

import android.content.Context
import com.example.app.data.ConversationStore
import com.example.app.data.PersistedConversation
import com.example.app.data.api.DeepSeekClient
import com.example.app.data.local.SettingsStore
import com.example.app.data.repository.ChatRepository
import com.example.app.data.repository.SettingsRepository
import com.example.app.domain.LinuxEnvironment
import com.example.app.domain.ToolExecutor
import com.example.app.util.ConversationManager
import com.example.app.util.SkillManager
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val linuxEnvironment = LinuxEnvironment(appContext)
    val settingsStore = SettingsStore(appContext)
    val settingsRepository = SettingsRepository(settingsStore)
    val conversationManager = ConversationManager()
    val conversationStore = ConversationStore(appContext)
    val deepSeekClient = DeepSeekClient()

    // Shared skills directory — initialized once, used by all workspaces
    val sharedSkillsDir: File = SkillManager.sharedSkillsDir(appContext.filesDir)

    init {
        SkillManager.initSkills(appContext, appContext.filesDir)
    }

    // Current conversation tracking
    var currentConversationId: String? = null
        internal set

    private fun getWorkspaceDir(): File =
        appContext.filesDir.resolve("workspace/${currentConversationId ?: "default"}").also { it.mkdirs() }

    var toolExecutor: ToolExecutor = ToolExecutor(getWorkspaceDir(), linuxEnvironment, sharedSkillsDir)
        private set

    val chatRepository = ChatRepository(
        settingsRepository = settingsRepository,
        client = deepSeekClient,
        toolExecutorProvider = { toolExecutor },
        conversationManager = conversationManager,
        linuxEnv = linuxEnvironment
    )

    fun switchConversation(conversationId: String?) {
        currentConversationId = conversationId
        val newDir = getWorkspaceDir()
        toolExecutor = ToolExecutor(newDir, linuxEnvironment, sharedSkillsDir)
    }

    // Used by ChatScreen — observable state for pending conversation actions
    val pendingActionVersion = androidx.compose.runtime.mutableStateOf(0)
    var pendingLoadConversationId: String? = null
        private set

    fun requestLoadConversation(id: String) {
        pendingLoadConversationId = id
        pendingActionVersion.value++
    }

    fun requestNewConversation() {
        pendingLoadConversationId = null
        pendingActionVersion.value++
    }

    suspend fun saveCurrentConversation() {
        val id = conversationManager.conversationId ?: return
        val messages = conversationManager.toMessages()
        val cleaned = sanitizeMessages(messages)
        conversationStore.saveConversation(
            PersistedConversation(
                id = id,
                title = conversationManager.conversationTitle.ifBlank { "新对话" },
                createdAt = conversationManager.createdAt,
                updatedAt = System.currentTimeMillis(),
                messages = cleaned
            )
        )
    }

    /** Strip assistant messages whose tool_calls lack matching tool responses. */
    private fun sanitizeMessages(messages: List<com.example.app.data.api.models.Message>): List<com.example.app.data.api.models.Message> {
        val result = messages.toMutableList()
        var i = 0
        while (i < result.size) {
            val msg = result[i]
            if (msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty()) {
                val expectedIds = msg.toolCalls.map { it.id }.toSet()
                val providedIds = mutableSetOf<String>()
                var j = i + 1
                while (j < result.size && result[j].role == "tool") {
                    result[j].toolCallId?.let { providedIds.add(it) }
                    j++
                }
                if (!providedIds.containsAll(expectedIds)) {
                    result[i] = msg.copy(toolCalls = null)
                }
            }
            i++
        }
        return result
    }

    fun syncOcrConfig(key: String, baseUrl: String) {
        linuxEnvironment.writeOcrConfig(key, baseUrl)
    }
}
