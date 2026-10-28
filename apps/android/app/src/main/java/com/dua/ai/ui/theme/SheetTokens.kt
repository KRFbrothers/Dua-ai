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

/** Sheet and dialog roles share the semantic palette in Color.kt. */
@Immutable
data class SheetColors(
    val container: Color, val handle: Color, val title: Color, val text: Color, val subText: Color, val icon: Color,
    val fill: Color, val selectedFill: Color, val divider: Color, val accent: Color, val onAccent: Color,
    val switchTrackOff: Color, val switchThumbOff: Color, val destructive: Color, val scrim: Color,
)

val SheetLightColors = sheetColors(DuaLightColorScheme, DuaLightColors)

val SheetDarkColors = sheetColors(DuaDarkColorScheme, DuaDarkColors)

object SheetTheme {
    val colors: SheetColors
        @Composable @ReadOnlyComposable get() = if (DuaTheme.colors.isDark) SheetDarkColors else SheetLightColors
}

object SheetSizes {
    val paddingH = 20.dp
    val paddingBottom = 28.dp
    val titleBottom = 12.dp
    val sectionGap = 12.dp
    val rowIcon = 22.dp
    val rowGap = 16.dp
    val rowPaddingH = 16.dp
    val rowPaddingV = 12.dp
    val rowMin = 56.dp
    val tileMin = 96.dp
    val attachmentTileWidth = 112.dp
    val tileIcon = 24.dp
    val tileGap = 10.dp
    val badge = 40.dp
    val handleWidth = 36.dp
    val handleHeight = 4.dp
    val handlePadding = 12.dp
    val dividerThickness = 1.dp
    val dividerIndent = 54.dp
    val dialogMaxWidth = 400.dp
    val touch = 48.dp
}

object SheetShapes {
    val sheet = DuaShape.sheet
    val group = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(20.dp)
    val badge = RoundedCornerShape(12.dp)
    val dialog = RoundedCornerShape(24.dp)
    val button = RoundedCornerShape(24.dp)
    val handle = CircleShape
}

const val SheetDisabledAlpha = 0.38f

val SheetTitleStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
val SheetRowStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
val SheetSubStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
val SheetNoteStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
val SheetTileStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
val SheetButtonStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
val SheetBodyStyle = TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
