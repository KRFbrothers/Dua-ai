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
 * Bottom-sheet and dialog tokens. The "Dua AI Dabba" reference (DuaApp.kt) has no sheet or dialog designs,
 * so these are DERIVED from its palette: white surface, #F3F4F6 fills, #111827 text, #E5E7EB dividers,
 * #8B5CF6 purple accent, Inter 16 sp semibold rows with 22 dp icons (the drawer menu row).
 * Dark values are the matching Tailwind-gray steps inverted, same as DrawerTokens.
 */
@Immutable
data class SheetColors(
    val container: Color, val handle: Color, val title: Color, val text: Color, val subText: Color, val icon: Color,
    val fill: Color, val selectedFill: Color, val divider: Color, val accent: Color, val onAccent: Color,
    val switchTrackOff: Color, val switchThumbOff: Color, val destructive: Color, val scrim: Color,
)

val SheetLightColors = SheetColors(
    container = Color(0xFFFFFFFF), handle = Color(0xFFD1D5DB), title = Color(0xFF111827), text = Color(0xFF111827),
    subText = Color(0xFF6B7280), icon = Color(0xFF111827), fill = Color(0xFFF3F4F6), selectedFill = Color(0xFFEDE9FE),
    divider = Color(0xFFE5E7EB), accent = Color(0xFF8B5CF6), onAccent = Color(0xFFFFFFFF),
    switchTrackOff = Color(0xFFE5E7EB), switchThumbOff = Color(0xFFFFFFFF), destructive = Color(0xFFDC2626), scrim = Color(0x66000000),
)

val SheetDarkColors = SheetColors(
    container = Color(0xFF111827), handle = Color(0xFF4B5563), title = Color(0xFFF9FAFB), text = Color(0xFFF9FAFB),
    subText = Color(0xFF9CA3AF), icon = Color(0xFFF9FAFB), fill = Color(0xFF1F2937), selectedFill = Color(0xFF2E1065),
    divider = Color(0xFF374151), accent = Color(0xFF8B5CF6), onAccent = Color(0xFFFFFFFF),
    switchTrackOff = Color(0xFF374151), switchThumbOff = Color(0xFF9CA3AF), destructive = Color(0xFFF87171), scrim = Color(0x99000000),
)

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
    val tileMin = 88.dp
    val attachmentTileWidth = 100.dp
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
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
