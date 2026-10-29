package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Existing sidebar color contract, retained while layout/typography follow the selected drawer reference.
 * Legacy dock/search fields remain available to existing consumers; shared palette values are unchanged.
 */
@Immutable
data class DrawerColors(
    val container: Color, val title: Color, val searchCircle: Color, val searchIcon: Color,
    val menuText: Color, val chevron: Color, val divider: Color, val chatText: Color, val subText: Color,
    val newChat: Color, val onNewChat: Color, val avatar: Color, val onAvatar: Color,
    /** Top of the background gradient (fades to [container]); search circle shadow; ring around the avatar. */
    val gradientTop: Color, val searchShadow: Color, val avatarRing: Color,
)

val DrawerLightColors = drawerColors(DuaLightColorScheme, DuaLightColors)

val DrawerDarkColors = drawerColors(DuaDarkColorScheme, DuaDarkColors)

object DrawerTheme {
    val colors: DrawerColors
        @Composable @ReadOnlyComposable get() = drawerColors(MaterialTheme.colorScheme, DuaTheme.colors)
}

/** Existing modal width and 48 dp targets, with spacing for the selected drawer reference. */
object DrawerSizes {
    val width = 360.dp
    val paddingH = 16.dp
    val paddingV = 16.dp
    val headerBottom = 12.dp
    val searchCircle = 46.dp
    val searchIcon = 24.dp
    val brandIcon = 32.dp
    val menuIcon = 26.dp
    val actionPaddingH = 12.dp
    val menuGap = 14.dp
    val menuPaddingV = 12.dp
    val subIndent = 38.dp
    val chevron = 20.dp
    val dividerThickness = 1.dp
    val dividerPaddingV = 16.dp
    val chatPaddingV = 12.dp
    val pinIcon = 20.dp
    val dockTop = 12.dp
    val avatar = 44.dp
    val voiceIcon = 18.dp
    val touch = 48.dp
    /** Soft elevation under the white search circle (reference ~6 dp blur). */
    val searchElevation = 6.dp
    /** White ring around the profile photo / initial. */
    val avatarRing = 3.dp
}

object DrawerShapes {
    val newChat = RoundedCornerShape(24.dp)
    val circle = CircleShape
    val row = RoundedCornerShape(12.dp)
}
