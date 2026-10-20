/*
 * Adapted from Jetchat's JumpToBottom.kt and Conversation.kt (https://github.com/android/compose-samples),
 * Copyright 2020 The Android Open Source Project, licensed under the Apache License, Version 2.0.
 * See THIRD_PARTY_NOTICES.md. Changes: Dua colours and 48 dp target, a "new replies" count with a dot, the slide-in is
 * skipped when animations are off, no backdrop magnifier, and Dua's own same-author spacing rule.
 */
package com.dua.ai.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

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

/** "Latest reply" pill. It slides in like Jetchat's Jump to bottom (no slide when animations are off) and leaves at once. */
@Composable
fun JumpToLatest(visible: Boolean, newReplies: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = com.dua.ai.ui.theme.rememberMotionAllowed()
    AnimatedVisibility(visible, modifier = modifier,
        enter = if (motion) slideInVertically { it / 2 } + fadeIn() else EnterTransition.None, exit = ExitTransition.None) {
        FilledTonalButton(onClick = onClick,
            modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics {
                contentDescription = "Go to latest reply"
                if (newReplies > 0) stateDescription = MessageGrouping.label(newReplies)
            },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = PageTheme.colors.card, contentColor = PageTheme.colors.text),
            border = BorderStroke(SettingsSizes.border, PageTheme.colors.cardBorder),
        ) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Text(MessageGrouping.label(newReplies), modifier = Modifier.padding(start = Spacing.xs))
            if (newReplies > 0) Box(Modifier.padding(start = Spacing.xs).size(8.dp).background(ChatTheme.colors.accent, CircleShape))
        }
    }
}
