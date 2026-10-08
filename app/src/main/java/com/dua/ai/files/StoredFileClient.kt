package com.dua.ai.files

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/** Response of POST /api/files/sign. */
data class SignedUpload(val path: String, val uploadUrl: String, val headers: Map<String, String>, val mimeType: String, val maxBytes: Long) {
    companion object {
        fun parse(json: JSONObject): SignedUpload {
            val path = json.getString("path")
            require(StoredFileTypes.PATH.matches(path)) { "Server returned an invalid file path." }
            val url = json.getString("uploadUrl")
            val parsed = URI(url)
            require(parsed.scheme == "https" && !parsed.host.isNullOrBlank() && parsed.userInfo == null) { "Server returned an invalid upload URL." }
            val headers = json.optJSONObject("headers")?.let { object_ -> object_.keys().asSequence()
                .filter { it in ALLOWED_HEADERS }.associateWith { object_.optString(it) }.filterValues { it.isNotBlank() && it.length <= 4000 } } ?: emptyMap()
            return SignedUpload(path, url, headers, json.optString("mimeType"), json.optLong("maxBytes"))
        }
        private val ALLOWED_HEADERS = setOf("Content-Type", "x-upsert", "apikey")
    }
}

/**
 * Streams a file straight to Supabase Storage with the signed upload URL (no Vercel body limit),
 * reporting progress. Cancelling the coroutine cancels the HTTP call.
 */
class StoredFileClient(private val client: OkHttpClient = uploadClient) {
    suspend fun upload(signed: SignedUpload, size: Long, open: () -> InputStream, onProgress: (Long) -> Unit) {
        val type = (signed.headers["Content-Type"] ?: signed.mimeType).toMediaType()
        val body = ProgressBody(type, size, open, onProgress)
        val builder = Request.Builder().url(signed.uploadUrl).put(body)
        signed.headers.forEach { (name, value) -> if (name != "Content-Type") builder.header(name, value) }
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { continuation.resumeWith(Result.failure(e)) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) {
                                val text = it.body?.source()?.let { source -> source.request(8192); source.buffer.snapshot().utf8().take(8192) }.orEmpty().lowercase()
                                throw StoredFileException(when {
                                    it.code == 413 || "too large" in text || "maximum allowed size" in text -> "File is larger than the storage limit."
                                    "mime type" in text -> "Storage does not accept this file type."
                                    "already exists" in text || it.code == 409 -> "Upload conflict. Try again."
                                    "expired" in text || it.code == 401 || it.code == 403 -> "Upload link expired or was refused. Try again."
                                    else -> "Upload failed (HTTP ${it.code}). Try again."
                                })
                            }
                        }
                    }
                    continuation.resumeWith(result)
                }
            })
        }
    }

    private class ProgressBody(private val type: MediaType, private val size: Long, private val open: () -> InputStream, private val onProgress: (Long) -> Unit) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = size
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) {
            var sent = 0L
            var reported = 0L
            open().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    sent += count
                    if (sent > size) throw IOException("File changed while uploading.")
                    sink.write(buffer, 0, count)
                    if (sent - reported >= 128 * 1024 || sent == size) { reported = sent; onProgress(sent) }
                }
            }
            if (sent != size) throw IOException("File changed while uploading.")
        }
    }

    companion object {
        val uploadClient: OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

class StoredFileException(message: String) : Exception(message)
