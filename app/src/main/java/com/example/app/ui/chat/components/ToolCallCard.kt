package com.example.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
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
import com.example.app.ui.chat.UIToolCall

@Composable
fun ToolCallCard(toolCall: UIToolCall, modifier: Modifier = Modifier) {
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .clickable { resultExpanded = !resultExpanded }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            Text(
                text = toolCall.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (!isSuccess && !isRunning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
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
                    .padding(start = 20.dp, top = 2.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                    .padding(6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
