/*
 * Adapted from Jetchat's JumpToBottom.kt and Conversation.kt (https://github.com/android/compose-samples),
 * Copyright 2020 The Android Open Source Project, licensed under the Apache License, Version 2.0.
 * See THIRD_PARTY_NOTICES.md. Changes: Dua's own same-author spacing rule and new-replies count. The button itself is now
 * Telegram's page-down button (TelegramPageDown.kt, GPL v2 or later).
 */
package com.dua.ai.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.ui.theme.ChatSizes

/** Jetchat-style message grouping and the "new replies" count, as pure rules so they are unit-tested. */
object MessageGrouping {
    /** Space above a message: tighter when the same author sends again (Jetchat's isFirstMessageByAuthor). */
    fun gapBefore(messages: List<ChatMessage>, index: Int): Dp = when {
        index <= 0 -> 0.dp
        messages[index - 1].isUser == messages[index].isUser -> ChatSizes.groupedMessageSpacing
        else -> ChatSizes.messageSpacing
    }

    /** Dua replies that arrived after the first [seen] messages (what the reader has not scrolled to yet). */
    fun newReplies(messages: List<ChatMessage>, seen: Int): Int =
        if (seen >= messages.size) 0 else messages.drop(seen.coerceAtLeast(0)).count { !it.isUser }

    fun label(newReplies: Int): String = when (newReplies) { 0 -> "Latest reply"; 1 -> "1 new reply"; else -> "$newReplies new replies" }
}

/** Scroll-to-latest control: Telegram's page-down button with the new-replies counter (see TelegramPageDown.kt). */
@Composable
fun JumpToLatest(visible: Boolean, newReplies: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TelegramPageDownButton(visible, newReplies, onClick, modifier,
        stateDescription = if (newReplies > 0) MessageGrouping.label(newReplies) else null)
}
