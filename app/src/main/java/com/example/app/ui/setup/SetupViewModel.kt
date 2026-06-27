package com.example.app.ui.setup

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.App
import com.example.app.data.local.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupUiState(
    // Progress
    val phase: String = "准备中...",
    val statusLines: List<String> = emptyList(),
    val overallProgress: Float = 0f,
    val linuxDone: Boolean = false,
    val depsDone: Boolean = false,
    val installHint: String = "",

    // API config
    val apiKey: String = "",
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val model: String = SettingsStore.DEFAULT_MODEL,
    val ocrApiKey: String = "",
    val ocrBaseUrl: String = SettingsStore.DEFAULT_OCR_BASE_URL,
    val showKey: Boolean = false,
    val showOcrKey: Boolean = false,

    val finished: Boolean = false
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val repo = app.container.settingsRepository

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val key = repo.apiKey.first()
            val baseUrl = repo.baseUrl.first()
            val model = repo.model.first()
            val ocrKey = repo.ocrApiKey.first()
            val ocrUrl = repo.ocrBaseUrl.first()
            _state.update {
                it.copy(apiKey = key, baseUrl = baseUrl, model = model, ocrApiKey = ocrKey, ocrBaseUrl = ocrUrl)
            }
        }
        startBackgroundInstall()
    }

    private fun startBackgroundInstall() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Phase 1: Environment init (~30s)
                _state.update { it.copy(phase = "开始初始化环境（约 5-10 分钟，可置于后台运行）", overallProgress = 0.10f) }
                appendStatus("▸ 开始初始化环境")

                val result = app.container.linuxEnvironment.setup()
                result.fold(
                    onSuccess = { log ->
                        _state.update {
                            it.copy(
                                linuxDone = true,
                                phase = "环境已就绪",
                                overallProgress = 0.45f,
                                installHint = "环境初始化完成，向下滑动继续配置"
                            )
                        }
                        for (line in log.lines().filter { it.isNotBlank() }) {
                            appendStatus("  $line")
                        }
                    },
                    onFailure = { err ->
                        _state.update { it.copy(phase = "初始化失败: ${err.message}") }
                        appendStatus("  ✗ 失败: ${err.message}")
                        Log.e("SetupVM", "Linux setup failed", err)
                    }
                )

                if (!_state.value.linuxDone) return@withContext

                // Phase 2: Skill dependencies (< 5s)
                _state.update { it.copy(phase = "正在安装技能依赖（约 5 秒）", overallProgress = 0.50f) }
                appendStatus("▸ 开始安装技能依赖")
                app.container.linuxEnvironment.installSkillDependencies { msg ->
                    if (msg.contains("✓")) {
                        appendStatus(msg)
                        val count = _state.value.statusLines.count { it.contains("✓") }
                        _state.update { it.copy(overallProgress = 0.50f + (count * 0.04f).coerceAtMost(0.20f)) }
                    } else if (msg.startsWith("⚠")) {
                        appendStatus(msg)
                    } else {
                        _state.update { it.copy(phase = msg) }
                    }
                }
                _state.update {
                    it.copy(
                        depsDone = true,
                        phase = "技能依赖已完成",
                        overallProgress = 0.70f,
                        installHint = "环境准备完成，请向下滑动填写 API 配置"
                    )
                }
                appendStatus("  ✓ 技能依赖已完成")
            }
        }
    }

    private fun appendStatus(line: String) {
        _state.update { it.copy(statusLines = it.statusLines + line) }
    }

    // API config
    fun onApiKeyChanged(key: String) {
        _state.update {
            val newProgress = if (key.isNotBlank() && it.overallProgress < 0.93f) 0.93f else it.overallProgress
            it.copy(apiKey = key, overallProgress = newProgress)
        }
        viewModelScope.launch { repo.saveApiKey(key) }
    }

    fun onBaseUrlChanged(url: String) {
        val value = url.ifBlank { SettingsStore.DEFAULT_BASE_URL }
        _state.update { it.copy(baseUrl = value) }
        viewModelScope.launch { repo.saveBaseUrl(value) }
    }

    fun onModelChanged(model: String) {
        _state.update { it.copy(model = model) }
        viewModelScope.launch { repo.saveModel(model) }
    }

    fun onOcrApiKeyChanged(key: String) {
        _state.update { it.copy(ocrApiKey = key) }
        viewModelScope.launch {
            repo.saveOcrApiKey(key)
            app.container.syncOcrConfig(key, _state.value.ocrBaseUrl)
        }
    }

    fun onOcrBaseUrlChanged(url: String) {
        val value = url.ifBlank { SettingsStore.DEFAULT_OCR_BASE_URL }
        _state.update { it.copy(ocrBaseUrl = value) }
        viewModelScope.launch {
            repo.saveOcrBaseUrl(value)
            app.container.syncOcrConfig(_state.value.ocrApiKey, value)
        }
    }

    fun toggleShowKey() { _state.update { it.copy(showKey = !it.showKey) } }
    fun toggleShowOcrKey() { _state.update { it.copy(showOcrKey = !it.showOcrKey) } }

    fun finishSetup() {
        viewModelScope.launch {
            repo.setSetupComplete()
            app.container.syncOcrConfig(_state.value.ocrApiKey, _state.value.ocrBaseUrl)
            _state.update { it.copy(overallProgress = 1.0f, finished = true) }
        }
    }
}
