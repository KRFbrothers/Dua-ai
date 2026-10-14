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
 * Home screen tokens copied from the code of Fareed's "Dua AI Dabba" reference shell (DuaApp.kt HomeScreen).
 * Light values are the reference's exact colours; dark values are the matching Tailwind-gray steps inverted.
 */
@Immutable
data class HomeColors(
    val roundButton: Color, val roundButtonIcon: Color,
    val segmentTrack: Color, val segmentSelected: Color, val segmentSelectedText: Color, val segmentText: Color,
    val promptIcon: Color, val promptText: Color,
    val bar: Color, val barBorder: Color, val barIcon: Color, val input: Color, val placeholder: Color,
    val voice: Color, val onVoice: Color, val modelLabel: Color,
)

val HomeLightColors = HomeColors(
    roundButton = Color(0xFFF3F4F6), roundButtonIcon = Color(0xFF1F2937),
    segmentTrack = Color(0xFFE5E7EB), segmentSelected = Color(0xFFFFFFFF), segmentSelectedText = Color(0xFF111827), segmentText = Color(0xFF6B7280),
    promptIcon = Color(0xFF374151), promptText = Color(0xFF1F2937),
    bar = Color(0xFFF9FAFB), barBorder = Color(0xFFE5E7EB), barIcon = Color(0xFF374151), input = Color(0xFF111827), placeholder = Color(0xFF9CA3AF),
    voice = Color(0xFF8B5CF6), onVoice = Color(0xFFFFFFFF), modelLabel = Color(0xFF6B7280),
)

val HomeDarkColors = HomeColors(
    roundButton = Color(0xFF1F2937), roundButtonIcon = Color(0xFFE5E7EB),
    segmentTrack = Color(0xFF1F2937), segmentSelected = Color(0xFF4B5563), segmentSelectedText = Color(0xFFF9FAFB), segmentText = Color(0xFF9CA3AF),
    promptIcon = Color(0xFFD1D5DB), promptText = Color(0xFFF3F4F6),
    bar = Color(0xFF111827), barBorder = Color(0xFF374151), barIcon = Color(0xFFD1D5DB), input = Color(0xFFF9FAFB), placeholder = Color(0xFF9CA3AF),
    voice = Color(0xFF8B5CF6), onVoice = Color(0xFFFFFFFF), modelLabel = Color(0xFF9CA3AF),
)

object HomeTheme {
    val colors: HomeColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) HomeDarkColors else HomeLightColors
}

/** Reference sizes. Visuals keep the reference sizes; each control sits in a 48 dp touch slot. */
object HomeSizes {
    val roundButton = 44.dp
    val segmentTab = 36.dp
    val segmentTrackInset = 3.dp
    val segmentTabPaddingH = 20.dp
    val barButton = 38.dp
    val voiceButton = 40.dp
    val plusIcon = 24.dp
    val micIcon = 22.dp
    val voiceIcon = 20.dp
    val promptIcon = 20.dp
    val promptGap = 12.dp
    /** Reference spacing (14 dp) minus the extra height of the 48 dp row (38 dp in the reference). */
    val promptSpacing = 4.dp
    val barBorder = 1.dp
    /** Reference bar padding 8/6 dp, less the 5 dp each 48 dp slot adds around a 38 dp button. */
    val barPaddingH = 3.dp
    val barPaddingV = 1.dp
    /** Reference top bar padding 16/8 dp, less the 2 dp each 48 dp slot adds around a 44 dp button. */
    val topBarPaddingH = 14.dp
    val topBarPaddingV = 6.dp
}

object HomeShapes {
    val segmentTrack = RoundedCornerShape(24.dp)
    val segmentTab = RoundedCornerShape(20.dp)
    val bar = RoundedCornerShape(32.dp)
    val round = CircleShape
}

val HomeSegmentSelectedStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
val HomeSegmentStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
val HomeModelLabelStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium, fontSize = 12.sp)
