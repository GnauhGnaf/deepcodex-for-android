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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
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

    if (isUser) {
        UserBubble(message, modifier)
    } else {
        AssistantBlock(message, modifier)
    }
}

// ────────────────────────────────────────────────────────────
// User message — simple bubble, right-aligned
// ────────────────────────────────────────────────────────────
@Composable
private fun UserBubble(message: UIMessage, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
// Assistant block — Codex terminal style
// Layout: reasoning → tools → divider → text → cursor
// ────────────────────────────────────────────────────────────
@Composable
private fun AssistantBlock(message: UIMessage, modifier: Modifier) {
    val hasTools = message.toolCallHistory.isNotEmpty()
    val hasReasoning = message.reasoning.isNotBlank()
    val hasContent = message.content.isNotBlank()
    val isThinking = message.isThinking && message.reasoning.isEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(10.dp)
    ) {
        // ── Thinking (no reasoning text yet) ──
        if (isThinking) {
            PulsingThinking()
        }

        // ── Reasoning — inline dimmed text, flows into main content ──
        if (hasReasoning) {
            if (hasContent) {
                // reasoning + content together, reasoning as muted prefix
                Text(
                    text = message.reasoning,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )
                Spacer(Modifier.height(6.dp))
            } else {
                // No content yet — reasoning IS the current output
                Text(
                    text = message.reasoning,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        // ── Tool calls ──
        if (hasTools) {
            ToolCallSection(message.toolCallHistory, message.isStreaming)
        }

        // ── Divider (between tools and text) ──
        if (hasTools && hasContent && !hasReasoning) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
            Spacer(Modifier.height(6.dp))
        }

        // ── Main text — always visible ──
        if (hasContent) {
            Markdown(
                content = message.content,
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

// ────────────────────────────────────────────────────────────
// Pulsing "思考中…" when the model hasn't emitted reasoning yet
// ────────────────────────────────────────────────────────────
@Composable
private fun PulsingThinking() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        Icon(
            Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
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
// Tool call section — Codex terminal style
// Each tool: [status] name · status
// ┃  result inline with accent border
// ────────────────────────────────────────────────────────────
@Composable
private fun ToolCallSection(toolCalls: List<UIToolCall>, isStreaming: Boolean) {
    val anyRunning = toolCalls.any { it.status == "running" }
    var collapsed by remember(isStreaming, toolCalls.size) {
        mutableStateOf(false)
    }

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
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "${toolCalls.size} 个",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
        if (anyRunning) {
            Text(
                text = "执行中…",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
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
            toolCalls.forEach { tc -> ToolCallRow(tc) }
        }
    }
}

// ────────────────────────────────────────────────────────────
// Single tool call row
// [icon] name · status
// ┃  result (always visible, monospace, accent border)
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

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(statusColor)
                )
            } else if (isSuccess) {
                Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = statusColor)
            } else {
                Icon(Icons.Default.Close, null, Modifier.size(12.dp), tint = statusColor)
            }

            Text(
                text = toolCall.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                fontSize = 12.sp,
                color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
            )

            Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = statusColor
            )
        }

        if (toolCall.result.isNotBlank()) {
            Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
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
                Text(
                    text = toolCall.result,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val ColorSuccess = androidx.compose.ui.graphics.Color(0xFF2E7D32)
