package com.dua.ai.ui.history

import com.dua.ai.history.SavedChat

data class HistoryMatch(val chat: SavedChat, val snippet: String? = null, val matchStart: Int = 0, val matchEnd: Int = 0)

/** Searches the supplied cache only. Project scoping happens before this function is called. */
object HistorySearch {
    const val MAX_QUERY_CHARS = 200
    private val whitespace = Regex("\\s+")

    fun search(chats: List<SavedChat>, query: String, checkCancelled: () -> Unit = {}): List<HistoryMatch> {
        val needle = query.trim()
        return chats.mapNotNull { chat ->
            checkCancelled()
            if (needle.isEmpty()) return@mapNotNull HistoryMatch(chat)
            fun match(text: String, label: String): HistoryMatch? {
                val index = text.indexOf(needle, ignoreCase = true)
                if (index < 0) return null
                var start = (index - 32).coerceAtLeast(0)
                var end = (index + needle.length + 80).coerceAtMost(text.length)
                // Never cut a UTF-16 surrogate pair at the context window's edges.
                if (start > 0 && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start--
                if (end < text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end++
                val prefix = label + (if (start > 0) "…" else "") + text.substring(start, index).replace(whitespace, " ")
                val found = text.substring(index, index + needle.length).replace(whitespace, " ")
                val suffix = text.substring(index + needle.length, end).replace(whitespace, " ") + if (end < text.length) "…" else ""
                return HistoryMatch(chat, prefix + found + suffix, prefix.length, prefix.length + found.length)
            }
            match(chat.draft, "Draft: ")?.let { return@mapNotNull it }
            // Prefer the most recent matching turn so the result explains why this chat matched.
            for (message in chat.messages.asReversed()) {
                checkCancelled()
                match(message.text, if (message.isUser) "You: " else "Dua: ")?.let { return@mapNotNull it }
                if (!message.isUser) for (run in message.codeRuns) {
                    checkCancelled()
                    match(run.code, "Code: ")?.let { return@mapNotNull it }
                    run.output?.let { match(it, "Output: ") }?.let { return@mapNotNull it }
                }
            }
            // Keep the existing 'Draft' title searchable even when the conversation is empty.
            match(chat.title, "Title: ")
        }
    }
}
