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

/** Generated audio and its type (MP3 for Grok/ElevenLabs, WAV for Gemini). */
class GeneratedAudio(val bytes: ByteArray, val mimeType: String)

/** Sends one explicitly selected reply (or one chunk of it) to the application's authenticated backend. */
class GeneratedAudioApi(
    private val baseUrl: String = BuildConfig.DUA_API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
) {
    suspend fun generate(text: String, token: String): ByteArray = request(GeneratedVoiceText.validate(text, true), token, null).bytes

    /**
     * Phase 4 backends: one chunk with an explicit provider, voice, language and speed. [maxCharacters] is the
     * provider's per-request limit from `/api/config`.
     */
    suspend fun synthesize(text: String, token: String, options: TtsOptions, maxCharacters: Int): GeneratedAudio {
        val selected = text.trim()
        require(selected.isNotEmpty() && selected.length <= maxCharacters) { "Voice chunk is empty or too long." }
        return request(selected, token, options)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun request(selected: String, token: String, options: TtsOptions?): GeneratedAudio {
        require(token.matches(Regex("[A-Za-z0-9._-]{1,10000}"))) { "Sign in before using AI voice." }
        val base = URL(baseUrl.trimEnd('/') + "/")
        require(base.protocol == "https" && base.host.isNotBlank() && base.userInfo == null) { "AI voice requires an HTTPS backend." }
        val request = Request.Builder().url(URL(base, "api/voice/audio"))
            .header("Accept", if (options == null) "audio/mpeg" else "audio/mpeg, audio/wav").header("x-dua-native", "1").header("Authorization", "Bearer $token")
            .post(VoiceCodec.requestBody(selected, options).toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    continuation.resumeWith(Result.failure(IOException("AI voice request failed. Try device voice.", error)))
                }
                override fun onResponse(call: Call, response: Response) {
                    var bytes: GeneratedAudio? = null
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw AccountException(it.code, when (it.code) {
                                401 -> "Sign in again before using AI voice."
                                403 -> "AI voice is restricted to the configured personal account."
                                429 -> "Voice request limit reached. Try device voice or retry later."
                                400 -> "This voice could not read the reply. Try another voice or device voice."
                                503 -> "AI voice setup is not enabled. Device voice remains available."
                                else -> "AI voice is unavailable. Try device voice."
                            })
                            val type = it.header("Content-Type").orEmpty().trim()
                            val mimeType = when {
                                Regex("audio/(mpeg|mp3)(?:;.*)?", RegexOption.IGNORE_CASE).matches(type) -> "audio/mpeg"
                                options != null && Regex("audio/(wav|x-wav|wave)(?:;.*)?", RegexOption.IGNORE_CASE).matches(type) -> "audio/wav"
                                else -> throw IllegalArgumentException("Invalid AI voice response. Try device voice.")
                            }
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
                            GeneratedAudio(buffer.toByteArray(), mimeType).also { bytes = it }
                        }
                    }
                    if (!continuation.isActive) bytes?.bytes?.fill(0)
                    result.fold(
                        onSuccess = { value -> continuation.resume(value, onCancellation = { value.bytes.fill(0) }) },
                        onFailure = { continuation.resumeWith(Result.failure(it)) }
                    )
                }
            })
        }
    }
    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}
