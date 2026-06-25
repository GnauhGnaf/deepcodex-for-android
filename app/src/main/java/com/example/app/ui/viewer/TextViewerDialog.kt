package com.example.app.ui.viewer

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.app.util.CodeHighlighter
import com.example.app.util.FileExportUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TextViewerDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var html by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                if (bytes.size > 2 * 1024 * 1024) {
                    error = "文件过大（${formatSize(bytes.size.toLong())}），请使用下载功能。支持最大 2MB 的文本预览。"
                    loading = false
                    return@withContext
                }
                val code = String(bytes, Charsets.UTF_8)
                val lang = CodeHighlighter.detectLanguage(file.name)
                html = CodeHighlighter.highlight(code, lang)
            } catch (e: Exception) {
                error = "无法读取文件: ${e.message}"
            }
            loading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // Header bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lang = remember(file) { CodeHighlighter.detectLanguage(file.name) }
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            lang.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { FileExportUtil.exportFile(context, file) }) {
                        Text("下载", fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                HorizontalDivider()

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    // WebView with syntax-highlighted code
                    AndroidView(
                        factory = {
                            WebView(it).apply {
                                setBackgroundColor(0xFF1E1E1E.toInt())
                                isVerticalScrollBarEnabled = true
                                settings.apply {
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    javaScriptEnabled = false
                                }
                            }
                        },
                        update = { wv -> wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}
