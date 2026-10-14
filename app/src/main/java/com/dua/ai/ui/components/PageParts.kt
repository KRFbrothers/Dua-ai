package com.dua.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import com.dua.ai.ui.theme.PageTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.theme.PageBodyStyle
import com.dua.ai.ui.theme.PageCardTitleStyle
import com.dua.ai.ui.theme.PageNoteStyle
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.ui.theme.SettingsShapes
import com.dua.ai.ui.theme.SettingsSizes

/** Shared full-page parts in the Settings (Dabba) look. Buttons and switches reuse SheetParts (purple filled / gray / text / switch). */

/** White (gray-900 dark) card with an E5E7EB border and 24 dp corners, like the Settings cards. */
@Composable
fun PageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = PageTheme.colors
    Surface(shape = SettingsShapes.card, color = p.card, contentColor = p.text, border = BorderStroke(SettingsSizes.border, p.cardBorder),
        modifier = modifier.fillMaxWidth()) { Column(content = content) }
}

@Composable
fun PageDivider(start: Dp = SettingsSizes.dividerStart) =
    HorizontalDivider(color = PageTheme.colors.divider, thickness = SettingsSizes.border, modifier = Modifier.padding(start = start))

@Composable
fun pageTextFieldColors() = PageTheme.colors.let { p ->
    OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, focusedLabelColor = p.accent, cursorColor = p.accent,
        unfocusedBorderColor = p.cardBorder, unfocusedLabelColor = p.subText, focusedTextColor = p.text, unfocusedTextColor = p.text,
        focusedContainerColor = p.card, unfocusedContainerColor = p.card, disabledContainerColor = p.fill,
        focusedLeadingIconColor = p.text, unfocusedLeadingIconColor = p.subText, errorBorderColor = p.danger, errorLabelColor = p.danger)
}

@Composable
fun pageCheckboxColors() = PageTheme.colors.let { p ->
    CheckboxDefaults.colors(checkedColor = p.accent, uncheckedColor = p.subText, checkmarkColor = p.onAccent)
}

@Composable
fun pageRadioColors() = PageTheme.colors.let { p -> RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.subText) }

@Composable
fun pageSliderColors() = PageTheme.colors.let { p ->
    SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.divider)
}

/** [PageCard] with the standard inner padding and item gap. */
@Composable
fun PagePaddedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = PageCard(modifier) {
    Column(Modifier.fillMaxWidth().padding(PageSizes.cardPadding), verticalArrangement = Arrangement.spacedBy(PageSizes.cardGap), content = content)
}

@Composable
fun PageCardTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = PageCardTitleStyle, color = PageTheme.colors.text, modifier = modifier.semantics { heading() })

@Composable
fun PageBody(text: String, modifier: Modifier = Modifier) = Text(text, style = PageBodyStyle, color = PageTheme.colors.text, modifier = modifier)

@Composable
fun PageNote(text: String, modifier: Modifier = Modifier) = Text(text, style = PageNoteStyle, color = PageTheme.colors.subText, modifier = modifier)

/** Status line announced politely by TalkBack. */
@Composable
fun PageStatus(text: String, modifier: Modifier = Modifier) =
    Text(text, style = PageNoteStyle, color = PageTheme.colors.subText, modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite })
