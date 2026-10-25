package com.dua.ai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.dua.ai.settings.AccentPreset

@Immutable
data class DuaAccent(val preset: AccentPreset, val color: Color, val onColor: Color, val container: Color, val onContainer: Color)

val LocalDuaAccent = staticCompositionLocalOf { accentColors(AccentPreset.Orange, true) }
internal fun contrast(foreground: Color, background: Color): Double {
    val a = foreground.luminance().toDouble(); val b = background.luminance().toDouble()
    return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
}
private fun readable(color: Color, surfaces: List<Color>, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    for (step in 0..100) {
        val candidate = lerp(color, target, step / 100f)
        if (surfaces.all { contrast(candidate, it) >= 4.5 }) return candidate
    }
    return target
}
internal fun accentColors(preset: AccentPreset, dark: Boolean): DuaAccent {
    val raw = Color(preset.argb)
    val onRaw = if (contrast(Color.Black, raw) >= contrast(Color.White, raw)) Color.Black else Color.White
    val scheme = if (dark) DuaDarkColorScheme else DuaLightColorScheme
    val container = lerp(scheme.surfaceContainerLow, raw, if (dark) .20f else .12f)
    return DuaAccent(preset, raw, onRaw, container, readable(raw, listOf(container), dark))
}

/** Add accent roles while retaining the original neutral, code, error and logo-related tokens. */
internal fun accentedScheme(base: ColorScheme, preset: AccentPreset, dark: Boolean): ColorScheme {
    val accent = accentColors(preset, dark)
    val surfaces = listOf(base.background, base.surface, base.surfaceVariant, base.surfaceContainerLowest, base.surfaceContainerLow,
        base.surfaceContainer, base.surfaceContainerHigh, base.surfaceContainerHighest)
    val primary = readable(accent.color, surfaces, dark)
    val onPrimary = if (contrast(Color.Black, primary) >= contrast(Color.White, primary)) Color.Black else Color.White
    val opposite = if (dark) DuaLightColorScheme else DuaDarkColorScheme
    return base.copy(primary = primary, onPrimary = onPrimary, primaryContainer = accent.container, onPrimaryContainer = accent.onContainer,
        tertiary = primary, onTertiary = onPrimary, tertiaryContainer = accent.container, onTertiaryContainer = accent.onContainer,
        inversePrimary = readable(accent.color, listOf(opposite.surface), !dark))
}
