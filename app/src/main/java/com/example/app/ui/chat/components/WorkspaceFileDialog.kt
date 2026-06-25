package com.example.app.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.app.util.FileExportUtil
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun WorkspaceFileDialog(
    workspaceDir: File,
    onDismiss: () -> Unit
) {
    var currentDir by remember { mutableStateOf(workspaceDir) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "工作区文件",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        scope.launch {
                            FileExportUtil.exportWorkspace(context, workspaceDir)
                        }
                    }) {
                        Text("打包下载", fontSize = 12.sp)
                    }
                }

                // Breadcrumb
                if (currentDir.canonicalPath != workspaceDir.canonicalPath) {
                    TextButton(
                        onClick = { currentDir = currentDir.parentFile ?: workspaceDir },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("返回上级目录", fontSize = 12.sp)
                    }

                    val relPath = currentDir.canonicalPath.removePrefix(workspaceDir.canonicalPath).removePrefix("/")
                    Text(
                        text = if (relPath.isEmpty()) "/" else "/$relPath",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                HorizontalDivider()

                // File list
                val files: List<File> = remember(currentDir) {
                    currentDir.listFiles()
                        ?.sortedWith(compareBy<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                        ?: emptyList()
                }

                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "目录为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(files, key = { it.absolutePath }) { file ->
                            FileRow(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        currentDir = file
                                    } else {
                                        FileExportUtil.exportFile(context, file)
                                    }
                                }
                            )
                        }
                    }
                }

                // Footer
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: File, onClick: () -> Unit) {
    val sizeText = if (file.isFile) formatFileSize(file.length()) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sizeText.isNotEmpty()) {
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
        }
        if (file.isFile) {
            Text(
                text = "下载",
                style = MaterialTheme.typography.labelSmall,
                color = CodexPink,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}

private val CodexPink = androidx.compose.ui.graphics.Color(0xFFD946EF)
