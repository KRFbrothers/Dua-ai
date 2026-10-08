package com.dua.ai.model

/** [context]: turns older servers get (last 39); [full]: every earlier turn, for the long-context window. */
data class ReplyRetryPlan(val model: String, val context: List<ChatMessage>, val full: List<ChatMessage> = context) {
    companion object {
        fun create(messages: List<ChatMessage>, messageId: String): ReplyRetryPlan {
            val reply = messages.lastOrNull()
            require(reply != null && reply.id == messageId && !reply.isUser && (reply.state == "complete" || (reply.state == "stopped" && reply.text.isNotBlank()))) { "Only the latest response can be retried." }
            val history = messages.dropLast(1)
            require(history.lastOrNull()?.isUser == true) { "Original prompt is unavailable." }
            require(!history.last().text.contains("\nFiles: ")) { "Original files are not saved. Reattach them and send a new message." }
            return ReplyRetryPlan(reply.model, history.takeLast(39), history)
        }
    }
}
