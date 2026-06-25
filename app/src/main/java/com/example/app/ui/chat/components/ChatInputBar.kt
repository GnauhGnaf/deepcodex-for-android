package com.example.app.ui.chat.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.App
import com.example.app.util.FileImportUtil
import kotlinx.coroutines.launch

data class PendingFile(val uri: Uri, val name: String, val size: Long = 0)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var pendingFiles by remember { mutableStateOf(listOf<PendingFile>()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newFiles = uris.map { uri ->
                PendingFile(uri = uri, name = queryFileName(context, uri))
            }
            pendingFiles = pendingFiles + newFiles
        }
    }

    fun doSend() {
        val msg = text.trim()
        if (msg.isBlank() && pendingFiles.isEmpty()) return

        scope.launch {
            val finalText = if (pendingFiles.isNotEmpty()) {
                val app = context.applicationContext as App
                val workspaceDir = app.container.toolExecutor.workspaceDir
                val folder = FileImportUtil.importFiles(context, pendingFiles.map { it.uri }, workspaceDir)
                val fileNames = pendingFiles.joinToString(", ") { it.name }
                "[已导入 ${pendingFiles.size} 个文件到 $folder/ 目录: $fileNames]\n\n$msg"
            } else {
                msg
            }

            text = ""
            pendingFiles = emptyList()
            onSend(finalText)
        }
    }

    fun removeFile(index: Int) {
        pendingFiles = pendingFiles.toMutableList().also { it.removeAt(index) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // File preview bar
        if (pendingFiles.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(pendingFiles, key = { it.uri.toString() }) { file ->
                    FileChip(file = file, onRemove = {
                        val idx = pendingFiles.indexOf(file)
                        if (idx >= 0) removeFile(idx)
                    })
                }
            }
        }

        // Input row
        Surface(
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = "选择文件",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("输入消息…", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.key == Key.Enter) {
                                doSend()
                                true
                            } else false
                        },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { doSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                if (isLoading) {
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "停止",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    val canSend = text.isNotBlank() || pendingFiles.isNotEmpty()
                    IconButton(onClick = { doSend() }, enabled = canSend) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileChip(file: PendingFile, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp)
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(18.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == "file") {
        return uri.path?.substringAfterLast('/') ?: "unknown"
    }
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return cursor.getString(idx)
        }
    }
    return uri.lastPathSegment ?: "unknown"
}
