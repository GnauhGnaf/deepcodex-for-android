package com.example.app.data.repository

import com.example.app.data.local.SettingsStore
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val store: SettingsStore) {

    val apiKey: Flow<String> = store.apiKey
    val baseUrl: Flow<String> = store.baseUrl
    val model: Flow<String> = store.model
    val ocrApiKey: Flow<String> = store.ocrApiKey
    val ocrBaseUrl: Flow<String> = store.ocrBaseUrl
    val setupComplete: Flow<Boolean> = store.setupComplete

    suspend fun saveApiKey(key: String) = store.setApiKey(key)
    suspend fun saveBaseUrl(url: String) = store.setBaseUrl(url)
    suspend fun saveModel(model: String) = store.setModel(model)
    suspend fun saveOcrApiKey(key: String) = store.setOcrApiKey(key)
    suspend fun saveOcrBaseUrl(url: String) = store.setOcrBaseUrl(url)
    suspend fun setSetupComplete() = store.setSetupComplete()
}
