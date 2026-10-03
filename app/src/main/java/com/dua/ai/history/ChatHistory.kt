package com.dua.ai.history

import com.dua.ai.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

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
    fun export(chats: List<SavedChat>, exportedAt: Long = System.currentTimeMillis()): String = JSONObject()
        .put("format", "dua-chat-export").put("version", 1).put("exportedAt", exportedAt)
        .put("chats", JSONArray().apply {
            chats.sortedByDescending { it.updatedAt }.forEach { chat ->
                put(JSONObject().put("id", chat.id).put("model", chat.model).put("draft", chat.draft)
                    .put("updatedAt", chat.updatedAt).put("messages", JSONArray().apply {
                        chat.messages.forEach { message -> put(JSONObject()
                            .put("role", if (message.isUser) "user" else "model")
                            .put("text", message.text).put("state", "complete")) }
                    }))
            }
        }).toString(2)

    fun encode(chats: List<SavedChat>, activeId: String): String = JSONObject()
        .put("activeId", activeId).put("chats", JSONArray().apply {
            chats.forEach { chat -> put(JSONObject().put("id", chat.id).put("model", chat.model)
                .put("draft", chat.draft).put("updatedAt", chat.updatedAt)
                .put("messages", JSONArray().apply { chat.messages.forEach { message ->
                    put(JSONObject().put("id", message.id).put("text", message.text).put("isUser", message.isUser)
                        .put("model", message.model))
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
                        isUser = message.getBoolean("isUser"), model = message.getString("model"))
                }, item.getLong("updatedAt"))
        }
        require(chats.all { it.model in listOf("Dua", "Gemini") } && chats.map { it.id }.distinct().size == chats.size)
        return root.getString("activeId") to chats
    }
}
