package com.dua.ai.voice

import com.dua.ai.BuildConfig
import com.dua.ai.account.AccountException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

/** Sends one explicitly selected reply to the application's authenticated backend. */
class GeneratedAudioApi(
    private val baseUrl: String = BuildConfig.DUA_API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun generate(text: String, token: String): ByteArray {
        val selected = GeneratedVoiceText.validate(text, true)
        require(token.matches(Regex("[A-Za-z0-9._-]{1,10000}"))) { "Sign in before using AI voice." }
        val base = URL(baseUrl.trimEnd('/') + "/")
        require(base.protocol == "https" && base.host.isNotBlank() && base.userInfo == null) { "AI voice requires an HTTPS backend." }
        val request = Request.Builder().url(URL(base, "api/voice/audio"))
            .header("Accept", "audio/mpeg").header("x-dua-native", "1").header("Authorization", "Bearer $token")
            .post(JSONObject().put("text", selected).toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    continuation.resumeWith(Result.failure(IOException("AI voice request failed. Try device voice.", error)))
                }
                override fun onResponse(call: Call, response: Response) {
                    var bytes: ByteArray? = null
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw AccountException(it.code, when (it.code) {
                                401 -> "Sign in again before using AI voice."
                                403 -> "AI voice is restricted to the configured personal account."
                                429 -> "Voice request limit reached. Try device voice or retry later."
                                503 -> "AI voice setup is not enabled. Device voice remains available."
                                else -> "AI voice is unavailable. Try device voice."
                            })
                            require(Regex("audio/(mpeg|mp3)(?:;.*)?", RegexOption.IGNORE_CASE).matches(it.header("Content-Type").orEmpty().trim())) { "Invalid AI voice response. Try device voice." }
                            val body = it.body ?: error("Empty AI voice response.")
                            require(body.contentLength() <= MAX_BYTES) { "AI voice response exceeds 8 MiB." }
                            val buffer = ByteArrayOutputStream()
                            body.byteStream().use { stream ->
                                val chunk = ByteArray(8192)
                                while (true) {
                                    if (call.isCanceled()) throw IOException("Voice request cancelled.")
                                    val count = stream.read(chunk)
                                    if (count < 0) break
                                    require(buffer.size() + count <= MAX_BYTES) { "AI voice response exceeds 8 MiB." }
                                    buffer.write(chunk, 0, count)
                                }
                            }
                            require(buffer.size() > 0) { "Empty AI voice response." }
                            buffer.toByteArray().also { bytes = it }
                        }
                    }
                    if (!continuation.isActive) bytes?.fill(0)
                    result.fold(
                        onSuccess = { value -> continuation.resume(value, onCancellation = { value.fill(0) }) },
                        onFailure = { continuation.resumeWith(Result.failure(it)) }
                    )
                }
            })
        }
    }
    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}
