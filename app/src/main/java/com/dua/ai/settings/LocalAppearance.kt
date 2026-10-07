package com.dua.ai.settings

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.dua.ai.ui.theme.DuaPalette

@Composable
fun LocalAppearance(value: LocalPreferences, content: @Composable () -> Unit) {
    val dark = value.theme == "dark" || value.theme == "system" && isSystemInDarkTheme()
    val family = when (value.font) { "serif" -> FontFamily.Serif; "monospace" -> FontFamily.Monospace; "system" -> FontFamily.Default; else -> FontFamily.SansSerif }
    val base = Typography()
    val typography = Typography(displayLarge = base.displayLarge.copy(fontFamily = family), displayMedium = base.displayMedium.copy(fontFamily = family), displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family), headlineMedium = base.headlineMedium.copy(fontFamily = family), headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family), titleMedium = base.titleMedium.copy(fontFamily = family), titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family), bodyMedium = base.bodyMedium.copy(fontFamily = family), bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family), labelMedium = base.labelMedium.copy(fontFamily = family), labelSmall = base.labelSmall.copy(fontFamily = family))
    val scheme = if (dark) DuaPalette.darkScheme else DuaPalette.lightScheme
    val view = LocalView.current
    SideEffect { (view.context as? Activity)?.window?.let { window -> WindowCompat.getInsetsController(window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } } }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
