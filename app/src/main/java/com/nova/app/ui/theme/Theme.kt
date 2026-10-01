package com.nova.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = NovaNavy,
    onPrimary = NovaOnNavy,
    primaryContainer = NovaNavyContainer,
    onPrimaryContainer = NovaOnNavyContainer,
    secondary = NovaAmber,
    onSecondary = NovaOnAmber,
    secondaryContainer = NovaAmberContainer,
    onSecondaryContainer = NovaOnAmberContainer,
    tertiary = NovaForest,
    background = NovaPaper,
    onBackground = NovaInk,
    surface = NovaSurfaceLight,
    onSurface = NovaInk,
    surfaceVariant = NovaSurfaceVariantLight,
    onSurfaceVariant = NovaOnSurfaceVariantLight,
    outline = NovaOutlineLight,
    error = NovaErrorLight,
    onError = NovaOnErrorLight
)

private val DarkColors = darkColorScheme(
    primary = NovaNavyDark,
    onPrimary = NovaOnNavyDark,
    primaryContainer = NovaNavyContainerDark,
    onPrimaryContainer = NovaOnNavyContainerDark,
    secondary = NovaAmberDark,
    onSecondary = NovaOnAmberDark,
    secondaryContainer = NovaAmberContainerDark,
    onSecondaryContainer = NovaOnAmberContainerDark,
    tertiary = NovaForestDark,
    background = NovaVoid,
    onBackground = NovaMist,
    surface = NovaSurfaceDark,
    onSurface = NovaMist,
    surfaceVariant = NovaSurfaceVariantDark,
    onSurfaceVariant = NovaOnSurfaceVariantDark,
    outline = NovaOutlineDark,
    error = NovaErrorDark,
    onError = NovaOnErrorDark
)

/**
 * @param useDynamicColor Android 12+ wallpaper colors. Off by default so Nova
 * brand colors stay consistent in screenshots and first install.
 */
@Composable
fun NovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content
    )
}
