package com.example.llm

import com.example.model.PresetsData
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Selects a motion preset through an OpenAI-compatible chat API (Mistral or xAI Grok). Only the prompt text is sent. */
object ChatMotionSelector {
    fun buildRequest(model: String, prompt: String): String {
        val options = PresetsData.presets.joinToString("\n") { "${it.id}: ${it.description}" }
        val system = "Select the closest motion preset for the user's description. " +
            "Reply with only one preset identifier from this list, no explanation:\n$options"
        return JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put("max_tokens", 24)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", prompt)))
            .toString()
    }

    fun parseReply(body: String): String {
        val content = JSONObject(body).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "")
        require(content.isNotBlank()) { "Empty LLM reply" }
        return content
    }

    suspend fun select(
        provider: LlmProvider,
        apiKey: String,
        prompt: String,
        endpoint: String = provider.endpoint
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "No API key for ${provider.label}" }
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.outputStream.use { it.write(buildRequest(provider.model, prompt).toByteArray()) }
            check(conn.responseCode in 200..299) { "${provider.label} HTTP ${conn.responseCode}" }
            parseReply(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
