package com.example.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codeeps")

class SettingsStore(private val context: Context) {

    companion object {
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_OCR_API_KEY = stringPreferencesKey("ocr_api_key")
        val KEY_OCR_BASE_URL = stringPreferencesKey("ocr_base_url")
        val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")

        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-v4-pro"
        const val DEFAULT_OCR_BASE_URL = "https://api.siliconflow.cn/v1/chat/completions"
    }

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_KEY] ?: ""
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL
    }

    val model: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MODEL] ?: DEFAULT_MODEL
    }

    val ocrApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OCR_API_KEY] ?: ""
    }

    val ocrBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OCR_BASE_URL] ?: DEFAULT_OCR_BASE_URL
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url }
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { it[KEY_MODEL] = model }
    }

    suspend fun setOcrApiKey(key: String) {
        context.dataStore.edit { it[KEY_OCR_API_KEY] = key }
    }

    suspend fun setOcrBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_OCR_BASE_URL] = url }
    }

    val setupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETUP_COMPLETE] ?: false
    }

    suspend fun setSetupComplete() {
        context.dataStore.edit { it[KEY_SETUP_COMPLETE] = true }
    }
}
