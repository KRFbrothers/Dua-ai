package com.dua.ai.ui.chat

import com.dua.ai.model.ChatMessage
import com.dua.ai.ui.history.SearchSnippet
import com.dua.ai.ui.history.searchSnippet

data class ConversationMatch(val messageId: String, val messageIndex: Int, val snippet: SearchSnippet)

/** One result per matching message, in conversation order. Never reads hidden context or file contents. */
object ConversationSearch {
    const val MAX_QUERY_CHARS = 200

    fun search(messages: List<ChatMessage>, query: String, checkCancelled: () -> Unit = {}): List<ConversationMatch> {
        val needle = query.trim().take(MAX_QUERY_CHARS)
        if (needle.isEmpty()) return emptyList()
        return messages.mapIndexedNotNull { index, message ->
            checkCancelled()
            var match = searchSnippet(message.text, needle, if (message.isUser) "You: " else "Dua: ")
            if (match == null && !message.isUser) for (run in message.codeRuns) {
                checkCancelled()
                match = searchSnippet(run.code, needle, "Code: ") ?: run.output?.let { searchSnippet(it, needle, "Output: ") }
                if (match != null) break
            }
            match?.let { ConversationMatch(message.id, index, it) }
        }
    }
}
