package com.example.app.data.repository

import com.example.app.data.local.SettingsStore
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val store: SettingsStore) {

    val apiKey: Flow<String> = store.apiKey
    val baseUrl: Flow<String> = store.baseUrl
    val model: Flow<String> = store.model

    suspend fun saveApiKey(key: String) = store.setApiKey(key)
    suspend fun saveBaseUrl(url: String) = store.setBaseUrl(url)
    suspend fun saveModel(model: String) = store.setModel(model)
}
