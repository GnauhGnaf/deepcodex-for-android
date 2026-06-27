package com.example.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.App
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showKey by remember { mutableStateOf(false) }
    var showOcrKey by remember { mutableStateOf(false) }

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
            // ── API 配置 ──
            Text("API 配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChanged,
                label = { Text("DeepSeek API Key") },
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

            // ── Linux 环境 ──
            Text("Linux 环境", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "LibreOffice 文档转换引擎",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "用于将 Office 文档（DOCX/XLSX/PPTX）转换为 PDF，支持在应用中直接渲染预览。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    when {
                        state.isLoInstalling -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.loInstallStatus.ifBlank { "正在安装..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        state.loInstallStatus == "installed" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "LibreOffice 已安装",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        state.loInstallStatus.startsWith("安装失败") -> {
                            Text(
                                state.loInstallStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    val workspaceDir = File(
                                        (viewModel.getApplication() as App).filesDir,
                                        "workspace/default"
                                    ).also { it.mkdirs() }
                                    viewModel.installLibreOffice(workspaceDir)
                                }
                            ) {
                                Text("重试安装")
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    val workspaceDir = File(
                                        (viewModel.getApplication() as App).filesDir,
                                        "workspace/default"
                                    ).also { it.mkdirs() }
                                    viewModel.installLibreOffice(workspaceDir)
                                }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("下载并安装 LibreOffice")
                            }
                        }
                    }
                    Text(
                        "约需下载 150-200MB，安装后增约 500MB 磁盘占用。需要网络连接。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 高级设置 ──
            Text("高级设置", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    TextButton(
                        onClick = viewModel::toggleAdvanced,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (state.advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("OCR 图像文字识别", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (state.ocrApiKey.isNotBlank()) "已配置" else "未配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.ocrApiKey.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = state.advancedExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "通过硅基流动 (SiliconFlow) 的 DeepSeek-OCR 模型识别图片和 PDF 中的文字。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = state.ocrApiKey,
                                onValueChange = viewModel::onOcrApiKeyChanged,
                                label = { Text("SiliconFlow API Key") },
                                placeholder = { Text("sk-...") },
                                visualTransformation = if (showOcrKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showOcrKey = !showOcrKey }) {
                                        Icon(
                                            if (showOcrKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = state.ocrBaseUrl,
                                onValueChange = viewModel::onOcrBaseUrlChanged,
                                label = { Text("API 地址") },
                                placeholder = { Text("https://api.siliconflow.cn/v1/chat/completions") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Text(
                                "注册地址: cloud.siliconflow.cn → API 密钥 → 新建密钥",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 用户指南 ──
            Text("用户指南", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideStep(
                        number = "1",
                        title = "获取 DeepSeek API Key",
                        content = "访问 platform.deepseek.com 注册账号。\n" +
                                "登录后进入「API Keys」页面，点击「创建 API Key」。\n" +
                                "复制生成的 key（以 sk- 开头），粘贴到上方的 API Key 输入框。\n" +
                                "DeepSeek 新用户通常有免费额度可供试用。"
                    )
                    GuideStep(
                        number = "2",
                        title = "配置 API 地址",
                        content = "默认地址为 https://api.deepseek.com，一般无需修改。\n" +
                                "如使用第三方代理或兼容接口，可在此处填写自定义地址。"
                    )
                    GuideStep(
                        number = "3",
                        title = "选择模型",
                        content = "V4 Pro：能力最强，适合复杂编程任务，支持推理过程展示。\n" +
                                "V4 Flash：响应更快，成本更低，适合简单问答和快速迭代。"
                    )
                    GuideStep(
                        number = "4",
                        title = "开始对话",
                        content = "返回聊天页面，输入问题即可与 AI 对话。\n" +
                                "AI 可在 Linux 沙箱中执行 Shell 命令、读写文件、创建项目。\n" +
                                "每条对话拥有独立的工作区，不同对话之间文件互不干扰。"
                    )
                    GuideStep(
                        number = "5",
                        title = "文件导入",
                        content = "点击输入框左侧的附件图标，可从设备中选择文件导入到工作区。\n" +
                                "AI 可直接读取和分析导入的文件内容。"
                    )
                    GuideStep(
                        number = "6",
                        title = "工具调用",
                        content = "AI 拥有 5 个内置工具：读取文件、写入文件、列出目录、搜索文件、执行命令。\n" +
                                "工具调用结果默认折叠显示一行摘要，点击可展开查看完整内容。\n" +
                                "执行的 Shell 命令在 Linux 沙箱内运行，不会影响手机系统。"
                    )
                }
            }

            // ── Linux 环境说明 ──
            Text("Linux 沙箱说明", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Codeeps 内置了一个基于 proot 的 Alpine Linux 环境，",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "特性：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    BulletItem("预装 Python 3.14，支持 pip 包安装")
                    BulletItem("可安装 LibreOffice 实现 Office 文档转换")
                    BulletItem("运行时安装中文字体 (font-wqy-zenhei)")
                    BulletItem("阿里云 (Alpine) 官方软件源，支持 apk 安装数千个软件包")
                    BulletItem("伪造的 /proc 和绑定的 /dev、/sys，安全隔离")
                    BulletItem("无 Root 权限运行，不影响手机系统文件")
                    BulletItem("首次启动需 1-3 分钟初始化（解压根文件系统 + 安装依赖）")
                }
            }

            // ── 文档转换 ──
            Text("文档转换", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "安装 LibreOffice 后，应用可将 Office 文档转为 PDF 进行在线预览：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    BulletItem("转换格式：DOCX → PDF、XLSX → PDF、PPTX → PDF")
                    BulletItem("通过 LibreOffice headless 模式在后台完成")
                    BulletItem("PDF 通过 Android PdfRenderer 渲染显示")
                    BulletItem("保持原始排版、字体、表格、图片")
                    Text(
                        "LibreOffice 较大（约 500MB），按需安装。安装按钮在「Linux 环境」卡片中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── OCR 说明 ──
            Text("OCR 文字识别", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "通过硅基流动 (SiliconFlow) 的 DeepSeek-OCR 模型识别图片和 PDF 中的文字：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    BulletItem("支持格式：PNG、JPG、WebP、PDF")
                    BulletItem("三种模式：free_ocr（快速，适合 80% 场景）、grounding（复杂表格）、ocr_image（精细提取）")
                    BulletItem("API Key 在 cloud.siliconflow.cn 免费获取")
                    BulletItem("在「高级设置」中展开配置 OCR API Key 后即可使用")
                    BulletItem("使用时在聊天中发送图片或 PDF 文件，AI 会自动调用 OCR 识别文字")
                }
            }

            // ── 提示 ──
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
                        "所有 API Key 及配置仅保存在设备本地，不会上传到任何第三方服务器。\n" +
                                "Linux 沙箱中的操作不会影响手机系统。\n" +
                                "对话切换或删除后，对应工作区文件将被清除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Text(
                "Codeeps — 运行在 Android 上的 AI 编程助手",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BulletItem(text: String) {
    Row(modifier = Modifier.padding(start = 8.dp)) {
        Text("•", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
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
