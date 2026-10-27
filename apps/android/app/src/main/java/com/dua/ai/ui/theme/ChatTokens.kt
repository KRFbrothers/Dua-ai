package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Conversation surfaces use the shared semantic palette in Color.kt. */
@Immutable
data class ChatColors(
    val background: Color,
    val userBubble: Color, val onUserBubble: Color,
    val assistantBubble: Color, val assistantText: Color,
    /** Chat-area chips, cards and media (Gemini Light #F0F4F9/#E1E6EF & Claude Warm Dark #21201E/#393834). */
    val chipFill: Color, val chipBorder: Color, val chipText: Color, val chipIcon: Color,
    val text: Color, val mutedText: Color,
    val cardFill: Color, val divider: Color,
    val mediaFill: Color, val shimmer: Color,
    val accent: Color, val accentSoft: Color, val onAccentSoft: Color,
    val errorFill: Color, val onErrorFill: Color, val errorIcon: Color,
)

val ChatLightColors = chatColors(DuaLightColorScheme, DuaLightColors)

val ChatDarkColors = chatColors(DuaDarkColorScheme, DuaDarkColors)

object ChatTheme {
    val colors: ChatColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) ChatDarkColors else ChatLightColors
}

/** Reference sizes: list padding 16 dp (+8 dp outer column top), 12 dp list padding, 14 dp between messages. */
object ChatSizes {
    /** Blinking dot after a streaming reply. */
    val streamingCursor = 10.dp
    const val streamingCursorBlinkMs = 530
    const val streamingFadeInMs = 180
    /** Shared pulse timing for the active Agent step. */
    const val placeholderPulseMs = 900
    val listPaddingH = 16.dp
    val listPaddingTop = Spacing.xxl
    val listPaddingBottom = 12.dp
    val messageSpacing = Spacing.xl
    /** Between consecutive messages from the same author (Jetchat-style grouping). */
    val groupedMessageSpacing = 6.dp
    val bubblePaddingH = 16.dp
    val bubblePaddingV = 10.dp
    val border = 1.dp
    val divider = 1.dp
    val searchSelectionBorder = 2.dp
    val fileChipNameMax = 160.dp
    val legacyFileChipNameMax = 140.dp
    /** Citation cards stay compact while leaving a peek at the next source on a phone. */
    val sourceCardWidth = 240.dp
    val siteIcon = 20.dp
    val favicon = 16.dp
    val mediaMaxWidth = 420.dp
    val portraitVideoMaxWidth = 280.dp
    val placeholderSingleMaxWidth = 300.dp
    val imageDecodeWidth = 360.dp
    val viewerDecodeWidth = 1200.dp
    val mediaIcon = 32.dp
    val viewerButtonMinWidth = 72.dp
}

object ChatShapes {
    val bubble = RoundedCornerShape(20.dp)
    val searchSelection = RoundedCornerShape(12.dp)
}

/** Reference message text: 16 sp. Line height 24 sp keeps multi-line markdown readable. */
