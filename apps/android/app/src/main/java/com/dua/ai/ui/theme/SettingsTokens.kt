package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Settings surfaces and controls use the shared semantic palette defined in Color.kt. */
@Immutable
data class SettingsColors(
    val background: Color, val circleButton: Color, val circleBorder: Color, val circleIcon: Color,
    val avatar: Color, val onAvatar: Color, val editBadge: Color, val editIcon: Color, val name: Color,
    val card: Color, val cardBorder: Color, val rowIcon: Color, val rowText: Color, val value: Color,
    val accentValue: Color, val arrow: Color, val chevron: Color, val divider: Color, val upgrade: Color, val danger: Color,
)

val SettingsLightColors = settingsColors(DuaLightColorScheme, DuaLightColors)

val SettingsDarkColors = settingsColors(DuaDarkColorScheme, DuaDarkColors)

object SettingsTheme {
    val colors: SettingsColors
        @Composable @ReadOnlyComposable get() = settingsColors(MaterialTheme.colorScheme, DuaTheme.colors)
}

/** Reference sizes. Visuals keep the reference sizes; each control sits in a 48 dp touch slot. */
object SettingsSizes {
    val screenPadding = 16.dp
    val headerBottom = 12.dp
    val circleButton = 44.dp
    val avatar = 88.dp
    val editBadge = 28.dp
    val editIcon = 16.dp
    val nameTop = 8.dp
    val cardsTop = 10.dp
    val cardGap = 16.dp
    val border = 1.dp
    val rowPadding = 16.dp
    val rowGap = 12.dp
    val rowIcon = 24.dp
    val valueGap = 4.dp
    val accentGap = 6.dp
    val accentDot = 10.dp
    val arrow = 16.dp
    val chevron = 18.dp
    val dividerStart = 0.dp
    val touch = Sizes.touchTarget
}

object SettingsShapes {
    val card = RoundedCornerShape(24.dp)
    val circle = CircleShape
}
