package com.example.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.App
import com.example.app.data.ConversationMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val store = app.container.conversationStore

    private val _conversations = MutableStateFlow<List<ConversationMeta>>(emptyList())
    val conversations: StateFlow<List<ConversationMeta>> = _conversations

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _conversations.value = store.listConversations()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            // If deleting the active conversation, start a new one
            if (id == app.container.currentConversationId) {
                app.container.switchConversation(null)
            }
            store.deleteConversation(id)
            loadConversations()
        }
    }
}
