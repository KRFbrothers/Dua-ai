package com.dua.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Markdown reply body text: the reference shell's 16 sp assistant text ([ChatMessageStyle]); light/dark via [ChatTheme]. */
val markdownBodyStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = ChatMessageStyle.copy(color = ChatTheme.colors.assistantText)

/** Inline `code` in markdown replies. */
val markdownInlineCodeStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
