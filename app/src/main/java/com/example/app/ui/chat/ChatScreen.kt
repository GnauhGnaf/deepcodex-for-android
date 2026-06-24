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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.ui.chat.components.ChatInputBar
import com.example.app.ui.chat.components.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()

    // Only auto-scroll when user is at the bottom
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= listState.layoutInfo.totalItemsCount - 1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DeepSeek Codex") },
                actions = {
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
                onSend = { viewModel.sendMessage(it) },
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
                if (isAtBottom && messages.isNotEmpty()) {
                    listState.scrollToItem(messages.size)
                }
            }
        }
    }
}
