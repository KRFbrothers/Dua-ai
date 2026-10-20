package com.dua.ai.viewmodel

import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiException

// Pure helpers moved out of ChatViewModel.kt unchanged (refactor only).

internal fun failureText(failure: Throwable): String = when (failure) {
    is DuaApiException, is IllegalArgumentException -> failure.message ?: "Reply nahi aa saka. Dobara try karein."
    is java.net.SocketTimeoutException -> "Dua response timeout hua. Dobara try karein."
    else -> "Connection fail hua. Dobara try karein."
}

internal fun imagineFailure(failure: Throwable): String = when (failure) {
    is com.dua.ai.account.AccountException -> failure.message ?: "Imagine request failed."
    is IllegalArgumentException, is IllegalStateException -> failure.message ?: "Imagine request failed."
    is java.net.SocketTimeoutException -> "Imagine took too long to answer. Check your daily usage in Settings before trying again."
    else -> "Connection fail hua. Dobara try karein."
}

internal fun elapsedThinking(message: ChatMessage): Int? =
    if (message.thinking.isNullOrBlank() || message.startedAt <= 0) null
    else (((System.currentTimeMillis() - message.startedAt) + 500) / 1000).toInt().coerceIn(1, 86400)

internal fun pathsOf(messages: List<ChatMessage>) = messages.flatMap { message -> message.files.map { it.path } + message.media.mapNotNull { it.path } }
