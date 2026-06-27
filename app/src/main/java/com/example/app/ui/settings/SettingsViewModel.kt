package com.example.app.ui.settings

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
import java.io.File

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val model: String = SettingsStore.DEFAULT_MODEL,
    val ocrApiKey: String = "",
    val ocrBaseUrl: String = SettingsStore.DEFAULT_OCR_BASE_URL,
    val advancedExpanded: Boolean = false,
    val loInstallStatus: String = "", // "", "installing", "installed", error message
    val isLoInstalling: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = (application as App)
    private val repo = app.container.settingsRepository

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.apiKey.collect { _state.update { s -> s.copy(apiKey = it) } }
        }
        viewModelScope.launch {
            repo.baseUrl.collect { _state.update { s -> s.copy(baseUrl = it) } }
        }
        viewModelScope.launch {
            repo.model.collect { _state.update { s -> s.copy(model = it) } }
        }
        viewModelScope.launch {
            repo.ocrApiKey.collect { _state.update { s -> s.copy(ocrApiKey = it) } }
        }
        viewModelScope.launch {
            repo.ocrBaseUrl.collect { _state.update { s -> s.copy(ocrBaseUrl = it) } }
        }
        // Sync OCR config on startup (if user previously configured it)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                app.container.syncOcrConfig(repo.ocrApiKey.first(), repo.ocrBaseUrl.first())
            }
        }
        // Check LibreOffice status
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val isLoReady = app.container.linuxEnvironment.isLibreOfficeReady
                if (isLoReady) {
                    _state.update { it.copy(loInstallStatus = "installed") }
                }
            }
        }
    }

    fun onApiKeyChanged(key: String) {
        _state.update { it.copy(apiKey = key) }
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

    fun toggleAdvanced() {
        _state.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    fun installLibreOffice(workspaceDir: File) {
        if (_state.value.isLoInstalling) return
        _state.update { it.copy(isLoInstalling = true, loInstallStatus = "installing") }
        viewModelScope.launch {
            val result = app.container.linuxEnvironment.installLibreOffice(
                workspaceDir = workspaceDir,
                onProgress = { msg ->
                    _state.update { it.copy(loInstallStatus = msg) }
                }
            )
            result.fold(
                onSuccess = { msg ->
                    _state.update { it.copy(isLoInstalling = false, loInstallStatus = "installed") }
                    Log.d("SettingsVM", "LibreOffice installed: $msg")
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(isLoInstalling = false, loInstallStatus = "安装失败: ${err.message}")
                    }
                    Log.e("SettingsVM", "LibreOffice install failed", err)
                }
            )
        }
    }
}
