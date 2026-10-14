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
    val newChat: Color, val onNewChat: Color, val avatar: Color, val onAvatar: Color,
    /** Top of the background gradient (fades to [container]); search circle shadow; ring around the avatar. */
    val gradientTop: Color, val searchShadow: Color, val avatarRing: Color,
)

val DrawerLightColors = DrawerColors(
    container = Color(0xFFFFFFFF), title = Color(0xFF111827), searchCircle = Color(0xFFFFFFFF), searchIcon = Color(0xFF1F2937),
    menuText = Color(0xFF111827), chevron = Color(0xFF6B7280), divider = Color(0xFFE5E7EB), chatText = Color(0xFF1F2937), subText = Color(0xFF6B7280),
    newChat = Color(0xFF8B5CF6), onNewChat = Color(0xFFFFFFFF), avatar = Color(0xFFFDE68A), onAvatar = Color(0xFF1F2937),
    gradientTop = Color(0xFFF1F1F3), searchShadow = Color(0x33000000), avatarRing = Color(0xFFFFFFFF),
)

val DrawerDarkColors = DrawerColors(
    container = Color(0xFF111827), title = Color(0xFFF9FAFB), searchCircle = Color(0xFF273244), searchIcon = Color(0xFFE5E7EB),
    menuText = Color(0xFFF9FAFB), chevron = Color(0xFF9CA3AF), divider = Color(0xFF374151), chatText = Color(0xFFE5E7EB), subText = Color(0xFF9CA3AF),
    newChat = Color(0xFF8B5CF6), onNewChat = Color(0xFFFFFFFF), avatar = Color(0xFFFDE68A), onAvatar = Color(0xFF1F2937),
    gradientTop = Color(0xFF1F2937), searchShadow = Color(0x66000000), avatarRing = Color(0xFF273244),
)

object DrawerTheme {
    val colors: DrawerColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) DrawerDarkColors else DrawerLightColors
}

/** Reference sizes. Visuals keep the reference sizes; each control sits in a 48 dp touch slot. */
object DrawerSizes {
    val width = 320.dp
    val paddingH = 28.dp
    val paddingV = 16.dp
    val headerTop = 4.dp
    val headerBottom = 20.dp
    val searchCircle = 46.dp
    val searchIcon = 24.dp
    val menuIcon = 24.dp
    val menuGap = 14.dp
    val menuPaddingV = 12.dp
    val subIcon = 18.dp
    val subGap = 12.dp
    val subIndent = 38.dp
    val chevron = 20.dp
    val dividerThickness = 1.dp
    val dividerPaddingV = 16.dp
    val chatPaddingV = 12.dp
    val pinIcon = 14.dp
    val dockTop = 12.dp
    val newChatPaddingH = 24.dp
    val newChatPaddingV = 13.dp
    val newChatIcon = 20.dp
    val newChatGap = 10.dp
    val avatar = 44.dp
    val voiceIcon = 18.dp
    val dockGap = 0.dp
    val touch = 48.dp
    /** Soft elevation under the white search circle (reference ~6 dp blur). */
    val searchElevation = 6.dp
    /** White ring around the profile photo / initial. */
    val avatarRing = 3.dp
    /** Height of the top background gradient and of the bottom fade over the recent chats. */
    val gradientHeight = 220.dp
    val bottomFade = 96.dp
    /** Space reserved under the list so the last chat can scroll clear of the dock. */
    val dockReserve = 72.dp
}

object DrawerShapes {
    val newChat = RoundedCornerShape(24.dp)
    val circle = CircleShape
    val row = RoundedCornerShape(12.dp)
}

val DrawerTitleStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
val DrawerMenuStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
val DrawerSubStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 15.sp)
val DrawerChatStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 16.sp)
val DrawerNewChatStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
val DrawerAvatarStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
