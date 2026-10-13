package com.dua.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Markdown reply body text (Fareed's values); colour follows the theme so dark mode works. */
val markdownBodyStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = TextStyle(fontFamily = SoehneFont, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)

/** Inline `code` in markdown replies. */
val markdownInlineCodeStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
