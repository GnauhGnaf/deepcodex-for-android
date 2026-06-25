package com.example.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("API 配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChanged,
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (showKey) "隐藏" else "显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChanged,
                label = { Text("API 地址") },
                placeholder = { Text("https://api.deepseek.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("模型选择", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = state.model == "deepseek-v4-pro",
                    onClick = { viewModel.onModelChanged("deepseek-v4-pro") },
                    label = { Text("V4 Pro") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.model == "deepseek-v4-flash",
                    onClick = { viewModel.onModelChanged("deepseek-v4-flash") },
                    label = { Text("V4 Flash") },
                    modifier = Modifier.weight(1f)
                )
            }

            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text(
                "Codeeps — 运行在 Android 上的编程助手。\n" +
                        "所有数据保存在本地，API 密钥仅用于请求 DeepSeek 服务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("用户指南", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideStep(
                        number = "1",
                        title = "获取 DeepSeek API Key",
                        content = "访问 platform.deepseek.com 注册账号。\n" +
                                "登录后进入「API Keys」页面，点击「创建 API Key」。\n" +
                                "复制生成的 key（以 sk- 开头），粘贴到上方的 API Key 输入框。\n" +
                                "DeepSeek 新用户通常有免费额度，具体以官网为准。"
                    )
                    GuideStep(
                        number = "2",
                        title = "配置 API 地址",
                        content = "默认地址为 https://api.deepseek.com，无需修改。\n" +
                                "如使用第三方代理或兼容接口，可在此处填写自定义地址。"
                    )
                    GuideStep(
                        number = "3",
                        title = "选择模型",
                        content = "V4 Pro：能力最强，适合复杂编程任务。\n" +
                                "V4 Flash：响应更快，适合简单问答和快速迭代。"
                    )
                    GuideStep(
                        number = "4",
                        title = "开始对话",
                        content = "配置完成后返回聊天页面，输入问题即可与 AI 对话。\n" +
                                "AI 可以执行 Shell 命令、读写文件、创建项目等。\n" +
                                "每条对话拥有独立的工作区，互不干扰。"
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "提示",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "API Key 只保存在设备本地，不会上传到任何第三方服务器。\n" +
                                "切换或删除对话后，该对话的工作区文件将被清除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, content: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
