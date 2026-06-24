package com.example.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.App
import com.example.app.ui.chat.components.ChatInputBar
import com.example.app.ui.chat.components.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()

    val app = LocalContext.current.applicationContext as App

    // Handle pending conversation actions from HistoryScreen
    val actionVersion by app.container.pendingActionVersion
    LaunchedEffect(actionVersion) {
        if (actionVersion == 0) return@LaunchedEffect
        val id = app.container.pendingLoadConversationId
        if (id != null && id.isNotEmpty()) {
            viewModel.loadConversation(id)
        } else {
            viewModel.clearChat()
        }
    }

    // Track whether user was at the bottom — updated continuously from layout.
    // Using mutableState (not derivedStateOf) because we read it in a
    // snapshotFlow collect that fires BEFORE layout updates for new content.
    var wasAtBottom by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= listState.layoutInfo.totalItemsCount - 1
        }.collect { atBottom ->
            wasAtBottom = atBottom
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DeepSeek Codex") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, "对话历史")
                    }
                    IconButton(onClick = {
                        viewModel.clearChat()
                    }) {
                        Icon(Icons.Default.Delete, "清空对话")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                onSend = {
                    wasAtBottom = true
                    viewModel.sendMessage(it)
                },
                onStop = { viewModel.stop() },
                isLoading = isLoading
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "DeepSeek Codex",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "你的 Android 编程助手",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "先配置 API Key 再开始对话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }
                    item(key = "bottom") {}
                }
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow {
                messages.lastOrNull()?.let { msg ->
                    msg.id to msg.blocks.size to msg.blocks.lastOrNull()
                }
            }.collect {
                if (wasAtBottom && messages.isNotEmpty()) {
                    listState.scrollToItem(messages.size)
                }
            }
        }
    }
}
