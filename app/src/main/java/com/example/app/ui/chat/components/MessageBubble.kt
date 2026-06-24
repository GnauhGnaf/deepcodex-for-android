package com.example.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .widthIn(max = 420.dp)
                .clip(shape)
                .background(bgColor)
                .padding(12.dp)
        ) {
            Column {
                // ── Thinking ──
                if (message.isThinking) {
                    ThinkingIndicator()
                }

                // ── Tool calls (Claude Code style: inline results, left accent border) ──
                if (!isUser && message.toolCallHistory.isNotEmpty()) {
                    ToolCallSection(message.toolCallHistory, message.isStreaming)
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(6.dp))
                }

                // ── Main text ── always visible
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

                // ── Streaming cursor ──
                if (message.isStreaming && !message.isThinking) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// Thinking indicator — subtle, like Claude Code's "thinking…"
// ────────────────────────────────────────────────────────────
@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(600),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            Icons.Default.Code,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = alpha)
        )
        Text(
            text = "思考中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
            fontSize = 12.sp
        )
    }
}

// ────────────────────────────────────────────────────────────
// Tool call section — Claude Code terminal style
// Each tool: icon + name + status · result inline with accent border
// ────────────────────────────────────────────────────────────
@Composable
private fun ToolCallSection(toolCalls: List<UIToolCall>, isStreaming: Boolean) {
    val anyRunning = toolCalls.any { it.status == "running" }
    var collapsed by remember(isStreaming, toolCalls.size) {
        mutableStateOf(false)
    }

    // Section header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { collapsed = !collapsed }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "工具调用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "${toolCalls.size} 个工具",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.sp
        )
        if (anyRunning) {
            Text(
                text = "执行中…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp
            )
        }
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
        Column(modifier = Modifier.padding(top = 2.dp)) {
            toolCalls.forEach { tc ->
                ToolCallRow(tc)
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// Single tool call row — Claude Code terminal style
// [icon] name · status
// ┃  result text (always visible inline, monospace, accent border)
// ────────────────────────────────────────────────────────────
@Composable
private fun ToolCallRow(toolCall: UIToolCall) {
    val isRunning = toolCall.status == "running"
    val isSuccess = toolCall.status == "done"
    val isError = toolCall.status == "error"

    val statusColor = when {
        isRunning -> MaterialTheme.colorScheme.primary
        isSuccess -> ColorSuccess
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    val statusLabel = when {
        isRunning -> "执行中…"
        isSuccess -> "完成"
        else -> "失败"
    }

    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        // Tool header: icon + name + status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Status dot/icon
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(statusColor)
                )
            } else if (isSuccess) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = statusColor
                )
            } else {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = statusColor
                )
            }

            Text(
                text = toolCall.name,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "·",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = statusColor
            )
        }

        // Tool result — always inline, with left accent border (Claude Code terminal style)
        if (toolCall.result.isNotBlank()) {
            Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                // Left accent border
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .heightIn(min = 16.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                )
                Spacer(Modifier.width(8.dp))
                // Result text — monospace, scrollable if wide, max height for long output
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 160.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = toolCall.result,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

// Lighter green that works on both light and dark themes
private val ColorSuccess = androidx.compose.ui.graphics.Color(0xFF2E7D32)
