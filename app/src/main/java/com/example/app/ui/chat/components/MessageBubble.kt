package com.example.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.App
import com.example.app.ui.chat.ContentBlock
import com.example.app.ui.chat.UIMessage
import com.example.app.ui.chat.UIToolCall
import com.example.app.util.FileExportUtil
import com.mikepenz.markdown.m3.Markdown
import java.io.File

// ────────────────────────────────────────────────────────────
// Claude Code / Codex terminal-style message rendering
// ────────────────────────────────────────────────────────────

@Composable
fun MessageBubble(message: UIMessage, onBrowseFiles: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    if (message.role == "user") {
        UserBubble(message, modifier)
    } else {
        AssistantBlock(message, onBrowseFiles, modifier)
    }
}

// ────────────────────────────────────────────────────────────
// User — subtle right-align, no bubble, muted color
// ────────────────────────────────────────────────────────────
@Composable
private fun UserBubble(message: UIMessage, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 500.dp)
        )
    }
}

// ────────────────────────────────────────────────────────────
// Assistant — plain text flow, no bubble, full-width markdown
// ────────────────────────────────────────────────────────────
@Composable
private fun AssistantBlock(message: UIMessage, onBrowseFiles: (() -> Unit)?, modifier: Modifier) {
    val blocks = message.blocks
    val hasReasoning = blocks.any { it is ContentBlock.Reasoning }
    val lastReasoningIdx = blocks.indexOfLast { it is ContentBlock.Reasoning }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Pulsing indicator when model is thinking but no reasoning emitted yet
        if (message.isThinking && !hasReasoning) {
            ThinkingLabel(isActive = true, charCount = 0)
        }

        blocks.forEachIndexed { index, block ->
            when (block) {
                is ContentBlock.Reasoning -> {
                    ThinkingBlock(block.text, index == lastReasoningIdx && message.isThinking)
                }
                is ContentBlock.ToolCalls -> {
                    block.calls.forEach { tc -> ToolCallRow(tc) }
                }
                is ContentBlock.Text -> {
                    if (block.text.isNotBlank()) {
                        Markdown(
                            content = block.text,
                            colors = com.mikepenz.markdown.m3.markdownColor(
                                text = MaterialTheme.colorScheme.onSurface
                            ),
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
            }
            if (index < blocks.lastIndex) {
                Spacer(Modifier.height(8.dp))
            }
        }

        // Output file downloads (only after generation is complete)
        if (message.outputFiles.isNotEmpty() && !message.isStreaming) {
            Spacer(Modifier.height(8.dp))
            OutputFilesSection(message.outputFiles, onBrowseFiles)
        }

        // Streaming cursor
        if (message.isStreaming && !message.isThinking) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun OutputFilesSection(outputFiles: List<String>, onBrowseFiles: (() -> Unit)?) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val workspaceDir = app.container.toolExecutor.workspaceDir

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        Text(
            text = "输出文件",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        outputFiles.forEach { relativePath ->
            val file = File(workspaceDir, relativePath)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = relativePath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { FileExportUtil.exportFile(context, file) },
                    modifier = Modifier.height(24.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "下载",
                        style = MaterialTheme.typography.labelSmall,
                        color = CodexPink,
                        fontSize = 10.sp
                    )
                }
            }
        }
        if (onBrowseFiles != null) {
            TextButton(
                onClick = onBrowseFiles,
                modifier = Modifier.fillMaxWidth().height(28.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "浏览全部文件 →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// Thinking — Claude Code style collapsible section
// ────────────────────────────────────────────────────────────
@Composable
private fun ThinkingLabel(isActive: Boolean, charCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Text(
            text = "思考中…",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
        )
        if (charCount > 0) {
            Text(
                text = "· $charCount 字",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ThinkingBlock(reasoning: String, isThinking: Boolean) {
    var collapsed by remember(isThinking) { mutableStateOf(true) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { collapsed = !collapsed }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "思考中…",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = if (isThinking) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
            )
            Text(
                text = "· ${reasoning.length} 字",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = if (collapsed) "展开" else "收起",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp)) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .heightIn(min = 12.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// Tool call — Claude Code terminal format
//   ⏺ name(args)
//     ⎿  result (markdown-rendered)
// ────────────────────────────────────────────────────────────
@Composable
private fun ToolCallRow(toolCall: UIToolCall) {
    val isRunning = toolCall.status == "running"
    val isError = toolCall.status == "error"

    val accentColor = when {
        isRunning -> MaterialTheme.colorScheme.primary
        isError -> MaterialTheme.colorScheme.error
        else -> CodexPink
    }

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        // ⏺ tool_name
        Text(
            text = "⏺ ${toolCall.name}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            fontSize = 12.sp,
            color = accentColor
        )

        // ⎿  result as markdown
        if (toolCall.result.isNotBlank()) {
            val resultColor = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

            Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                Text(
                    text = "⏿",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontSize = 12.sp,
                    color = resultColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.width(6.dp))
                Markdown(
                    content = toolCall.result,
                    colors = com.mikepenz.markdown.m3.markdownColor(
                        text = resultColor
                    ),
                    typography = com.mikepenz.markdown.m3.markdownTypography(
                        text = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        code = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        inlineCode = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        h1 = MaterialTheme.typography.labelLarge,
                        h2 = MaterialTheme.typography.labelLarge,
                        h3 = MaterialTheme.typography.labelMedium
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private val CodexPink = androidx.compose.ui.graphics.Color(0xFFD946EF)
