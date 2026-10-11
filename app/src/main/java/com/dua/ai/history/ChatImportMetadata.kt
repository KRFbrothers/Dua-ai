package com.dua.ai.history

import org.json.JSONArray
import org.json.JSONObject

/** Imports must reject lossy/unsafe metadata instead of silently applying the lenient cache decoders. */
internal object ChatImportMetadata {
    private val chatFields = listOf("summary", "buildRepository", "projectId")
    private val userFields = listOf("attachmentText", "files")
    private val replyFields = listOf("thinking", "thinkingSeconds", "media", "imagine", "codeRuns", "build", "proposal")

    fun validate(source: JSONObject, decoded: SavedChat) {
        // Reuse the same bounded codecs as cache/cloud export. Compare source fields recursively:
        // optional defaults may be added, but provided values may not be coerced, truncated or dropped.
        val canonical = JSONObject(ChatHistoryCodec.export(listOf(decoded), exportedAt = 0))
            .getJSONArray("chats").getJSONObject(0)
        for (key in chatFields) if (source.has(key)) unchanged(source.get(key), canonical.get(key))
        decoded.summary?.let { summary -> require(decoded.messages.any { it.id == summary.throughId }) { "Summary refers to a missing message." } }
        val original = source.getJSONArray("messages")
        val messages = canonical.getJSONArray("messages")
        require(original.length() == messages.length())
        for (index in 0 until original.length()) {
            val item = original.getJSONObject(index)
            val isUser = item.getString("role") == "user"
            require((if (isUser) replyFields else userFields).none(item::has))
            val exported = messages.getJSONObject(index)
            for (key in if (isUser) userFields else replyFields) {
                if (item.has(key)) unchanged(item.get(key), exported.get(key))
            }
        }
    }

    private fun unchanged(source: Any, canonical: Any) {
        when (source) {
            is JSONObject -> {
                require(canonical is JSONObject)
                source.keys().forEach { key -> unchanged(source.get(key), canonical.get(key)) }
            }
            is JSONArray -> {
                require(canonical is JSONArray && source.length() == canonical.length())
                for (index in 0 until source.length()) unchanged(source.get(index), canonical.get(index))
            }
            is Number -> require(canonical is Number && source.toDouble().isFinite() &&
                source.toDouble() in 0.0..9007199254740991.0 && source.toDouble() == canonical.toDouble())
            else -> require(source == canonical)
        }
    }
}
