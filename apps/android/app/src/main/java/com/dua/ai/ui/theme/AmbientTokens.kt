package com.dua.ai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colors and timings for Gemini Light Mode & Claude Warm Dark Mode ambient washes. */
internal object AmbientTokens {
    const val driftMs = 26_000
    const val breatheMs = 11_000
    const val spinMs = 4_200
    const val pulseMs = 1_600
    const val sparkleMs = 1_100
    const val modeMs = 500
    const val homeIntensity = 0.8f
    const val conversationIntensity = 0.28f
    const val conversationWash = 0.35f
    val orbSize = 28.dp
}

@Immutable
internal data class AmbientPalette(
    val blue: Color, val indigo: Color, val purple: Color, val magenta: Color,
    val pink: Color, val orange: Color, val coral: Color, val baseEnd: Color,
    val sky: Color, val task: Color,
) {
    val sweep get() = listOf(blue, indigo, purple, magenta, pink, orange, coral, blue)
}

/** Google Gemini Light Mode soft sky-blue to periwinkle-lavender lower glow. */
internal val AmbientLight = AmbientPalette(
    blue = Color(0xFF87AEFD), indigo = Color(0xFFBFC6FF), purple = Color(0xFFD0BCFF), magenta = Color(0xFFF6B8BE),
    pink = Color(0xFFFFB1C8), orange = Color(0xFFFFD8A8), coral = Color(0xFFFFC4A3), baseEnd = Color(0xFFF0F4F9),
    sky = Color(0xFFB1DAF0), task = Color(0xFF9294FF),
)

/** Anthropic Claude Warm Dark Mode subtle warm terracotta-amber charcoal glow. */
internal val AmbientDark = AmbientPalette(
    blue = Color(0xFFD97757), indigo = Color(0xFFB85C3E), purple = Color(0xFF8C4A36), magenta = Color(0xFFC4684B),
    pink = Color(0xFFD97757), orange = Color(0xFFE09153), coral = Color(0xFFD97757), baseEnd = Color(0xFF21201E),
    sky = Color(0xFF2E2520), task = Color(0xFF5C3A2B),
)
