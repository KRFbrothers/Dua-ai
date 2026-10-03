package com.dua.ai.network

import com.dua.ai.BuildConfig
import com.dua.ai.model.ChatMessage
import com.dua.ai.attachments.ChatAttachment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.io.ByteArrayOutputStream

/** Both clients use the same server-owned Gemini key and model configuration. */
class DuaApiClient(
    private val baseUrl: String = BuildConfig.DUA_API_BASE_URL,
    private val client: OkHttpClient = sharedClient
) {
    suspend fun reply(model: String, history: List<ChatMessage>, attachments: List<ChatAttachment> = emptyList()): String {
        require(model in listOf("Dua", "Gemini")) { "Dua ya Gemini select karein." }
        val base = URL(baseUrl.trimEnd('/') + "/")
        require(base.protocol == "https") { "Dua backend ke liye HTTPS URL set karein." }
        val request = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        history.filter { it.state == "complete" }.takeLast(39).forEach { message ->
            messages.put(JSONObject().put("role", if (message.isUser) "user" else "model").put("text", message.text))
        }
        require(messages.toString().toByteArray(Charsets.UTF_8).size <= 262144) { "Conversation too large. New chat shuru karein." }
        val files = JSONArray().apply { attachments.forEach { file ->
            put(JSONObject().put("name", file.name).put("mimeType", file.mimeType).put("data", file.data))
        } }
        val body = JSONObject().put("model", model).put("messages", messages).put("attachments", files)
            .toString().toByteArray(Charsets.UTF_8)
        require(body.size <= 3 * 1024 * 1024) { "Conversation too large. New chat shuru karein." }
        Request.Builder().url(URL(base, "api/chat"))
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            // Cancels the socket even while waiting for headers or reading the body.
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    continuation.resumeWith(Result.failure(error))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use { readReply(it) } }
                    continuation.resumeWith(result)
                }
            })
        }
    }

    private fun readReply(response: Response): String {
        val status = response.code
        val contentType = response.header("Content-Type").orEmpty()
        if (!contentType.contains("application/json", ignoreCase = true)) {
            throw DuaApiException("Dua backend available nahi hai. Hosting access settings check karein.")
        }
        val stream = response.body?.byteStream()
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
        return result.optString("text").takeIf { it.isNotBlank() }
            ?: throw DuaApiException("Gemini ne text response nahi diya. Dobara try karein.")
    }

    private companion object {
        val sharedClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

class DuaApiException(message: String) : Exception(message)
