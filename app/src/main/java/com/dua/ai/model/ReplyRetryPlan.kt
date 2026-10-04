package com.dua.ai.model

data class ReplyRetryPlan(val model: String, val context: List<ChatMessage>) {
    companion object {
        fun create(messages: List<ChatMessage>, messageId: String): ReplyRetryPlan {
            val reply = messages.lastOrNull()
            require(reply != null && reply.id == messageId && !reply.isUser && reply.state == "complete") { "Only the latest completed response can be retried." }
            val history = messages.dropLast(1)
            require(history.lastOrNull()?.isUser == true) { "Original prompt is unavailable." }
            require(!history.last().text.contains("\nFiles: ")) { "Original files are not saved. Reattach them and send a new message." }
            return ReplyRetryPlan(reply.model, history.takeLast(39))
        }
    }
}
