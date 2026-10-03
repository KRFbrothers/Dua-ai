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
    val updatedAt: Long
) {
    val title: String get() = messages.firstOrNull { it.isUser }?.text?.take(60) ?: "Draft"
    val preview: String get() = draft.takeIf { it.isNotBlank() }?.let { "Draft: $it" }
        ?: messages.lastOrNull()?.text.orEmpty()
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

    fun parseImport(raw: String): List<SavedChat> {
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
                require(model in listOf("Dua", "Gemini")) { invalid }
                val draft = text(chat, "draft", 8000)
                val updatedAt = integer(chat, "updatedAt")
                val messages = chat.getJSONArray("messages")
                require(messages.length() <= 500) { invalid }
                SavedChat(id, model, draft, (0 until messages.length()).map { i ->
                    val message = messages.getJSONObject(i)
                    val role = text(message, "role", 10)
                    val state = text(message, "state", 10)
                    require(role in listOf("user", "model") && state in listOf("complete", "stopped")) { invalid }
                    ChatMessage(text = text(message, "text", 1048576), isUser = role == "user", model = model, state = state)
                }, updatedAt)
            }
        } catch (_: Exception) { throw IllegalArgumentException(invalid) }
    }

    fun importCopies(existing: List<SavedChat>, imported: List<SavedChat>): List<SavedChat> {
        val used = existing.map { it.id }.toMutableSet()
        return existing + imported.map { chat ->
            var id: String
            do { id = UUID.randomUUID().toString() } while (!used.add(id))
            chat.copy(id = id, messages = chat.messages.map { it.copy(id = UUID.randomUUID().toString()) })
        }
    }

    fun export(chats: List<SavedChat>, exportedAt: Long = System.currentTimeMillis()): String = JSONObject()
        .put("format", "dua-chat-export").put("version", 1).put("exportedAt", exportedAt)
        .put("chats", JSONArray().apply {
            chats.sortedByDescending { it.updatedAt }.forEach { chat ->
                put(JSONObject().put("id", chat.id).put("model", chat.model).put("draft", chat.draft)
                    .put("updatedAt", chat.updatedAt).put("messages", JSONArray().apply {
                        chat.messages.forEach { message -> put(JSONObject()
                            .put("role", if (message.isUser) "user" else "model")
                            .put("text", message.text).put("state", message.state)) }
                    }))
            }
        }).toString(2)

    fun encode(chats: List<SavedChat>, activeId: String): String = JSONObject()
        .put("activeId", activeId).put("chats", JSONArray().apply {
            chats.forEach { chat -> put(JSONObject().put("id", chat.id).put("model", chat.model)
                .put("draft", chat.draft).put("updatedAt", chat.updatedAt)
                .put("messages", JSONArray().apply { chat.messages.forEach { message ->
                    put(JSONObject().put("id", message.id).put("text", message.text).put("isUser", message.isUser)
                        .put("model", message.model).put("state", message.state))
                } })) }
        }).toString()

    fun decode(raw: String): Pair<String, List<SavedChat>> {
        val root = JSONObject(raw)
        val records = root.getJSONArray("chats")
        val chats = (0 until records.length()).map { index ->
            val item = records.getJSONObject(index)
            val messages = item.getJSONArray("messages")
            SavedChat(item.getString("id"), item.getString("model"), item.optString("draft"),
                (0 until messages.length()).map { i -> val message = messages.getJSONObject(i)
                    ChatMessage(id = message.getString("id"), text = message.getString("text"),
                        isUser = message.getBoolean("isUser"), model = message.getString("model"), state = message.optString("state", "complete"))
                }, item.getLong("updatedAt"))
        }
        require(chats.all { it.model in listOf("Dua", "Gemini") } && chats.map { it.id }.distinct().size == chats.size)
        return root.getString("activeId") to chats
    }
}
