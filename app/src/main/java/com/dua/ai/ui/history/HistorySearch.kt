package com.dua.ai.ui.history

import com.dua.ai.history.SavedChat

data class HistoryMatch(val chat: SavedChat, val snippet: String? = null, val matchStart: Int = 0, val matchEnd: Int = 0)

/** Searches the supplied cache only. Project scoping happens before this function is called. */
object HistorySearch {
    const val MAX_QUERY_CHARS = 200

    fun search(chats: List<SavedChat>, query: String, checkCancelled: () -> Unit = {}): List<HistoryMatch> {
        val needle = query.trim()
        return chats.mapNotNull { chat ->
            checkCancelled()
            if (needle.isEmpty()) return@mapNotNull HistoryMatch(chat)
            fun match(text: String, label: String): HistoryMatch? {
                val snippet = searchSnippet(text, needle, label) ?: return null
                return HistoryMatch(chat, snippet.text, snippet.matchStart, snippet.matchEnd)
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
