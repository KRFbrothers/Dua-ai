package com.dua.ai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colors and timings from the approved ambient HTML preview, with Dua's mode washes. */
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

internal val AmbientLight = AmbientPalette(
    blue = Color(0xFFA8C7FA), indigo = Color(0xFFBFC6FF), purple = Color(0xFFD0BCFF), magenta = Color(0xFFF6B8BE),
    pink = Color(0xFFFFB1C8), orange = Color(0xFFFFD8A8), coral = Color(0xFFFFC4A3), baseEnd = Color(0xFFF2F0FF),
    sky = Color(0xFFB9DEFF), task = Color(0xFF9294FF),
)

internal val AmbientDark = AmbientPalette(
    blue = Color(0xFF4285F4), indigo = Color(0xFF5B6CFF), purple = Color(0xFF9B72CB), magenta = Color(0xFFD96570),
    pink = Color(0xFFFF6B9D), orange = Color(0xFFF2A60C), coral = Color(0xFFFF8A3D), baseEnd = Color(0xFF1A1C33),
    sky = Color(0xFF173853), task = Color(0xFF4A3588),
)
