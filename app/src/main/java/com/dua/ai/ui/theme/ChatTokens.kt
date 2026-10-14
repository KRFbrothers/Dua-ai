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
)

val ChatLightColors = ChatColors(
    background = Color(0xFFFFFFFF),
    userBubble = Color(0xFFF3F4F6), onUserBubble = Color(0xFF111827),
    assistantBubble = Color.Transparent, assistantText = Color(0xFF111827),
)

val ChatDarkColors = ChatColors(
    background = DuaPalette.Neutral4,
    userBubble = Color(0xFF1F2937), onUserBubble = Color(0xFFF9FAFB),
    assistantBubble = Color.Transparent, assistantText = Color(0xFFF9FAFB),
)

object ChatTheme {
    val colors: ChatColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) ChatDarkColors else ChatLightColors
}

/** Reference sizes: list padding 16 dp (+8 dp outer column top), 12 dp list padding, 14 dp between messages. */
object ChatSizes {
    val listPaddingH = 16.dp
    val listPaddingTop = 20.dp
    val listPaddingBottom = 12.dp
    val messageSpacing = 14.dp
    val bubblePaddingH = 16.dp
    val bubblePaddingV = 10.dp
}

object ChatShapes {
    val bubble = RoundedCornerShape(20.dp)
}

/** Reference message text: 16 sp. Line height 24 sp keeps multi-line markdown readable. */
val ChatMessageStyle = TextStyle(fontFamily = SoehneFont, fontSize = 16.sp, lineHeight = 24.sp)
