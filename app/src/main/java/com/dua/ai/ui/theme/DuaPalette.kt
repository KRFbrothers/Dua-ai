package com.dua.ai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Single theme map. Mirrors theme/dua-theme.json and web/dist/theme.css (enforced by web/theme.test.mjs).
object DuaPalette {
    val darkScheme: ColorScheme = darkColorScheme(
        primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
        secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
        background = Color(0xFF141218), surface = Color(0xFF141218),
        surfaceContainer = Color(0xFF211F26), surfaceContainerHigh = Color(0xFF2B2930), surfaceContainerHighest = Color(0xFF36343B),
        onSurface = Color(0xFFE6E0E9), onBackground = Color(0xFFE6E0E9), onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99), error = Color(0xFFFFB1C0)
    )
    val lightScheme: ColorScheme = lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
        background = Color(0xFFFEF7FF), surface = Color(0xFFFEF7FF),
        surfaceContainer = Color(0xFFF3EDF7), surfaceContainerHigh = Color(0xFFECE6F0), surfaceContainerHighest = Color(0xFFE6E0E9),
        onSurface = Color(0xFF1D1B20), onBackground = Color(0xFF1D1B20), onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E), error = Color(0xFFB3261E)
    )
}
