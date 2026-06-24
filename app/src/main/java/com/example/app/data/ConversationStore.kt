package com.example.app.data

import android.content.Context
import com.example.app.data.api.models.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ConversationStore(context: Context) {

    private val appDir = context.filesDir
    private val convDir = File(appDir, "conversations").also { it.mkdirs() }
    private val indexFile = File(convDir, "index.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mutex = Mutex()

    suspend fun listConversations(): List<ConversationMeta> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readIndex().conversations.sortedByDescending { it.updatedAt }
        }
    }

    suspend fun loadConversation(id: String): PersistedConversation? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(convDir, "$id.json")
            if (!file.exists()) return@withContext null
            try {
                json.decodeFromString<PersistedConversation>(file.readText())
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveConversation(conv: PersistedConversation) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(convDir, "${conv.id}.json")
            file.writeText(json.encodeToString(PersistedConversation.serializer(), conv))
            updateIndex(conv)
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(convDir, "$id.json").delete()
            // Clean up workspace
            File(appDir, "workspace/$id").deleteRecursively()

            val index = readIndex()
            val updated = index.copy(conversations = index.conversations.filter { it.id != id })
            writeIndex(updated)
        }
    }

    private fun updateIndex(conv: PersistedConversation) {
        val index = readIndex()
        val meta = ConversationMeta(
            id = conv.id,
            title = conv.title,
            createdAt = conv.createdAt,
            updatedAt = conv.updatedAt,
            messageCount = conv.messages.size
        )
        val updated = index.copy(
            conversations = (index.conversations.filter { it.id != conv.id } + meta)
        )
        writeIndex(updated)
    }

    private fun readIndex(): ConversationIndex {
        if (!indexFile.exists()) return ConversationIndex(emptyList())
        return try {
            json.decodeFromString<ConversationIndex>(indexFile.readText())
        } catch (e: Exception) {
            ConversationIndex(emptyList())
        }
    }

    private fun writeIndex(index: ConversationIndex) {
        indexFile.writeText(json.encodeToString(ConversationIndex.serializer(), index))
    }
}
