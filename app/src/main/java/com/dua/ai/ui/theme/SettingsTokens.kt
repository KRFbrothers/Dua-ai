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
 * Settings tokens copied from the code of Fareed's "Dua AI Dabba" reference shell (DuaApp.kt SettingsScreen).
 * Light values are the reference's exact colours; dark values are the matching Tailwind-gray steps inverted.
 */
@Immutable
data class SettingsColors(
    val background: Color, val circleButton: Color, val circleBorder: Color, val circleIcon: Color,
    val avatar: Color, val onAvatar: Color, val editBadge: Color, val editIcon: Color, val name: Color,
    val card: Color, val cardBorder: Color, val rowIcon: Color, val rowText: Color, val value: Color,
    val accentValue: Color, val arrow: Color, val chevron: Color, val divider: Color, val upgrade: Color, val danger: Color,
)

val SettingsLightColors = SettingsColors(
    background = Color(0xFFF9FAFB), circleButton = Color(0xFFFFFFFF), circleBorder = Color(0xFFE5E7EB), circleIcon = Color(0xFF1F2937),
    avatar = Color(0xFFFDE68A), onAvatar = Color(0xFF1F2937), editBadge = Color(0xFFFFFFFF), editIcon = Color(0xFF374151), name = Color(0xFF111827),
    card = Color(0xFFFFFFFF), cardBorder = Color(0xFFE5E7EB), rowIcon = Color(0xFF111827), rowText = Color(0xFF111827), value = Color(0xFF6B7280),
    accentValue = Color(0xFF4B5563), arrow = Color(0xFF6B7280), chevron = Color(0xFF9CA3AF), divider = Color(0xFFF3F4F6), upgrade = Color(0xFF8B5CF6),
    danger = Color(0xFFDC2626),
)

val SettingsDarkColors = SettingsColors(
    background = Color(0xFF030712), circleButton = Color(0xFF111827), circleBorder = Color(0xFF374151), circleIcon = Color(0xFFE5E7EB),
    avatar = Color(0xFFFDE68A), onAvatar = Color(0xFF1F2937), editBadge = Color(0xFF111827), editIcon = Color(0xFFD1D5DB), name = Color(0xFFF9FAFB),
    card = Color(0xFF111827), cardBorder = Color(0xFF374151), rowIcon = Color(0xFFF9FAFB), rowText = Color(0xFFF9FAFB), value = Color(0xFF9CA3AF),
    accentValue = Color(0xFFD1D5DB), arrow = Color(0xFF9CA3AF), chevron = Color(0xFF6B7280), divider = Color(0xFF1F2937), upgrade = Color(0xFFA78BFA),
    danger = Color(0xFFF87171),
)

object SettingsTheme {
    val colors: SettingsColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) SettingsDarkColors else SettingsLightColors
}

/** Reference sizes. Visuals keep the reference sizes; each control sits in a 48 dp touch slot. */
object SettingsSizes {
    val screenPadding = 16.dp
    val headerBottom = 12.dp
    val circleButton = 42.dp
    val avatar = 64.dp
    val editBadge = 24.dp
    val editIcon = 12.dp
    val nameTop = 8.dp
    val cardsTop = 10.dp
    val cardGap = 14.dp
    val border = 1.dp
    val rowPadding = 16.dp
    val rowGap = 12.dp
    val rowIcon = 18.dp
    val valueGap = 4.dp
    val accentGap = 6.dp
    val accentDot = 10.dp
    val arrow = 16.dp
    val chevron = 18.dp
    val dividerStart = 46.dp
    val touch = 48.dp
}

object SettingsShapes {
    val card = RoundedCornerShape(24.dp)
    val circle = CircleShape
}

val SettingsAvatarStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Bold, fontSize = 24.sp)
val SettingsNameStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
val SettingsRowStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Medium, fontSize = 15.sp)
val SettingsUpgradeStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
val SettingsValueStyle = TextStyle(fontFamily = SoehneFont, fontWeight = FontWeight.Normal, fontSize = 13.sp)
