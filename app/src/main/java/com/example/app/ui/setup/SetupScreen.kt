package com.example.app.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.finished) {
        if (state.finished) onSetupComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Codeeps 初始化设置") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Progress bar (always visible)
            LinearProgressIndicator(
                progress = { state.overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = state.phase,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (state.installHint.isNotEmpty()) {
                Text(
                    text = state.installHint,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Install status (collapsible)
                if (state.statusLines.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(true) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "安装进度",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(
                                        if (expanded) "收起" else "展开",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            if (expanded) {
                                val lines = state.statusLines.takeLast(20)
                                Text(
                                    text = if (state.statusLines.size > 20) {
                                        "... (${state.statusLines.size - 20} 条更早) \n" + lines.joinToString("\n")
                                    } else {
                                        lines.joinToString("\n")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // DeepSeek API
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "DeepSeek API（必需）",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        val deepseekUrl = "https://platform.deepseek.com"
                        val annotatedString = buildAnnotatedString {
                            append("获取地址：")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append(deepseekUrl)
                            }
                            addStringAnnotation("URL", deepseekUrl, 5, 5 + deepseekUrl.length)
                        }
                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodySmall,
                            onClick = { offset ->
                                annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                                }
                            }
                        )
                        Text(
                            "注册账号 → API Keys → 创建 Key → 复制 sk- 开头的密钥",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = state.apiKey,
                            onValueChange = viewModel::onApiKeyChanged,
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            visualTransformation = if (state.showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = viewModel::toggleShowKey) {
                                    Icon(
                                        if (state.showKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("模型选择", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = state.model == "deepseek-v4-pro",
                                onClick = { viewModel.onModelChanged("deepseek-v4-pro") },
                                label = { Text("V4 Pro", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = state.model == "deepseek-v4-flash",
                                onClick = { viewModel.onModelChanged("deepseek-v4-flash") },
                                label = { Text("V4 Flash", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // OCR API
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ImageSearch,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "OCR 文字识别（可选）",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        val sfUrl = "https://cloud.siliconflow.cn"
                        val sfAnnotated = buildAnnotatedString {
                            append("获取地址：")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(sfUrl) }
                            addStringAnnotation("URL", sfUrl, 5, 5 + sfUrl.length)
                        }
                        ClickableText(
                            text = sfAnnotated,
                            style = MaterialTheme.typography.bodySmall,
                            onClick = { offset ->
                                sfAnnotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                                }
                            }
                        )
                        Text(
                            "注册 → API 密钥 → 新建密钥 → 复制粘贴",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = state.ocrApiKey,
                            onValueChange = viewModel::onOcrApiKeyChanged,
                            label = { Text("SiliconFlow API Key") },
                            placeholder = { Text("sk-...（可选）") },
                            visualTransformation = if (state.showOcrKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = viewModel::toggleShowOcrKey) {
                                    Icon(
                                        if (state.showOcrKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // Privacy note
                Text(
                    "所有配置仅保存在你的设备上，不上传任何第三方服务器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Finish button
                Button(
                    onClick = viewModel::finishSetup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    enabled = state.linuxDone && state.apiKey.isNotBlank()
                ) {
                    Text(
                        when {
                            !state.linuxDone -> "环境初始化中，请稍候..."
                            state.apiKey.isBlank() -> "请先填入 DeepSeek API Key"
                            else -> "完成 — 开始使用 Codeeps"
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
