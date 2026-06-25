package com.example.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.App
import com.example.app.ui.chat.components.ChatInputBar
import com.example.app.ui.chat.components.MessageBubble
import com.example.app.ui.chat.components.WorkspaceFileDialog

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

    var showFileBrowser by remember { mutableStateOf(false) }
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

    // Track whether user was at the bottom
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
                title = {
                    Text(
                        "DeepSeek Codex",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, "历史")
                    }
                    IconButton(onClick = { showFileBrowser = true }) {
                        Icon(Icons.Default.Folder, "文件")
                    }
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.Delete, "清空")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "DeepSeek Codex",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "你的 Android 编程助手",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "先配置 API Key 再开始对话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg, onBrowseFiles = { showFileBrowser = true })
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

    if (showFileBrowser) {
        WorkspaceFileDialog(
            workspaceDir = app.container.toolExecutor.workspaceDir,
            onDismiss = { showFileBrowser = false }
        )
    }
}
