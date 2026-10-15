package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * In-chat conversation tokens copied from the code of Fareed's "Dua AI Dabba" reference shell
 * (DuaApp.kt HomeScreen, the `messages.isNotEmpty()` branch). Top bar and composer reuse [HomeTheme].
 * Light values are the reference's exact colours; dark values are the matching Tailwind-gray steps inverted.
 */
@Immutable
data class ChatColors(
    val background: Color,
    val userBubble: Color, val onUserBubble: Color,
    val assistantBubble: Color, val assistantText: Color,
    /** Chat-area chips, cards and media (zip look: #F3F4F6 fills, #E5E7EB borders, #111827 text, purple accent). */
    val chipFill: Color, val chipBorder: Color, val chipText: Color, val chipIcon: Color,
    val text: Color, val mutedText: Color,
    val cardFill: Color, val divider: Color,
    val mediaFill: Color, val shimmer: Color,
    val accent: Color, val accentSoft: Color, val onAccentSoft: Color,
    val errorFill: Color, val onErrorFill: Color, val errorIcon: Color,
)

val ChatLightColors = ChatColors(
    background = Color(0xFFFFFFFF),
    userBubble = Color(0xFFF3F4F6), onUserBubble = Color(0xFF111827),
    assistantBubble = Color.Transparent, assistantText = Color(0xFF111827),
    chipFill = Color(0xFFF3F4F6), chipBorder = Color(0xFFE5E7EB), chipText = Color(0xFF374151), chipIcon = Color(0xFF6B7280),
    text = Color(0xFF111827), mutedText = Color(0xFF6B7280),
    cardFill = Color(0xFFF9FAFB), divider = Color(0xFFE5E7EB),
    mediaFill = Color(0xFFF3F4F6), shimmer = Color(0xFFE5E7EB),
    // Same purple as Sheet/Page/Home/Drawer tokens (#8B5CF6), not a chat-only copy.
    accent = PageLightColors.accent, accentSoft = PageLightColors.accentContainer, onAccentSoft = PageLightColors.onAccentContainer,
    errorFill = Color(0xFFFEF2F2), onErrorFill = Color(0xFF991B1B), errorIcon = Color(0xFFDC2626),
)

val ChatDarkColors = ChatColors(
    background = DuaPalette.Neutral4,
    userBubble = Color(0xFF1F2937), onUserBubble = Color(0xFFF9FAFB),
    assistantBubble = Color.Transparent, assistantText = Color(0xFFF9FAFB),
    chipFill = Color(0xFF1F2937), chipBorder = Color(0xFF374151), chipText = Color(0xFFD1D5DB), chipIcon = Color(0xFF9CA3AF),
    text = Color(0xFFF9FAFB), mutedText = Color(0xFF9CA3AF),
    cardFill = Color(0xFF111827), divider = Color(0xFF374151),
    mediaFill = Color(0xFF1F2937), shimmer = Color(0xFF374151),
    // Same purple as Sheet/Page/Home/Drawer tokens (#8B5CF6), not a chat-only copy.
    accent = PageDarkColors.accent, accentSoft = PageDarkColors.accentContainer, onAccentSoft = PageDarkColors.onAccentContainer,
    errorFill = Color(0xFF450A0A), onErrorFill = Color(0xFFFECACA), errorIcon = Color(0xFFF87171),
)

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
    val listPaddingH = 16.dp
    val listPaddingTop = 20.dp
    val listPaddingBottom = 12.dp
    val messageSpacing = 14.dp
    val bubblePaddingH = 16.dp
    val bubblePaddingV = 10.dp
    val border = 1.dp
    val divider = 1.dp
    val searchSelectionBorder = 2.dp
    val fileChipNameMax = 160.dp
    val legacyFileChipNameMax = 140.dp
    val sourceTextMax = 200.dp
    val siteIcon = 20.dp
    val favicon = 16.dp
    val typingDot = 8.dp
    val typingDotGap = 6.dp
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
val ChatMessageStyle = TextStyle(fontFamily = InterFont, fontSize = 16.sp, lineHeight = 24.sp)
