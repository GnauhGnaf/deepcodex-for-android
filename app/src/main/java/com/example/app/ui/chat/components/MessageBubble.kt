package com.example.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.app.ui.chat.UIMessage
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
                .widthIn(max = 360.dp)
                .clip(shape)
                .background(bgColor)
                .padding(12.dp)
        ) {
            Column {
                if (message.isThinking) {
                    Text(
                        text = "思考中...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
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
                if (message.isStreaming && !message.isThinking) {
                    Text(
                        text = "▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                message.toolCalls.forEach { tc ->
                    ToolCallCard(tc)
                }
            }
        }
    }
}
