package com.example.app.ui.viewer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.app.domain.LinuxEnvironment
import com.example.app.util.FileCategory
import com.example.app.util.FileTypeUtil
import java.io.File

@Composable
fun FileViewerDialog(
    file: File,
    onDismiss: () -> Unit,
    linuxEnv: LinuxEnvironment? = null,
    workspaceDir: File? = null
) {
    val category = remember(file) { FileTypeUtil.categorize(file) }
    val ext = remember(file) { file.extension }

    when (category) {
        FileCategory.TEXT -> TextViewerDialog(file, onDismiss)
        FileCategory.IMAGE -> ImageViewerDialog(file, onDismiss)
        FileCategory.PDF -> PdfViewerDialog(file, onDismiss)
        FileCategory.DOCX, FileCategory.XLSX, FileCategory.PPTX ->
            OfficeViewerDialog(file, ext, onDismiss, linuxEnv, workspaceDir)
        FileCategory.VIDEO -> VideoViewerDialog(file, onDismiss)
        FileCategory.UNKNOWN -> UnsupportedViewerDialog(file, onDismiss)
    }
}

@Composable
private fun UnsupportedViewerDialog(file: File, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.5f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                HorizontalDivider()
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "无法预览此文件类型",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ".${file.extension} 文件暂不支持在线查看",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "请使用下载功能获取文件后使用其他应用打开",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
