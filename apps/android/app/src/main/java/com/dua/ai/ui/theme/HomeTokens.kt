package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Home chrome uses the shared semantic palette in Color.kt. */
@Immutable
data class HomeColors(
    val roundButton: Color, val roundButtonIcon: Color,
    val segmentTrack: Color, val segmentSelected: Color, val segmentSelectedText: Color, val segmentText: Color,
    /** Translucent selected pill when the Chat/Task toggle floats over a conversation. */
    val segmentFloatSelected: Color,
    val promptIcon: Color, val promptText: Color, val promptRow: Color, val tagline: Color,
    val bar: Color, val barBorder: Color, val barIcon: Color, val input: Color, val placeholder: Color,
    val voice: Color, val onVoice: Color, val modelLabel: Color,
)

val HomeLightColors = homeColors(DuaLightColorScheme, DuaLightColors)

val HomeDarkColors = homeColors(DuaDarkColorScheme, DuaDarkColors)

private val LocalHomeColors = staticCompositionLocalOf<HomeColors?> { null }

/** Home-only roles; feature/project composers and open sheets retain their existing palette. */
@Composable
internal fun HomeChrome(enabled: Boolean, content: @Composable () -> Unit) {
    val extra = DuaTheme.colors
    val scheme = MaterialTheme.colorScheme
    val colors = if (enabled) homeColors(scheme, extra).copy(
        segmentSelected = if (extra.isDark) DuaMatch.HomeDarkBar else scheme.surfaceContainerHigh,
        segmentSelectedText = scheme.onSurface,
        tagline = scheme.onSurfaceVariant,
        bar = if (extra.isDark) DuaMatch.HomeDarkBar else scheme.surfaceContainerLowest,
        barBorder = scheme.outlineVariant.copy(alpha = .35f),
    ) else null
    // Keep the Home surface opaque; other composers retain their existing floating overlay.
    CompositionLocalProvider(
        LocalHomeColors provides colors,
        LocalDuaColors provides (colors?.let { extra.copy(floatingSurface = it.bar) } ?: extra),
        content = content,
    )
}

object HomeTheme {
    val colors: HomeColors
        @Composable @ReadOnlyComposable get() = LocalHomeColors.current ?: homeColors(MaterialTheme.colorScheme, DuaTheme.colors)
}

/** Reference sizes. Visuals keep the reference sizes; each control sits in a 48 dp touch slot. */
object HomeSizes {
    /** Original 610:1024 lockup fills the former logo-and-greeting area without stretching. */
    val heroLogo = 80.dp
    val heroCompactLogo = 64.dp
    val heroCompactHeight = 320.dp
    /** assistant_robin_chat_input_max_width from the supplied Google App resource table. */
    val composerMaxWidth = 696.dp
    val referenceIcon = 28.dp
    /** Idle Home inset is 40 dp; the keyboard state opens to 20 dp. Both pills are 80 dp tall. */
    val composerMarginH = 40.dp
    val composerCompactMarginH = 20.dp
    val composerRoomyWidth = 360.dp
    val composerInnerPaddingH = 16.dp
    val composerIdlePaddingV = 16.dp
    val composerKeyboardPaddingV = 16.dp
    val composerElevation = 6.dp
    val noElevation = 0.dp
    fun composerHorizontalMargin(availableWidth: Dp): Dp =
        if (availableWidth >= composerRoomyWidth) composerMarginH else composerCompactMarginH
    val roundButton = 44.dp
    val segmentTab = 28.dp
    val segmentTabPaddingH = 14.dp
    val voiceButton = 40.dp
    val plusIcon = 24.dp
    val micIcon = 22.dp
    val voiceIcon = 20.dp
    val promptIcon = 20.dp
    val promptGap = 12.dp
    val promptRowPaddingH = 12.dp
    val barBorder = 1.dp
    /** Reference bar padding 8/6 dp, less the 5 dp each 48 dp slot adds around a 38 dp button. */
    val barPaddingH = 3.dp
    /** Reference top bar padding 16/8 dp, less the 2 dp each 48 dp slot adds around a 44 dp button. */
    val topBarPaddingH = 14.dp
    val topBarPaddingV = 6.dp
    val headerDividerWidth = 1.dp
    val headerDividerHeight = 18.dp
    val modelPillMinHeight = 32.dp
    val modelPillPaddingH = 10.dp
}

/** Home empty-state motion durations (ms); callers snap to 0 when reduce-motion is on. */
object HomeMotion {
    const val fadeIn = 240
    const val fadeOut = 180
    const val resize = DuaMotion.DurationNormal
}

object HomeShapes {
    val segmentTrack = RoundedCornerShape(24.dp)
    val segmentTab = RoundedCornerShape(20.dp)
    val bar = DuaShape.composer
    /** 40 dp on the 80 dp Home pill is a capsule. */
    val homeBar = RoundedCornerShape(40.dp)
    /** Task starter rows on Home. */
    val promptRow = RoundedCornerShape(16.dp)
    val round = CircleShape
}
