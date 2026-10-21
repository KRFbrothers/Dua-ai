package com.dua.ai.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** One owner document indexed by the engine (GET /api/agent/documents). */
data class AgentDocument(val id: String, val name: String, val kind: String)

/** Outcome of a Files call: documents disabled on the server is its own state, not an error. */
sealed interface AgentDocumentsResult<out T> {
    data class Ok<T>(val value: T) : AgentDocumentsResult<T>
    data object Disabled : AgentDocumentsResult<Nothing>
    data class Failed(val message: String) : AgentDocumentsResult<Nothing>
}

/**
 * Owner-scoped document RAG on the Cloud Run engine: `/api/agent/documents`.
 * GET lists, POST `{name,text}` indexes extracted text (max [MAX_DOCUMENT_CHARS]), DELETE `{id}` removes.
 * Uses the same base URL, X-Dua-App-Token and owner Bearer session as [DuaApiClient].
 */
class AgentDocumentsApi(
    private val baseUrl: String = com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL,
    private val appToken: String = com.dua.ai.BuildConfig.DUA_APP_TOKEN,
    private val client: OkHttpClient = DuaApiClient.sharedClient,
    private val allowHttp: Boolean = false,
) {
    suspend fun list(token: String?): AgentDocumentsResult<List<AgentDocument>> =
        call("GET", null, token) { parseList(it) }

    suspend fun add(name: String, text: String, token: String?): AgentDocumentsResult<Unit> =
        call("POST", JSONObject().put("name", name).put("text", text), token) { }

    suspend fun delete(id: String, token: String?): AgentDocumentsResult<Boolean> =
        call("DELETE", JSONObject().put("id", id), token) { JSONObject(it).optBoolean("deleted", false) }

    internal fun buildRequest(method: String, body: JSONObject?, token: String?): Request? {
        val base = runCatching { URL(baseUrl.trimEnd('/') + "/") }.getOrNull() ?: return null
        if (base.protocol != "https" && !(allowHttp && base.protocol == "http")) return null
        val payload = body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder().url(URL(base, "api/agent/documents")).method(method, payload)
            .header("Accept", "application/json").header("x-dua-native", "1")
        if (appToken.isNotBlank()) builder.header("X-Dua-App-Token", appToken)
        if (token != null && token.length <= 10000 && token.matches(Regex("[A-Za-z0-9._-]+"))) builder.header("Authorization", "Bearer $token")
        return builder.build()
    }

    private suspend fun <T> call(method: String, body: JSONObject?, token: String?, parse: (String) -> T): AgentDocumentsResult<T> =
        withContext(Dispatchers.IO) {
            val request = buildRequest(method, body, token) ?: return@withContext AgentDocumentsResult.Failed("Files need the Google Cloud engine.")
            runCatching {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.source()?.let { s -> s.request(MAX_RESPONSE_BYTES + 1L); if (s.buffer.size > MAX_RESPONSE_BYTES) null else s.readUtf8() }
                    classify(response.code, text, parse)
                }
            }.getOrElse { AgentDocumentsResult.Failed("Couldn't reach Dua. Check your connection and try again.") }
        }

    companion object {
        /** Same limit as the engine's agent_documents.MAX_DOCUMENT_CHARS. */
        const val MAX_DOCUMENT_CHARS = 100_000
        const val MAX_NAME_CHARS = 200
        /** Raw file bytes read before decoding; UTF-8 text can use up to 4 bytes per character. */
        const val MAX_FILE_BYTES = MAX_DOCUMENT_CHARS * 4
        private const val MAX_RESPONSE_BYTES = 1_000_000L
        /** Text-like types the picker offers; PDFs are not supported (no on-device PDF text extractor). */
        val PICKER_TYPES = arrayOf("text/*", "application/json", "application/xml", "application/x-yaml")

        internal fun <T> classify(code: Int, body: String?, parse: (String) -> T): AgentDocumentsResult<T> {
            val error = body?.let { runCatching { JSONObject(it).optString("error") }.getOrNull() }.orEmpty()
            return when {
                code in 200..299 && body != null -> runCatching { AgentDocumentsResult.Ok(parse(body)) }
                    .getOrElse { AgentDocumentsResult.Failed("Dua sent an unexpected answer.") }
                code == 503 && error.contains("not configured", ignoreCase = true) -> AgentDocumentsResult.Disabled
                code == 401 || code == 403 -> AgentDocumentsResult.Failed("Sign in from Profile to use Files.")
                code == 400 -> AgentDocumentsResult.Failed("Dua couldn't read that file. Try a smaller plain-text file.")
                else -> AgentDocumentsResult.Failed("Files are unavailable right now. Try again later.")
            }
        }

        internal fun parseList(body: String): List<AgentDocument> {
            val rows = JSONObject(body).getJSONArray("documents")
            return (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val id = row.optString("id"); if (id.isBlank()) return@mapNotNull null
                AgentDocument(id, row.optString("name", "Untitled"), row.optString("kind", "document"))
            }.filter { it.kind == "document" }
        }

        /** Decodes strict UTF-8 text and checks the engine limits; returns the text or a friendly error. */
        internal fun extractText(bytes: ByteArray): Result<String> {
            if (bytes.size > MAX_FILE_BYTES) return Result.failure(IllegalArgumentException("That file is too big. Files can have up to 100,000 characters."))
            val text = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            } catch (_: CharacterCodingException) {
                return Result.failure(IllegalArgumentException("Only text files work for now (TXT, MD, CSV, JSON). PDFs aren't supported yet."))
            }
            if (text.contains('\u0000')) return Result.failure(IllegalArgumentException("Only text files work for now (TXT, MD, CSV, JSON). PDFs aren't supported yet."))
            if (text.isBlank()) return Result.failure(IllegalArgumentException("That file is empty."))
            if (text.length > MAX_DOCUMENT_CHARS) return Result.failure(IllegalArgumentException("That file is too big. Files can have up to 100,000 characters."))
            return Result.success(text)
        }

        internal fun documentName(display: String?): String =
            display?.trim()?.replace(Regex("[\\r\\n\\t]"), " ")?.take(MAX_NAME_CHARS)?.ifBlank { null } ?: "Untitled file"
    }
}
