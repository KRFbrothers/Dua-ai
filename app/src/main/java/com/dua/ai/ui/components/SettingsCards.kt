package com.dua.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.dua.ai.ui.chat.sheetSwitchColors
import com.dua.ai.ui.theme.SettingsRowStyle
import com.dua.ai.ui.theme.SettingsSectionStyle
import com.dua.ai.ui.theme.SettingsShapes
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.SettingsTheme
import com.dua.ai.ui.theme.SettingsValueStyle

/**
 * Inner settings pages (Voice, Appearance, History and data, Imagine) in the main Settings card + row style:
 * white bordered SettingsTokens cards, reference icon/text sizes, value on the right, chevron, 48 dp rows,
 * purple SheetParts switch. Light and dark come from [SettingsTheme].
 */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = SettingsTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SettingsSizes.accentGap)) {
        if (title != null) Text(title, style = SettingsSectionStyle, color = c.value,
            modifier = Modifier.padding(start = SettingsSizes.rowPadding).semantics { heading() })
        Column(modifier.fillMaxWidth().clip(SettingsShapes.card).background(c.card).border(SettingsSizes.border, c.cardBorder, SettingsShapes.card),
            content = content)
    }
}

@Composable
fun SettingsRowDivider() {
    val c = SettingsTheme.colors
    HorizontalDivider(Modifier.padding(start = SettingsSizes.dividerStart), thickness = SettingsSizes.border, color = c.divider)
}

@Composable
private fun RowScope.Label(icon: ImageVector?, title: String, subtitle: String?, titleColor: Color, enabled: Boolean, stackedValue: String? = null) {
    val c = SettingsTheme.colors
    if (icon != null) Icon(icon, null, tint = if (!enabled) c.chevron else if (titleColor == Color.Unspecified) c.rowIcon else titleColor,
        modifier = Modifier.size(SettingsSizes.rowIcon))
    Column(Modifier.weight(1f)) {
        Text(title, style = SettingsRowStyle, color = if (!enabled) c.value else if (titleColor == Color.Unspecified) c.rowText else titleColor)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = SettingsValueStyle, color = c.value)
        if (!stackedValue.isNullOrBlank()) Text(stackedValue, style = SettingsValueStyle, color = c.value)
    }
}

/** Measure with the actual font scale: long values flow below the label instead of truncating it. */
@Composable
internal fun settingsValueFitsInline(width: Dp, title: String, value: String?, icon: Boolean, chevron: Boolean): Boolean {
    if (value.isNullOrBlank()) return true
    val measurer = rememberTextMeasurer()
    val reserved = SettingsSizes.rowPadding * 2 + SettingsSizes.rowGap +
        (if (icon) SettingsSizes.rowIcon + SettingsSizes.rowGap else Dp(0f)) +
        (if (chevron) SettingsSizes.chevron + SettingsSizes.rowGap else Dp(0f))
    val available = with(LocalDensity.current) { (width - reserved).toPx() }
    return measurer.measure(title, SettingsRowStyle, softWrap = false, maxLines = 1).size.width +
        measurer.measure(value, SettingsValueStyle, softWrap = false, maxLines = 1).size.width <= available
}

/** Navigation row: icon, title, optional subtitle, right-hand value and chevron. */
@Composable
fun SettingsNavRow(icon: ImageVector?, title: String, subtitle: String? = null, value: String? = null, titleColor: Color = Color.Unspecified,
    showChevron: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val c = SettingsTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val inline = settingsValueFitsInline(maxWidth, title, value, icon != null, showChevron)
        Row(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(SettingsSizes.rowPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap)) {
            Label(icon, title, subtitle, titleColor, enabled, stackedValue = value.takeUnless { inline })
            if (inline && !value.isNullOrBlank()) Text(value, style = SettingsValueStyle, color = c.value, maxLines = 1)
            if (showChevron) Icon(Icons.Filled.ChevronRight, null, tint = c.chevron, modifier = Modifier.size(SettingsSizes.chevron))
        }
    }
}

/** Switch row. The whole row is one toggleable (Role.Switch) target. */
@Composable
fun SettingsToggleRow(icon: ImageVector?, title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = SettingsSizes.rowPadding, vertical = SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap)) {
        Label(icon, title, subtitle, Color.Unspecified, enabled)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = sheetSwitchColors())
    }
}

/** Single-choice row (Role.RadioButton). Put rows in a selectableGroup. [trailing] holds an optional extra action. */
@Composable
fun SettingsChoiceRow(title: String, selected: Boolean, subtitle: String? = null, enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(start = SettingsSizes.rowPadding, end = if (trailing != null) SettingsSizes.valueGap else SettingsSizes.rowPadding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap)) {
        Column(Modifier.weight(1f).padding(vertical = SettingsSizes.rowGap)) {
            val c = SettingsTheme.colors
            Text(title, style = SettingsRowStyle, color = if (enabled) c.rowText else c.value)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = SettingsValueStyle, color = c.value)
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled, colors = pageRadioColors())
        trailing?.invoke()
    }
}

/** Static label + right-hand value row (no action). */
@Composable
fun SettingsValueRow(title: String, value: String, modifier: Modifier = Modifier) {
    val c = SettingsTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch).padding(horizontal = SettingsSizes.rowPadding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap)) {
        Text(title, Modifier.weight(1f), style = SettingsRowStyle, color = c.rowText)
        Text(value, style = SettingsValueStyle, color = c.value)
    }
}
