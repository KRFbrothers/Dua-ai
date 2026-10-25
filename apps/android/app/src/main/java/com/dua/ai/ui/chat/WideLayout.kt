/*
 * Adapted from Reply's ReplyApp.kt / ReplyNavigationComponents.kt (android/compose-samples, read through
 * the fork https://github.com/KRFbrothers/compose-samples) and Now in Android's adaptive app shell
 * (https://github.com/KRFbrothers/nowinandroid), Copyright 2022 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md. Changes: uses the
 * available width from BoxWithConstraints instead of the window-size-class library, and only decides
 * whether chat history is a permanent side panel (expanded) or the existing modal drawer.
 */
package com.dua.ai.ui.chat

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material window width classes, from the width Dua actually gets (split screen and freeform windows included). */
enum class ChatWidth { Compact, Medium, Expanded }

object WideLayout {
    val MEDIUM_MIN = 600.dp
    val EXPANDED_MIN = 840.dp

    fun widthClass(width: Dp): ChatWidth = when {
        width >= EXPANDED_MIN -> ChatWidth.Expanded
        width >= MEDIUM_MIN -> ChatWidth.Medium
        else -> ChatWidth.Compact
    }

    /** Expanded windows keep chat history open beside the chat; feature pages (Agent, Imagine...) stay full width. */
    fun permanentHistory(width: Dp, featureOpen: Boolean): Boolean = !featureOpen && widthClass(width) == ChatWidth.Expanded
}
