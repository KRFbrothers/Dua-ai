package com.dua.ai.network

import com.dua.ai.BuildConfig
import com.dua.ai.model.ChatMessage
import com.dua.ai.attachments.ChatAttachment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

/** Provider credentials and model names remain on the server. */
class DuaApiClient(
    private val baseUrl: String = BuildConfig.DUA_API_BASE_URL,
    private val client: OkHttpClient = sharedClient
) {
    /** Legacy single-JSON reply (kept for older flows and tests). Chat uses [streamReply]. */
    suspend fun reply(model: String, history: List<ChatMessage>, attachments: List<ChatAttachment> = emptyList(), memory: String = "", token: String? = null, web: String? = null): String {
        val request = withContext(Dispatchers.IO) { buildRequest(model, history, attachments, memory, token, web, reasoning = false, stream = false) }
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

    /**
     * Streams a reply as [ReplyEvent]s. Cancelling collection cancels the HTTP call. The flow fails
     * with [DuaApiException] for server errors (before or during the stream) and with an
     * [IOException] when the connection drops; events already emitted stay valid (partial reply).
     */
    fun streamReply(model: String, history: List<ChatMessage>, attachments: List<ChatAttachment> = emptyList(), memory: String = "",
        token: String? = null, web: String? = null, reasoning: Boolean = false, historyBytes: Int = MAX_HISTORY_BYTES,
        context: com.dua.ai.model.ConversationContext? = null, xSearch: Boolean = false, code: Boolean = false, build: String? = null): Flow<ReplyEvent> = callbackFlow {
        val request = withContext(Dispatchers.IO) { buildRequest(model, history, attachments, memory, token, web, reasoning, stream = true, historyBytes = historyBytes, context = context, xSearch = xSearch, code = code, build = build) }
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { close(error) }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { readStream(it) { event -> trySendBlocking(event).getOrThrow() } }
                    close()
                } catch (error: Throwable) { close(error) }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun readStream(response: Response, emit: (ReplyEvent) -> Unit) {
        val contentType = response.header("Content-Type").orEmpty()
        if (!contentType.contains("text/event-stream", ignoreCase = true)) {
            // Errors before the stream starts (validation, auth, quota) keep the JSON format.
            val text = readReply(response)
            emit(ReplyEvent.Text(text))
            emit(ReplyEvent.Done(false))
            return
        }
        if (response.code !in 200..299) throw DuaApiException("Reply nahi aa saka. Dobara try karein.", response.code)
        val source = response.body?.source() ?: throw DuaApiException("Dua server se valid response nahi mila.")
        val parser = ChatStreamParser()
        var bytes = 0L
        while (true) {
            val line = source.readUtf8Line() ?: break
            bytes += line.length + 1
            if (bytes > MAX_STREAM_BYTES) throw DuaApiException("Dua response bahut bada hai. Partial reply kept.")
            when (val event = parser.line(line)) {
                null -> Unit
                is ReplyEvent.Failed -> throw DuaApiException(event.message, event.status)
                is ReplyEvent.Done -> { emit(event); return }
                else -> emit(event)
            }
        }
        when (val event = parser.finish()) {
            is ReplyEvent.Done -> { emit(event); return }
            is ReplyEvent.Failed -> throw DuaApiException(event.message, event.status)
            null -> Unit
            else -> emit(event)
        }
        throw IOException("Reply stream ended early.")
    }

    /**
     * [context] (Phase 2 servers only) means [history] is already the long-context window chosen by
     * [com.dua.ai.model.ContextWindow]: it is sent as is (up to 400 turns) with the chat id and cached summary.
     */
    internal fun buildRequest(model: String, history: List<ChatMessage>, attachments: List<ChatAttachment>, memory: String, token: String?, web: String?,
        reasoning: Boolean, stream: Boolean, historyBytes: Int = MAX_HISTORY_BYTES, context: com.dua.ai.model.ConversationContext? = null, xSearch: Boolean = false,
        code: Boolean = false, build: String? = null): Request {
        require(build == null || com.dua.ai.model.BuildInfos.validRepository(build)) { "Choose a repository for Build mode." }
        require(model in com.dua.ai.setup.IntegrationCapabilities.MODELS) { "Supported model select karein." }
        require(memory.length <= 3000) { "Memory notes are too large." }
        require(web == null || web == "search" || web == "page") { "Web mode must be search or page." }
        val base = URL(baseUrl.trimEnd('/') + "/")
        require(base.protocol == "https") { "Dua backend ke liye HTTPS URL set karein." }
        val longContext = stream && context != null
        val messages = historyJson(history, includeFileText = stream, maxBytes = historyBytes, maxTurns = if (longContext) com.dua.ai.model.ContextWindow.MAX_TURNS else LEGACY_TURNS)
        val files = JSONArray().apply { attachments.forEach { file ->
            put(JSONObject().put("name", file.name).put("mimeType", file.mimeType).put("data", file.data))
        } }
        val body = JSONObject().put("model", model).put("messages", messages).put("attachments", files).put("memory", memory)
            .apply {
                if (web != null) put("web", web)
                // Older servers reject unknown fields, so these are only sent by the streaming path.
                // Build mode (Phase 5B, read-only) replaces Auto/Reasoning and names the repository.
                if (stream) put("stream", true).put("mode", if (build != null) "build" else if (reasoning) "reasoning" else "auto")
                if (stream && build != null) put("repository", build)
                if (longContext) put("context", context!!.toJson())
                if (stream && xSearch && model == "Grok") put("xSearch", true)
                // Run code (Phase 5): only sent when the server advertised it, so older servers never see the field.
                if (stream && code && build == null) put("code", true)
            }
            .toString().toByteArray(Charsets.UTF_8)
        require(body.size <= (if (historyBytes > MAX_HISTORY_BYTES || longContext) 4 else 3) * 1024 * 1024) { "Conversation too large. New chat shuru karein." }
        val builder = Request.Builder().url(URL(base, "api/chat"))
            .header("Accept", if (stream) "text/event-stream, application/json" else "application/json")
            .header("x-dua-native", "1")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (token != null) {
            require(token.length <= 10000 && token.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid account session." }
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
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
                ?: "Reply nahi aa saka. Dobara try karein.", status
        )
        return result.optString("text").takeIf { it.isNotBlank() }
            ?: throw DuaApiException("Gemini ne text response nahi diya. Dobara try karein.")
    }

    companion object {
        const val MAX_STREAM_BYTES = 4L * 1024 * 1024
        /** Budget for earlier turns' stored text-file content re-sent with follow-ups. */
        const val FILE_CONTEXT_CHARS = 96_000
        const val MAX_HISTORY_BYTES = 262144
        /** Servers with stored files (Phase 2) accept 1 MB of history (file references and transcripts). */
        const val FILES_HISTORY_BYTES = 1024 * 1024
        /** Long-context servers accept 2.5 MB of history; 2 MB leaves room for the request, 1 MB when inline files ride along. */
        const val LONG_HISTORY_BYTES = 2 * 1024 * 1024
        const val LONG_HISTORY_BYTES_WITH_FILES = 1024 * 1024
        /** Turns older servers accept (and older apps sent). */
        const val LEGACY_TURNS = 39
        /** Server limit for one earlier model turn. */
        private const val MAX_MODEL_TURN_CHARS = 80_000

        /**
         * Completed turns (last 39) as request JSON. With [includeFileText], earlier user turns carry
         * their stored text-file content, newest first within [FILE_CONTEXT_CHARS], dropping the oldest
         * file text if the server's 256 KB history limit would be exceeded.
         */
        internal fun historyJson(history: List<ChatMessage>, includeFileText: Boolean, maxBytes: Int = MAX_HISTORY_BYTES, maxTurns: Int = LEGACY_TURNS): JSONArray {
            // Stopped (partial) replies are not sent back as context, same as before streaming.
            val turns = history.filter { it.state == "complete" }.takeLast(maxTurns)
            val withText = BooleanArray(turns.size)
            if (includeFileText) {
                var budget = FILE_CONTEXT_CHARS
                for (index in turns.indices.reversed()) {
                    val text = turns[index].attachmentText
                    if (index == turns.lastIndex || !turns[index].isUser || text.isNullOrEmpty() || text.length > budget) continue
                    budget -= text.length
                    withText[index] = true
                }
            }
            fun encode() = JSONArray().apply { turns.forEachIndexed { index, message ->
                put(JSONObject().put("role", if (message.isUser) "user" else "model").put("text", if (message.isUser) message.text else message.text.take(MAX_MODEL_TURN_CHARS))
                    .apply {
                        if (withText[index]) put("attachmentText", message.attachmentText)
                        // Stored files are re-attached by the server from these references (stream path = Phase 2 servers).
                        if (includeFileText && message.isUser && message.files.isNotEmpty()) put("files", com.dua.ai.files.FileRefCodec.array(message.files))
                    })
            } }
            var messages = encode()
            while (messages.toString().toByteArray(Charsets.UTF_8).size > maxBytes) {
                val oldest = withText.indexOfFirst { it }
                require(oldest >= 0) { "Conversation too large. New chat shuru karein." }
                withText[oldest] = false
                messages = encode()
            }
            return messages
        }

        /** Upper bound of one turn's request JSON (with its file text and file references), for window selection. */
        fun turnJsonBytes(message: ChatMessage): Int = JSONObject().put("role", if (message.isUser) "user" else "model")
            .put("text", if (message.isUser) message.text else message.text.take(MAX_MODEL_TURN_CHARS))
            .apply {
                if (message.isUser && !message.attachmentText.isNullOrEmpty()) put("attachmentText", message.attachmentText)
                if (message.isUser && message.files.isNotEmpty()) put("files", com.dua.ai.files.FileRefCodec.array(message.files))
            }.toString().toByteArray(Charsets.UTF_8).size

        // readTimeout is the longest gap between bytes: the server sends a heartbeat every 15 s while
        // a reply streams, so a stalled connection fails in ~75 s while long replies can run for the
        // server's whole deadline (DUA_CHAT_DEADLINE_SECONDS, default 280 s). callTimeout is a backstop.
        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(75, TimeUnit.SECONDS)
            .callTimeout(330, TimeUnit.SECONDS)
            .build()
    }
}

class DuaApiException(message: String, val status: Int = 0) : Exception(message)
