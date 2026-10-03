package com.dua.ai.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: String = "Just now",
    val model: String = "Dua 2.0 Flash",
    val hasCode: Boolean = false,
    val codeSnippet: String? = null,
    val codeLanguage: String = "Kotlin",
    val reaction: MessageReaction = MessageReaction.NONE,
    val state: String = "complete"
)

enum class MessageReaction {
    NONE, LIKED, DISLIKED
}

data class PromptSuggestion(
    val title: String,
    val description: String,
    val prompt: String,
    val category: String
)

data class ChatSession(
    val id: String,
    val title: String,
    val category: String = "General",
    val timestamp: String = "Today"
)

