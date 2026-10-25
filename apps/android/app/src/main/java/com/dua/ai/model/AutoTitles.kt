package com.dua.ai.model

/** When a chat gets an automatic title: exactly its first exchange, finished, with text on both sides. */
object AutoTitles {
    fun shouldTitle(messages: List<ChatMessage>): Boolean =
        messages.size == 2 && messages[0].isUser && !messages[1].isUser && messages[1].state == "complete" &&
            !messages[1].isImagine && messages[0].text.isNotBlank() && messages[1].text.isNotBlank()
}
