package com.example.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.App
import com.example.app.data.local.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val model: String = SettingsStore.DEFAULT_MODEL
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as App).container.settingsRepository

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Load persisted values into UI state immediately
        viewModelScope.launch {
            repo.apiKey.collect { _state.update { s -> s.copy(apiKey = it) } }
        }
        viewModelScope.launch {
            repo.baseUrl.collect { _state.update { s -> s.copy(baseUrl = it) } }
        }
        viewModelScope.launch {
            repo.model.collect { _state.update { s -> s.copy(model = it) } }
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
}
