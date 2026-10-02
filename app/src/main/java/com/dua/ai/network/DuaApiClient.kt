package com.dua.ai.network

import com.dua.ai.BuildConfig
import com.dua.ai.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

/** Both clients use the same server-owned Gemini key and model configuration. */
class DuaApiClient(private val baseUrl: String = BuildConfig.DUA_API_BASE_URL) {
    suspend fun reply(model: String, history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        require(model in listOf("Dua", "Gemini")) { "Dua ya Gemini select karein." }
        val base = URL(baseUrl.trimEnd('/') + "/")
        require(base.protocol == "https") { "Dua backend ke liye HTTPS URL set karein." }
        val messages = JSONArray()
        history.takeLast(39).forEach { message ->
            messages.put(JSONObject().put("role", if (message.isUser) "user" else "model").put("text", message.text))
        }
        val body = JSONObject().put("model", model).put("messages", messages).toString().toByteArray(Charsets.UTF_8)
        require(body.size <= 262144) { "Conversation too large. New chat shuru karein." }
        val connection = URL(base, "api/chat").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            if (!contentType.contains("application/json", ignoreCase = true)) {
                throw DuaApiException("Dua backend available nahi hai. Hosting access settings check karein.")
            }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 1048576) throw DuaApiException("Dua response bahut bada hai.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: byteArrayOf()
            if (bytes.size > 1048576) throw DuaApiException("Dua response bahut bada hai.")
            val result = try { JSONObject(bytes.toString(Charsets.UTF_8)) }
                catch (_: Exception) { throw DuaApiException("Dua server se valid response nahi mila.") }
            if (status !in 200..299) throw DuaApiException(
                result.optString("error").takeIf { it.isNotBlank() }
                    ?: "Reply nahi aa saka. Dobara try karein."
            )
            result.optString("text").takeIf { it.isNotBlank() }
                ?: throw DuaApiException("Gemini ne text response nahi diya. Dobara try karein.")
        } finally {
            connection.disconnect()
        }
    }
}

class DuaApiException(message: String) : Exception(message)
