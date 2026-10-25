package com.dua.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

internal object ProjectTheme {
    val colors: ProjectPalette @Composable get() = if (DuaTheme.colors.isDark) ProjectPaletteDark else ProjectPaletteLight
}
internal object ProjectSizes {
    val circle = 46.dp; val icon = 24.dp; val badge = 48.dp; val smallCircle = 42.dp
    val headerPadding = 20.dp; val searchPadding = 32.dp; val searchHeight = 52.dp
    val inputHeight = 60.dp; val createHeight = 54.dp; val settingsHeight = 42.dp
    val border = 1.dp; val elevation = 3.dp; val searchElevation = 6.dp
    val fanCardWidth = 82.dp; val fanCardHeight = 98.dp; val fanWidth = 236.dp; val fanHeight = 140.dp
    val maxWidth = 600.dp; val modeMenuWidth = 180.dp
}
internal object ProjectShapes {
    val chip = RoundedCornerShape(20.dp); val badge = RoundedCornerShape(14.dp)
    val search = RoundedCornerShape(26.dp); val name = RoundedCornerShape(18.dp)
    val button = RoundedCornerShape(27.dp); val settings = RoundedCornerShape(21.dp)
    val popup = RoundedCornerShape(24.dp); val attachments = RoundedCornerShape(28.dp)
}
