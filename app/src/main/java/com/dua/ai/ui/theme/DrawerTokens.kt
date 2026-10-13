package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sidebar tokens copied from the code of Fareed's "Dua AI Dabba" reference shell (DuaApp.kt DrawerContent).
 * Light values are the reference's exact colours; dark values are the matching Tailwind-gray steps inverted.
 */
@Immutable
data class DrawerColors(
    val container: Color, val title: Color, val searchCircle: Color, val searchIcon: Color,
    val menuText: Color, val chevron: Color, val divider: Color, val chatText: Color, val subText: Color,
    val newChat: Color, val onNewChat: Color, val avatar: Color, val onAvatar: Color, val voice: Color, val onVoice: Color,
)

val DrawerLightColors = DrawerColors(
    container = Color(0xFFFFFFFF), title = Color(0xFF111827), searchCircle = Color(0xFFF3F4F6), searchIcon = Color(0xFF1F2937),
    menuText = Color(0xFF111827), chevron = Color(0xFF6B7280), divider = Color(0xFFE5E7EB), chatText = Color(0xFF1F2937), subText = Color(0xFF6B7280),
    newChat = Color(0xFF8B5CF6), onNewChat = Color(0xFFFFFFFF), avatar = Color(0xFFFDE68A), onAvatar = Color(0xFF1F2937),
    voice = Color(0xFF8B5CF6), onVoice = Color(0xFFFFFFFF),
)

val DrawerDarkColors = DrawerColors(
    container = Color(0xFF111827), title = Color(0xFFF9FAFB), searchCircle = Color(0xFF1F2937), searchIcon = Color(0xFFE5E7EB),
    menuText = Color(0xFFF9FAFB), chevron = Color(0xFF9CA3AF), divider = Color(0xFF374151), chatText = Color(0xFFE5E7EB), subText = Color(0xFF9CA3AF),
    newChat = Color(0xFF8B5CF6), onNewChat = Color(0xFFFFFFFF), avatar = Color(0xFFFDE68A), onAvatar = Color(0xFF1F2937),
    voice = Color(0xFF8B5CF6), onVoice = Color(0xFFFFFFFF),
)

object DrawerTheme {
    val colors: DrawerColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) DrawerDarkColors else DrawerLightColors
}

/** Reference sizes. Visuals keep the reference sizes; each control sits in a 48 dp touch slot. */
object DrawerSizes {
    val width = 320.dp
    val paddingH = 20.dp
    val paddingV = 16.dp
    val headerTop = 4.dp
    val headerBottom = 20.dp
    val searchCircle = 42.dp
    val searchIcon = 20.dp
    val menuIcon = 22.dp
    val menuGap = 16.dp
    val menuPaddingV = 12.dp
    val subIcon = 18.dp
    val subGap = 12.dp
    val subIndent = 38.dp
    val chevron = 20.dp
    val dividerThickness = 1.dp
    val dividerPaddingV = 16.dp
    val chatPaddingV = 11.dp
    val pinIcon = 14.dp
    val dockTop = 12.dp
    val newChatPaddingH = 22.dp
    val newChatPaddingV = 10.dp
    val newChatIcon = 16.dp
    val newChatGap = 8.dp
    val avatar = 38.dp
    val voiceIcon = 18.dp
    /** Reference gap between avatar and voice is 10 dp; two 48 dp slots around 38 dp circles give exactly that. */
    val dockGap = 0.dp
    val touch = 48.dp
}

object DrawerShapes {
    val newChat = RoundedCornerShape(24.dp)
    val circle = CircleShape
    val row = RoundedCornerShape(12.dp)
}

val DrawerTitleStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Bold, fontSize = 26.sp)
val DrawerMenuStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
val DrawerSubStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Normal, fontSize = 15.sp)
val DrawerChatStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Normal, fontSize = 15.sp)
val DrawerNewChatStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
val DrawerAvatarStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
