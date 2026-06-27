package com.example.app.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.App
import com.example.app.data.api.models.ToolResult
import com.example.app.data.repository.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ContentBlock {
    data class Reasoning(val text: String) : ContentBlock()
    data class Text(val text: String) : ContentBlock()
    data class ToolCalls(val calls: List<UIToolCall>) : ContentBlock()
}

data class UIMessage(
    val id: Long,
    val role: String,
    val blocks: List<ContentBlock> = emptyList(),
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false,
    val outputFiles: List<String> = emptyList()
) {
    val content: String get() = blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
    val toolCallHistory: List<UIToolCall> get() = blocks.filterIsInstance<ContentBlock.ToolCalls>().flatMap { it.calls }
}

data class UIToolCall(
    val id: String,
    val name: String,
    val status: String = "running",
    val result: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val repo = app.container.chatRepository

    private val _messages = MutableStateFlow<List<UIMessage>>(emptyList())
    val messages: StateFlow<List<UIMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var messageCounter = 0L
    private var currentAssistantMsg: UIMessage? = null
    private val pendingToolCalls = mutableMapOf<Int, UIToolCall>()
    private var currentJob: Job? = null
    private var workspaceSnapshot: Set<String> = emptySet()

    init {
        // Restore active conversation if ViewModel was recreated (rotation, process death)
        val convId = app.container.currentConversationId
        val history = app.container.conversationManager.history
        if (convId != null && history.size > 1) { // >1 because system prompt is always there
            _messages.value = rebuildUIMessages(history)
            messageCounter = _messages.value.size.toLong()
        } else {
            startNewConversation()
        }
    }

    fun startNewConversation() {
        val newId = app.container.conversationStore.newId()
        app.container.switchConversation(newId)
        repo.initSession(convId = newId)
        _messages.value = emptyList()
        messageCounter = 0L
        currentAssistantMsg = null
        pendingToolCalls.clear()
    }

    suspend fun loadConversation(id: String) {
        val persisted = app.container.conversationStore.loadConversation(id) ?: return
        app.container.switchConversation(id)
        repo.initSession(convId = id, messages = persisted.messages)
        _messages.value = rebuildUIMessages(persisted.messages)
        messageCounter = _messages.value.size.toLong()
        currentAssistantMsg = null
        pendingToolCalls.clear()
    }

    private fun rebuildUIMessages(apiMessages: List<com.example.app.data.api.models.Message>): List<UIMessage> {
        val uiMessages = mutableListOf<UIMessage>()
        for (msg in apiMessages) {
            when (msg.role) {
                "user" -> {
                    val text = msg.content ?: ""
                    uiMessages.add(UIMessage(
                        id = nextId(),
                        role = "user",
                        blocks = listOf(ContentBlock.Text(text))
                    ))
                }
                "assistant" -> {
                    val blocks = mutableListOf<ContentBlock>()
                    if (!msg.content.isNullOrBlank()) {
                        blocks.add(ContentBlock.Text(msg.content))
                    }
                    if (!msg.toolCalls.isNullOrEmpty()) {
                        val uiToolCalls = msg.toolCalls.map { tc ->
                            UIToolCall(
                                id = tc.id,
                                name = tc.function.name,
                                status = "done",
                                result = ""
                            )
                        }
                        blocks.add(ContentBlock.ToolCalls(uiToolCalls))
                    }
                    if (blocks.isNotEmpty()) {
                        uiMessages.add(UIMessage(id = nextId(), role = "assistant", blocks = blocks))
                    }
                }
                "tool" -> {
                    val toolCallId = msg.toolCallId ?: continue
                    val result = msg.content ?: ""
                    for (i in uiMessages.indices.reversed()) {
                        val uiMsg = uiMessages[i]
                        val toolCallsBlock = uiMsg.blocks.filterIsInstance<ContentBlock.ToolCalls>().lastOrNull()
                        if (toolCallsBlock != null) {
                            val updatedCalls = toolCallsBlock.calls.map { call ->
                                if (call.id == toolCallId) call.copy(result = result) else call
                            }
                            val updatedBlocks = uiMsg.blocks.map { block ->
                                if (block is ContentBlock.ToolCalls) ContentBlock.ToolCalls(updatedCalls) else block
                            }
                            uiMessages[i] = uiMsg.copy(blocks = updatedBlocks)
                            break
                        }
                    }
                }
            }
        }
        // Post-process: fill in outputFiles after tool results are populated
        return uiMessages.map { msg ->
            if (msg.role == "assistant") msg.copy(outputFiles = extractOutputFiles(msg)) else msg
        }
    }

    private suspend fun persistCurrentConversation() {
        app.container.saveCurrentConversation()
    }

    // Called from outside (e.g., Activity onPause) to save without a coroutine scope
    fun onPause() {
        if (app.container.currentConversationId != null && _messages.value.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                app.container.saveCurrentConversation()
            }
        }
    }

    private fun startPeriodicSave() {
        viewModelScope.launch(Dispatchers.IO) {
            while (_isLoading.value) {
                delay(5_000)
                if (_isLoading.value) {
                    app.container.saveCurrentConversation()
                }
            }
        }
    }

    // Save right after user message to survive crashes during API calls
    private fun persistAfterUserMessage() {
        viewModelScope.launch(Dispatchers.IO) {
            app.container.saveCurrentConversation()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return

        val userMsg = UIMessage(id = nextId(), role = "user", blocks = listOf(ContentBlock.Text(text)))
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        currentJob?.cancel()
        currentAssistantMsg = null
        pendingToolCalls.clear()

        currentJob = viewModelScope.launch {
            try {
                workspaceSnapshot = withContext(Dispatchers.IO) { snapshotWorkspaceFiles() }
                // Persist immediately after user message (before API call)
                persistAfterUserMessage()
                startPeriodicSave()
                val resolved = resolveSlashCommand(text)
                repo.sendMessage(resolved).collect { event ->
                    when {
                        event.thinking -> setAssistantThinking(true)

                        event.reasoningDelta != null -> {
                            setAssistantThinking(true)
                            appendToReasoning(event.reasoningDelta)
                        }

                        event.error != null -> {
                            appendToAssistant("\n\n**❌ 错误：** ${event.error}")
                            finalizeAssistant()
                            _isLoading.value = false
                        }

                        event.textDelta != null -> {
                            if (currentAssistantMsg?.isThinking == true) {
                                setAssistantThinking(false)
                            }
                            appendToAssistant(event.textDelta)
                        }

                        event.toolCallStart != null -> {
                            val tc = event.toolCallStart
                            pendingToolCalls[tc.index] = UIToolCall(
                                id = tc.id ?: "pending",
                                name = tc.function?.name ?: "unknown"
                            )
                            syncToolCallsToAssistant()
                        }

                        event.toolResult != null -> {
                            val result = event.toolResult
                            Log.d("ChatVM", "ToolResult: id=${result.toolCallId} name=${result.name} success=${result.success} outputLen=${result.output.length}")
                            // Match by ID first, fallback to name (avoids false match when multiple calls share a name)
                            val entry = pendingToolCalls.entries.firstOrNull { it.value.id == result.toolCallId }
                                ?: pendingToolCalls.entries.firstOrNull { it.value.name == result.name }
                            if (entry != null) {
                                val (key, existing) = entry
                                Log.d("ChatVM", "  Matched existing: id=${existing.id} name=${existing.name}")
                                val updated = existing.copy(
                                    status = if (result.success) "done" else "error",
                                    result = result.output
                                )
                                pendingToolCalls[key] = updated
                            } else {
                                Log.d("ChatVM", "  Fallback: no match found, adding new entry")
                                val idx = pendingToolCalls.size
                                pendingToolCalls[idx] = UIToolCall(
                                    id = result.toolCallId,
                                    name = result.name,
                                    status = if (result.success) "done" else "error",
                                    result = result.output
                                )
                            }
                            syncToolCallsToAssistant()
                        }

                        event.done -> {
                            finalizeAssistant()
                            _isLoading.value = false
                            persistCurrentConversation()
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation — just finalize and suppress
                finalizeAssistant()
                _isLoading.value = false
                throw e
            } catch (e: Exception) {
                appendToAssistant("\n\n**❌ 错误：** ${e.message}")
                finalizeAssistant()
                _isLoading.value = false
            }
            currentJob = null
        }
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
        finalizeAssistant()
        _isLoading.value = false
    }

    /**
     * Detect /skill-name prefix and inject the skill's SKILL.md content.
     * Cleans Windows-specific paths for the Android proot environment.
     * If no user message follows the skill name, prompts the AI to wait for instructions.
     */
    private suspend fun resolveSlashCommand(text: String): String = withContext(Dispatchers.IO) {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("/")) return@withContext text

        val parts = trimmed.split("\\s+".toRegex(), limit = 2)
        val skillName = parts[0].removePrefix("/").lowercase()
        val userMessage = parts.getOrElse(1) { "" }.trim()

        val skillsDir = app.container.sharedSkillsDir
        val skillFile = File(skillsDir, "$skillName/SKILL.md")

        val rawContent = when {
            skillFile.exists() -> skillFile.readText()
            File(skillsDir, "$skillName.md").exists() -> File(skillsDir, "$skillName.md").readText()
            else -> return@withContext text // Unknown skill, pass through
        }

        // Clean Windows paths → Android proot paths
        var clean = rawContent
            .replace("C:\\\\Users\\\\27602\\\\.claude\\\\skills\\\\", "/skills/")
            .replace("C:/Users/27602/.claude/skills/", "/skills/")
            .replace("python scripts/", "python3 /skills/$skillName/scripts/")
            .replace("python3 scripts/", "python3 /skills/$skillName/scripts/")
            .replace("bash scripts/", "sh /skills/$skillName/scripts/")

        val instruction = if (userMessage.isEmpty()) {
            "**用户通过 /$skillName 加载了此技能，但未指定具体任务。请介绍该技能的功能和可用的脚本，询问用户需要做什么。**"
        } else {
            "用户消息:\n$userMessage"
        }

        return@withContext "[技能已加载: $skillName]\n\n$clean\n\n---\n$instruction"
    }

    private fun ensureAssistant(): UIMessage {
        val current = currentAssistantMsg
        if (current != null) return current
        val msg = UIMessage(id = nextId(), role = "assistant", isStreaming = true)
        currentAssistantMsg = msg
        _messages.value = _messages.value + msg
        return msg
    }

    private fun appendToAssistant(text: String) {
        val current = ensureAssistant()
        val mutableBlocks = current.blocks.toMutableList()
        val last = mutableBlocks.lastOrNull()
        if (last is ContentBlock.Text) {
            mutableBlocks[mutableBlocks.lastIndex] = ContentBlock.Text(last.text + text)
        } else {
            mutableBlocks.add(ContentBlock.Text(text))
        }
        val updated = current.copy(blocks = mutableBlocks)
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun appendToReasoning(text: String) {
        val current = ensureAssistant()
        val mutableBlocks = current.blocks.toMutableList()
        val last = mutableBlocks.lastOrNull()
        if (last is ContentBlock.Reasoning) {
            mutableBlocks[mutableBlocks.lastIndex] = ContentBlock.Reasoning(last.text + text)
        } else {
            mutableBlocks.add(ContentBlock.Reasoning(text))
        }
        val updated = current.copy(blocks = mutableBlocks)
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun setAssistantThinking(thinking: Boolean) {
        val current = ensureAssistant()
        val updated = current.copy(isThinking = thinking, isStreaming = true)
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun syncToolCallsToAssistant() {
        val current = ensureAssistant()
        val mutableBlocks = current.blocks.toMutableList()
        val allCalls = pendingToolCalls.values.toList()
        val last = mutableBlocks.lastOrNull()
        if (last is ContentBlock.ToolCalls) {
            mutableBlocks[mutableBlocks.lastIndex] = ContentBlock.ToolCalls(allCalls)
        } else {
            mutableBlocks.add(ContentBlock.ToolCalls(allCalls))
        }
        val updated = current.copy(blocks = mutableBlocks)
        currentAssistantMsg = updated
        updateInList(updated)
    }

    private fun finalizeAssistant() {
        val current = currentAssistantMsg ?: return
        val files = extractOutputFiles(current)
        val updated = current.copy(isStreaming = false, outputFiles = files)
        currentAssistantMsg = null
        pendingToolCalls.clear()
        updateInList(updated)
    }

    private fun extractOutputFiles(msg: UIMessage): List<String> {
        val fromWriteFile = msg.blocks.filterIsInstance<ContentBlock.ToolCalls>()
            .flatMap { it.calls }
            .filter { it.name == "write_file" && it.status == "done" }
            .mapNotNull { extractFilePath(it.result) }

        // Also detect files created by run_command or scripts (e.g. docx generation)
        val workspaceDir = app.container.toolExecutor.workspaceDir
        val currentFiles = try {
            workspaceDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(workspaceDir).path }.toSet()
        } catch (_: Exception) { emptySet<String>() }

        val newFiles = if (workspaceSnapshot.isNotEmpty()) {
            (currentFiles - workspaceSnapshot).filter { !it.startsWith(".") }
        } else {
            emptySet()
        }
        workspaceSnapshot = emptySet()

        return (fromWriteFile.toSet() + newFiles).toList()
    }

    private fun snapshotWorkspaceFiles(): Set<String> {
        val dir = app.container.toolExecutor.workspaceDir
        return try {
            dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    private fun extractFilePath(result: String): String? {
        val regex = Regex("文件已写入:\\s*(.+?)\\s*\\(\\d+\\s*字符\\)")
        return regex.find(result)?.groupValues?.get(1)?.trim()
    }

    private fun updateInList(msg: UIMessage) {
        _messages.value = _messages.value.map { if (it.id == msg.id) msg else it }
    }

    fun clearChat() {
        startNewConversation()
    }

    private fun nextId() = ++messageCounter
}
