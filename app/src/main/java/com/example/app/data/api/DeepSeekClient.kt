package com.example.app.data.api

import com.example.app.data.api.models.ChatRequest
import com.example.app.data.api.models.ChatResponse
import com.example.app.util.SseParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class DeepSeekClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun streamChat(
        apiKey: String,
        baseUrl: String,
        request: ChatRequest
    ): Flow<ChatResponse> = callbackFlow {
        val jsonBody = json.encodeToString(ChatRequest.serializer(), request)

        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(httpRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    close(IOException("HTTP ${response.code}: ${response.body?.string()}"))
                    return
                }

                val body = response.body ?: run {
                    close(IOException("Empty response body"))
                    return
                }

                try {
                    val source = body.source()
                    var pendingId: String? = null
                    var pendingName: String? = null

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val parsed = SseParser.parse(line) ?: continue

                        if (parsed.choices.isEmpty()) continue
                        val delta = parsed.choices[0].delta

                        val resolvedId = delta.toolCalls?.firstOrNull()?.id ?: pendingId
                        val resolvedName = delta.toolCalls?.firstOrNull()?.function?.name ?: pendingName
                        if (resolvedId != null) pendingId = resolvedId
                        if (resolvedName != null) pendingName = resolvedName

                        val enriched = if (delta.toolCalls != null && delta.toolCalls.isNotEmpty()) {
                            val tc = delta.toolCalls[0]
                            val updatedTc = tc.copy(
                                id = tc.id ?: pendingId,
                                function = tc.function?.copy(name = tc.function.name ?: pendingName)
                            )
                            parsed.copy(choices = listOf(
                                parsed.choices[0].copy(delta = delta.copy(toolCalls = listOf(updatedTc)))
                            ))
                        } else {
                            parsed
                        }
                        trySend(enriched)
                    }
                } catch (e: Exception) {
                    close(e)
                } finally {
                    response.close()
                }
                close()
            }
        })

        awaitClose()
    }
}
