package com.dua.ai.model

/**
 * Edit a sent user message (ChatGPT-style): the message and everything after it are replaced by the
 * edited message and a new reply. Only plain-text user turns can be edited; messages that carried
 * files keep their original form (re-attach the files and send a new message instead).
 */
object MessageEdits {
    fun editable(message: ChatMessage): Boolean =
        message.isUser && message.state == "complete" && message.files.isEmpty() && message.inlineAttachments.isEmpty() &&
            message.attachmentText.isNullOrEmpty() && !message.text.contains("\nFiles: ") && message.text.isNotBlank()

    /** Messages kept before the edited one, or null when [id] is not an editable user message in [messages]. */
    fun keptBefore(messages: List<ChatMessage>, id: String): List<ChatMessage>? {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0 || !editable(messages[index])) return null
        return messages.take(index)
    }

    /** Ids removed by the edit (the edited message and every later turn). */
    fun removedIds(messages: List<ChatMessage>, id: String): List<String> {
        val index = messages.indexOfFirst { it.id == id }
        return if (index < 0) emptyList() else messages.drop(index).map { it.id }
    }
}
