package com.example.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.app.ui.chat.UIMessage
import com.example.app.ui.chat.UIToolCall
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MessageBubble(message: UIMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    val bgColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .clip(shape)
                .background(bgColor)
                .padding(12.dp)
        ) {
            Column {
                // Tool call history section (assistant only, at top)
                if (!isUser && message.toolCallHistory.isNotEmpty()) {
                    CollapsibleToolSection(message.toolCallHistory, message.isStreaming)
                    Spacer(Modifier.height(8.dp))
                }

                // Thinking indicator
                if (message.isThinking) {
                    Text(
                        text = "思考中...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Main text content — always visible for assistant
                if (message.content.isNotBlank()) {
                    if (isUser) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    } else {
                        Markdown(
                            content = message.content,
                            colors = com.mikepenz.markdown.m3.markdownColor(text = textColor),
                            typography = com.mikepenz.markdown.m3.markdownTypography(
                                h1 = MaterialTheme.typography.headlineMedium,
                                h2 = MaterialTheme.typography.headlineSmall,
                                h3 = MaterialTheme.typography.titleLarge,
                                text = MaterialTheme.typography.bodyMedium,
                                code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                inlineCode = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            )
                        )
                    }
                }

                // Blinking cursor while streaming
                if (message.isStreaming && !message.isThinking) {
                    Text(
                        text = "▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleToolSection(toolCalls: List<UIToolCall>, isStreaming: Boolean) {
    // Auto-expand when any tool is still running, auto-collapse when all done
    val anyRunning = toolCalls.any { it.status == "running" }
    var expanded by remember(isStreaming, toolCalls.size) {
        mutableStateOf(anyRunning)
    }

    val runningCount = toolCalls.count { it.status == "running" }
    val doneCount = toolCalls.count { it.status == "done" }
    val errorCount = toolCalls.count { it.status == "error" }

    Column {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "工具调用过程",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (runningCount > 0) {
                Text(
                    text = "$runningCount 执行中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (errorCount > 0) {
                Text(
                    text = "$doneCount 完成, $errorCount 失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "$doneCount 完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }

        // Expandable tool call entries
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                toolCalls.forEach { tc ->
                    ToolCallEntry(tc)
                }
            }
        }
    }
}

@Composable
private fun ToolCallEntry(toolCall: UIToolCall) {
    var resultExpanded by remember { mutableStateOf(false) }
    val isRunning = toolCall.status == "running"
    val isSuccess = toolCall.status == "done"

    val icon = when {
        isRunning -> Icons.Default.Build
        isSuccess -> Icons.Default.CheckCircle
        else -> Icons.Default.Error
    }
    val iconTint = when {
        isRunning -> MaterialTheme.colorScheme.primary
        isSuccess -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val statusText = when {
        isRunning -> "执行中..."
        isSuccess -> "完成"
        else -> "失败"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { resultExpanded = !resultExpanded }
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            Text(
                text = toolCall.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (!isSuccess && !isRunning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = iconTint
            )
            if (toolCall.result.isNotBlank()) {
                Icon(
                    if (resultExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "查看结果",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }

        AnimatedVisibility(visible = resultExpanded && toolCall.result.isNotBlank()) {
            Text(
                text = toolCall.result,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .padding(start = 18.dp, top = 2.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                    .padding(6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
