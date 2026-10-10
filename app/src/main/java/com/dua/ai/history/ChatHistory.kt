package com.dua.ai.history

import com.dua.ai.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

data class SavedChat(
    val id: String,
    val model: String,
    val draft: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long,
    /** Server summary of older turns (long context). Included in the signed-in cloud snapshot. */
    val summary: com.dua.ai.model.ChatSummary? = null,
    /** Build mode repository this chat is tied to (owner/name). Included in the signed-in cloud snapshot. */
    val buildRepository: String? = null
) {
    val title: String get() = messages.firstOrNull { it.isUser }?.text?.take(60) ?: "Draft"
    val preview: String get() = draft.takeIf { it.isNotBlank() }?.let { "Draft: $it" }
        ?: messages.lastOrNull { it.text.isNotBlank() }?.text.orEmpty()
}

object ChatHistoryCodec {
    const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

    fun readImport(input: InputStream): List<SavedChat> {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_IMPORT_BYTES) { "Choose a Dua JSON export up to 4 MiB." }
            output.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val raw = try { decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString() }
            catch (_: java.nio.charset.CharacterCodingException) { error("This file is not valid UTF-8 JSON.") }
        return parseImport(raw)
    }

    fun parseImport(raw: String, includeServerFields: Boolean = false): List<SavedChat> {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_IMPORT_BYTES) { "Choose a Dua JSON export up to 4 MiB." }
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in raw) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else if (char == '"') quoted = true
            else if (char == '{' || char == '[') { depth++; require(depth <= 64) { "Export is too deeply nested. No chats were changed." } }
            else if (char == '}' || char == ']') depth--
        }
        val invalid = "Unsupported or invalid Dua export. No chats were changed."
        try {
            // JSONObject accepts trailing text; reject it rather than partially importing a file.
            val tokener = org.json.JSONTokener(raw.removePrefix("\uFEFF"))
            val root = tokener.nextValue() as? JSONObject ?: error(invalid)
            require(tokener.nextClean() == '\u0000') { invalid }
            fun integer(item: JSONObject, key: String): Long {
                val value = item.get(key)
                require(value is Number && value.toDouble().isFinite() && value.toDouble() >= 0 && value.toDouble() <= 9007199254740991.0 && value.toDouble() == value.toLong().toDouble()) { invalid }
                return value.toLong()
            }
            fun text(item: JSONObject, key: String, limit: Int): String {
                val value = item.get(key)
                require(value is String && value.length <= limit) { invalid }
                return value
            }
            require(root.get("format") == "dua-chat-export" && root.get("version") is Number && integer(root, "version") == 1L) { invalid }
            integer(root, "exportedAt")
            val records = root.getJSONArray("chats")
            require(records.length() <= 100) { invalid }
            val ids = mutableSetOf<String>()
            return (0 until records.length()).map { index ->
                val chat = records.getJSONObject(index)
                val id = text(chat, "id", 200)
                require(id.isNotBlank() && ids.add(id)) { invalid }
                val model = text(chat, "model", 20)
                require(model in com.dua.ai.setup.IntegrationCapabilities.MODELS) { invalid }
                val draft = text(chat, "draft", 8000)
                val updatedAt = integer(chat, "updatedAt")
                val messages = chat.getJSONArray("messages")
                require(messages.length() <= 500) { invalid }
                SavedChat(id, model, draft, (0 until messages.length()).map { i ->
                    val message = messages.getJSONObject(i)
                    val role = text(message, "role", 10)
                    val state = text(message, "state", 10)
                    require(role in listOf("user", "model") && state in listOf("complete", "stopped")) { invalid }
                    // Optional since Reasoning mode; exports and cloud snapshots without them stay valid.
                    val thinking = if (role == "model" && message.has("thinking")) text(message, "thinking", 100000) else null
                    val seconds = if (role == "model" && message.has("thinkingSeconds")) integer(message, "thinkingSeconds").also { require(it <= 86400) { invalid } }.toInt() else null
                    // Optional since Imagine (Phase 3): stored references to generated images and videos.
                    val media = if (role == "model" && message.has("media")) com.dua.ai.imagine.ImagineCodec.mediaFrom(message.getJSONArray("media"), local = false) else emptyList()
                    ChatMessage(id = if (includeServerFields) message.optString("id").takeIf { it.isNotBlank() && it.length <= 100 } ?: UUID.randomUUID().toString() else UUID.randomUUID().toString(),
                        text = text(message, "text", 1048576), isUser = role == "user", model = model, state = state,
                        thinking = thinking?.takeIf { it.isNotBlank() }, thinkingSeconds = seconds, media = media,
                        attachmentText = if (includeServerFields && role == "user") message.optString("attachmentText").takeIf { it.isNotEmpty() }?.take(ChatMessage.MAX_ATTACHMENT_TEXT_CHARS) else null,
                        files = if (includeServerFields && role == "user") com.dua.ai.files.FileRefCodec.fromArray(message.optJSONArray("files")) else emptyList(),
                        imagine = if (includeServerFields && role == "model") com.dua.ai.imagine.ImagineCodec.infoFrom(message.optJSONObject("imagine")) else null,
                        codeRuns = if (includeServerFields && role == "model") com.dua.ai.model.CodeRuns.fromJson(message.optJSONArray("codeRuns")) else emptyList(),
                        build = if (includeServerFields && role == "model") com.dua.ai.model.BuildInfos.fromJson(message.optJSONObject("build")) else null,
                        proposal = if (includeServerFields && role == "model") com.dua.ai.model.BuildProposals.fromJson(message.optJSONObject("proposal")) else null)
                }, updatedAt,
                    if (includeServerFields) com.dua.ai.model.ChatSummary.fromJson(chat.optJSONObject("summary")) else null,
                    if (includeServerFields) chat.optString("buildRepository").takeIf { com.dua.ai.model.BuildInfos.validRepository(it) } else null)
            }
        } catch (_: Exception) { throw IllegalArgumentException(invalid) }
    }

    fun importCopies(existing: List<SavedChat>, imported: List<SavedChat>): List<SavedChat> {
        val used = existing.map { it.id }.toMutableSet()
        return existing + imported.map { chat ->
            var id: String
            do { id = UUID.randomUUID().toString() } while (!used.add(id))
            chat.copy(id = id, messages = chat.messages.map { it.copy(id = UUID.randomUUID().toString()) }, summary = null)
        }
    }

    fun export(chats: List<SavedChat>, exportedAt: Long = System.currentTimeMillis(), includeServerFields: Boolean = false): String = JSONObject()
        .put("format", "dua-chat-export").put("version", 1).put("exportedAt", exportedAt)
        .put("chats", JSONArray().apply {
            chats.sortedByDescending { it.updatedAt }.forEach { chat ->
                put(JSONObject().put("id", chat.id).put("model", chat.model).put("draft", chat.draft)
                    .apply { if (includeServerFields) { chat.summary?.let { put("summary", it.toJson()) }; chat.buildRepository?.let { put("buildRepository", it) } } }
                    .put("updatedAt", chat.updatedAt).put("messages", JSONArray().apply {
                        chat.messages.filter(::persistable).forEach { message -> put(JSONObject()
                            .put("role", if (message.isUser) "user" else "model")
                            .put("text", message.text).put("state", savedState(message.state))
                            .apply {
                                if (includeServerFields) put("id", message.id)
                                putThinking(this, message); putMedia(this, message, local = false)
                                if (includeServerFields && message.isUser) {
                                    message.attachmentText?.takeIf { it.isNotEmpty() }?.let { put("attachmentText", it.take(ChatMessage.MAX_ATTACHMENT_TEXT_CHARS)) }
                                    if (message.files.isNotEmpty()) put("files", com.dua.ai.files.FileRefCodec.array(message.files))
                                }
                                if (includeServerFields && !message.isUser) {
                                    message.imagine?.let { put("imagine", com.dua.ai.imagine.ImagineCodec.infoJson(it)) }
                                    if (message.codeRuns.isNotEmpty()) put("codeRuns", com.dua.ai.model.CodeRuns.toJson(message.codeRuns))
                                    message.build?.let { put("build", com.dua.ai.model.BuildInfos.toJson(it)) }
                                    message.proposal?.let { put("proposal", com.dua.ai.model.BuildProposals.toJson(it)) }
                                }
                            }) }
                    }))
            }
        }).toString(2)

    fun encode(chats: List<SavedChat>, activeId: String): String = JSONObject()
        .put("activeId", activeId).put("chats", JSONArray().apply {
            chats.forEach { chat -> put(JSONObject().put("id", chat.id).put("model", chat.model)
                .put("draft", chat.draft).put("updatedAt", chat.updatedAt)
                .apply { chat.summary?.let { put("summary", it.toJson()) }; chat.buildRepository?.let { put("buildRepository", it) } }
                .put("messages", JSONArray().apply { chat.messages.filter(::persistable).forEach { message ->
                    put(JSONObject().put("id", message.id).put("text", message.text).put("isUser", message.isUser)
                        .put("model", message.model).put("state", savedState(message.state))
                        .apply {
                            putThinking(this, message)
                            // Text-file content stays local: it is not exported or synced to the cloud.
                            if (message.isUser && !message.attachmentText.isNullOrEmpty()) put("attachmentText", message.attachmentText.take(ChatMessage.MAX_ATTACHMENT_TEXT_CHARS))
                            // Stored-file references (and audio transcripts) also stay local.
                            if (message.isUser && message.files.isNotEmpty()) put("files", com.dua.ai.files.FileRefCodec.array(message.files))
                            putMedia(this, message, local = true)
                            if (!message.isUser) message.imagine?.let { put("imagine", com.dua.ai.imagine.ImagineCodec.infoJson(it)) }
                            // Code that ran for a reply (Run code) stays local like file references: not exported or synced.
                            if (!message.isUser && message.codeRuns.isNotEmpty()) put("codeRuns", com.dua.ai.model.CodeRuns.toJson(message.codeRuns))
                            // Build mode file lists stay local too (repository paths can be private).
                            if (!message.isUser) message.build?.let { put("build", com.dua.ai.model.BuildInfos.toJson(it)) }
                            // Build proposals and their pull request links stay local as well (Phase 5C).
                            if (!message.isUser) message.proposal?.let { put("proposal", com.dua.ai.model.BuildProposals.toJson(it)) }
                        })
                } })) }
        }).toString()

    /** An empty reply placeholder that is still streaming is not saved. */
    private fun persistable(message: ChatMessage) = !(message.state == ChatMessage.STREAMING && message.text.isBlank())

    /** A reply still streaming when the app saves is restored as a stopped partial reply. */
    private fun savedState(state: String) = if (state == ChatMessage.STREAMING) "stopped" else state

    private fun putThinking(json: JSONObject, message: ChatMessage) {
        if (message.isUser) return
        message.thinking?.takeIf { it.isNotBlank() }?.let { json.put("thinking", it.take(ChatMessage.MAX_THINKING_CHARS)) }
        message.thinkingSeconds?.takeIf { it in 0..86400 }?.let { json.put("thinkingSeconds", it) }
    }

    private fun putMedia(json: JSONObject, message: ChatMessage, local: Boolean) {
        if (message.isUser || message.media.isEmpty()) return
        val array = com.dua.ai.imagine.ImagineCodec.mediaArray(message.media, local)
        if (array.length() > 0) json.put("media", array)
    }

    fun decode(raw: String): Pair<String, List<SavedChat>> {
        val root = JSONObject(raw)
        val records = root.getJSONArray("chats")
        val chats = (0 until records.length()).map { index ->
            val item = records.getJSONObject(index)
            val messages = item.getJSONArray("messages")
            SavedChat(item.getString("id"), item.getString("model"), item.optString("draft"),
                (0 until messages.length()).map { i -> val message = messages.getJSONObject(i)
                    ChatMessage(id = message.getString("id"), text = message.getString("text"),
                        isUser = message.getBoolean("isUser"), model = message.getString("model"),
                        state = message.optString("state", "complete").let { if (it == ChatMessage.STREAMING) "stopped" else it },
                        thinking = if (message.has("thinking")) message.optString("thinking").takeIf { it.isNotBlank() } else null,
                        thinkingSeconds = if (message.has("thinkingSeconds")) message.optInt("thinkingSeconds", -1).takeIf { it in 0..86400 } else null,
                        attachmentText = if (message.has("attachmentText")) message.optString("attachmentText").takeIf { it.isNotEmpty() } else null,
                        files = com.dua.ai.files.FileRefCodec.fromArray(message.optJSONArray("files")),
                        media = com.dua.ai.imagine.ImagineCodec.mediaFrom(message.optJSONArray("media"), local = true),
                        imagine = com.dua.ai.imagine.ImagineCodec.infoFrom(message.optJSONObject("imagine")),
                        codeRuns = if (message.optBoolean("isUser")) emptyList() else com.dua.ai.model.CodeRuns.fromJson(message.optJSONArray("codeRuns")),
                        build = if (message.optBoolean("isUser")) null else com.dua.ai.model.BuildInfos.fromJson(message.optJSONObject("build")),
                        proposal = if (message.optBoolean("isUser")) null else com.dua.ai.model.BuildProposals.fromJson(message.optJSONObject("proposal")))
                }, item.getLong("updatedAt"), com.dua.ai.model.ChatSummary.fromJson(item.optJSONObject("summary")),
                item.optString("buildRepository").takeIf { com.dua.ai.model.BuildInfos.validRepository(it) })
        }
        require(chats.all { it.model in com.dua.ai.setup.IntegrationCapabilities.MODELS } && chats.map { it.id }.distinct().size == chats.size)
        return root.getString("activeId") to chats
    }
}
