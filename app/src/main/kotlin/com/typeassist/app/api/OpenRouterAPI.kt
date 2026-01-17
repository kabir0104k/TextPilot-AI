package com.typeassist.app.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OpenRouterApiClient(
    private val client: OkHttpClient = OkHttpClient()
) {

    fun callOpenRouter(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        callback: (Result<String>) -> Unit
    ) {
        Thread {
            try {
                val body = """
                {
                  "model": "$model",
                  "messages": [
                    { "role": "system", "content": "$systemPrompt" },
                    { "role": "user", "content": "$userText" }
                  ],
                  "max_tokens": 256,
                  "temperature": 0.2
                }
                """.trimIndent()

                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val json = JSONObject(response.body!!.string())

                val output = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                callback(Result.success(output))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }.start()
    }
}