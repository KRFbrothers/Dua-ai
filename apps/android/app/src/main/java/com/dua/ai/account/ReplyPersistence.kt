package com.dua.ai.account

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Owner-bound metadata for one reply. No full history or provider credentials are duplicated here. */
data class ReplyPersistence(val json: JSONObject, val chatId: String, val ownerId: String, val replyId: String, val userId: String)
data class ReplySaved(val ownerId: String, val chatId: String, val replyId: String, val userId: String, val revision: Long,
    val metadata: JSONObject, val replyHash: String, val thinkingSeconds: Int?, val originalMetadata: JSONObject)

object ReplyPersistenceCodec {
    private fun wire(messages: List<ChatMessage>): JSONArray = JSONObject(ChatHistoryCodec.export(
        listOf(SavedChat("hash", "Dua", "", messages, 0)), exportedAt = 0, includeServerFields = true
    )).getJSONArray("chats").getJSONObject(0).getJSONArray("messages")

    /** Same versioned binary JSON encoding as apps/api/src/core/reply-persistence.mjs. */
    fun digest(value: Any): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun marker(value: String) { digest.update(value.toByteArray(Charsets.UTF_8)) }
        fun add(value: Any?) {
            when (value) {
                null, JSONObject.NULL -> marker("z")
                is Boolean -> marker(if (value) "t" else "f")
                is Number -> {
                    val number = value.toDouble(); require(number.isFinite())
                    marker("d"); digest.update(ByteBuffer.allocate(8).putDouble(if (number == 0.0) 0.0 else number).array())
                }
                is String -> {
                    marker("s${value.length}:")
                    val bytes = ByteBuffer.allocate(value.length * 2)
                    value.forEach { bytes.putChar(it) }; digest.update(bytes.array())
                }
                is JSONArray -> { marker("a${value.length()}:"); for (index in 0 until value.length()) add(value.get(index)) }
                is JSONObject -> {
                    val keys = value.keys().asSequence().toList().sorted(); marker("o${keys.size}:")
                    keys.forEach { add(it); add(value.get(it)) }
                }
                else -> error("Unsupported reply digest value")
            }
        }
        marker("dua-messages-v1:"); add(value)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun create(chatId: String, ownerId: String, base: List<ChatMessage>, user: ChatMessage, replyId: String,
        turns: List<ChatMessage>, submittedDraft: String, replacing: Boolean = false): ReplyPersistence {
        require(base.size <= 500 && user.isUser && user.state == "complete" && turns.lastOrNull()?.id == user.id)
        val json = JSONObject().put("chatId", chatId).put("ownerId", ownerId).put("replyId", replyId)
            .put("baseHash", digest(wire(base))).put("replaceReply", replacing)
            .put("user", wire(listOf(user)).getJSONObject(0)).put("turnIds", JSONArray(turns.map { it.id }))
            .put("submittedDraft", submittedDraft)
        if (replacing) json.put("prefixHash", digest(wire(base.dropLast(2))))
        return ReplyPersistence(json, chatId, ownerId, replyId, user.id)
    }
    fun receipt(json: JSONObject): ReplySaved {
        fun id(key: String) = (json.get(key) as? String)?.also { require(it.isNotBlank() && it.length <= 100) } ?: error("Invalid saved reply ID")
        fun integer(key: String): Long {
            val number = json.get(key) as? Number ?: error("Invalid saved reply number")
            require(number.toDouble().isFinite() && number.toDouble() == number.toLong().toDouble())
            return number.toLong()
        }
        val owner = id("ownerId"); require(owner.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")))
        val revision = integer("revision"); require(revision in 1..2147483647L)
        val hash = json.getString("replyHash"); require(hash.matches(Regex("[a-f0-9]{64}")))
        val seconds = if (json.has("thinkingSeconds")) integer("thinkingSeconds").also { require(it in 0..86400) }.toInt() else null
        return ReplySaved(owner, id("chatId"), id("replyId"), id("userId"), revision, json.getJSONObject("chat"), hash, seconds, json.getJSONObject("original"))
    }
    /** Validate the server receipt against the exact streamed content before replacing cached metadata. */
    fun confirmedChat(receipt: ReplySaved, messages: List<ChatMessage>): SavedChat {
        val reply = messages.lastOrNull() ?: error("Reply content missing")
        require(reply.id == receipt.replyId && !reply.isUser && reply.state == "complete" && messages.getOrNull(messages.lastIndex - 1)?.id == receipt.userId)
        require(digest(wire(listOf(reply))) == receipt.replyHash) { "Server saved reply differs from the phone. Review cloud history." }
        val chat = JSONObject(receipt.metadata.toString()).put("messages", wire(messages))
        require(chat.getString("id") == receipt.chatId)
        val root = JSONObject().put("format", "dua-chat-export").put("version", 1).put("exportedAt", 0).put("chats", JSONArray().put(chat))
        // Include this chat's project definition only for codec validation; existing project settings remain intact.
        val projectId = chat.optString("projectId", "personal")
        if (projectId != "personal") root.put("projects", com.dua.ai.projects.ProjectCodec.toJson(
            com.dua.ai.projects.ProjectCodec.defaults + if (projectId !in com.dua.ai.projects.ProjectCodec.defaults.map { it.id })
                listOf(com.dua.ai.projects.ChatProject(projectId, projectId)) else emptyList()))
        return ChatHistoryCodec.parseImport(root.toString()).single()
    }

    /** Adopt fresh server metadata while retaining local edits absent from the request's server baseline. */
    fun cachedMetadata(confirmed: SavedChat, prior: SavedChat?, draft: String, buildRepository: String?, receipt: ReplySaved): SavedChat {
        val original = receipt.originalMetadata
        val title = original.optString("title").takeUnless { original.isNull("title") || it.isBlank() }
        val build = original.optString("buildRepository").takeUnless { original.isNull("buildRepository") || it.isBlank() }
        return confirmed.copy(
            draft = draft.takeIf(String::isNotEmpty) ?: confirmed.draft,
            customTitle = if (prior?.customTitle != title) prior?.customTitle else confirmed.customTitle,
            pinned = if (prior != null && prior.pinned != original.optBoolean("pinned")) prior.pinned else confirmed.pinned,
            buildRepository = if (buildRepository != build) buildRepository else confirmed.buildRepository)
    }
}
