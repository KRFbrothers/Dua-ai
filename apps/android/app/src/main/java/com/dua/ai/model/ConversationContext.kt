package com.dua.ai.model

import org.json.JSONObject

/**
 * Server-made summary of the earlier part of one chat (Phase 2 long context). It covers every message
 * up to and including [throughId]; later turns are sent in full. Saved with the chat in the server snapshot and phone cache.
 */
data class ChatSummary(val text: String, val throughId: String) {
    companion object {
        const val MAX_CHARS = 16_000
        fun fromJson(json: JSONObject?): ChatSummary? {
            val text = json?.optString("text").orEmpty()
            val through = json?.optString("throughId").orEmpty()
            return if (text.isBlank() || through.isBlank() || text.length > MAX_CHARS || through.length > 100) null else ChatSummary(text, through)
        }
    }
    fun toJson(): JSONObject = JSONObject().put("text", text).put("throughId", throughId)
}

/** What a long-context request sends: the chat id (prompt-cache routing on the server) and the cached summary. */
data class ConversationContext(val id: String, val summary: String?, val projectId: String? = null) {
    init { require(id.matches(Regex("[A-Za-z0-9-]{8,100}"))) { "Invalid conversation id." } }
    fun toJson(): JSONObject = JSONObject().put("id", id).apply { summary?.takeIf { it.isNotBlank() }?.let { put("summary", it.take(ChatSummary.MAX_CHARS)) } }
}

/** Chooses the turns a long-context request sends and moves the summary boundary when the server summarises. */
object ContextWindow {
    /** Server limit (apps/api/src/core/providers.mjs MAX_MESSAGES). */
    const val MAX_TURNS = 400

    data class Selection(val turns: List<ChatMessage>, val summary: ChatSummary?, val dropped: Int)

    /** The summary only applies while the message it ends at is still in the chat. */
    fun validSummary(messages: List<ChatMessage>, summary: ChatSummary?): ChatSummary? =
        summary?.takeIf { s -> messages.any { it.id == s.throughId } }

    /**
     * Completed turns after the summary boundary, plus anything after them (the new prompt). Oldest turns are
     * dropped beyond [MAX_TURNS] or [maxBytes] (estimated per turn by [turnBytes]); the latest turn always stays.
     */
    fun select(messages: List<ChatMessage>, summary: ChatSummary?, maxBytes: Int, turnBytes: (ChatMessage) -> Int): Selection {
        val valid = validSummary(messages, summary)
        val start = valid?.let { s -> messages.indexOfFirst { it.id == s.throughId } + 1 } ?: 0
        val candidates = messages.drop(start).filter { it.state == "complete" }
        var budget = maxBytes
        val kept = ArrayDeque<ChatMessage>()
        for (message in candidates.asReversed()) {
            val size = turnBytes(message) + 1
            if (kept.isNotEmpty() && (kept.size >= MAX_TURNS || size > budget)) break
            budget -= size
            kept.addFirst(message)
        }
        // A window starts with a user turn (Claude and Gemini expect user first).
        while (kept.size > 1 && kept.first().isUser.not()) kept.removeFirst()
        return Selection(kept.toList(), valid, candidates.size - kept.size)
    }

    /** New summary after the server covered the first [covered] sent turns; null when the event does not fit. */
    fun advance(sent: List<ChatMessage>, covered: Int, text: String): ChatSummary? =
        if (covered in 1 until sent.size && text.isNotBlank() && text.length <= ChatSummary.MAX_CHARS) ChatSummary(text, sent[covered - 1].id) else null
}
