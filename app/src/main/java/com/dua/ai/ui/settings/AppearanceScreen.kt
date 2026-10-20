package com.dua.ai.ui.settings

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.AccentPreset
import com.dua.ai.settings.AppearancePreferences
import com.dua.ai.settings.DuaPersonality
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.components.*
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.*
import com.dua.ai.viewmodel.ChatViewModel

@Composable
fun AppearanceScreen(viewModel: ChatViewModel, onBack: () -> Unit, accentPage: Boolean = false) {
    val appearance by viewModel.appearance.collectAsState()
    val settings by viewModel.localSettings.collectAsState()
    val notice by viewModel.settingsStatus.collectAsState()
    val readNotice by viewModel.appearanceNotice.collectAsState()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    if (accentPage) AccentContent(appearance, onBack, { viewModel.setAccent(it) }, notice.ifBlank { readNotice })
    else AppearanceContent(appearance, settings, onBack, { viewModel.setAppearance(it) },
        { viewModel.updateSettings(viewModel.localSettings.value.copy(font = it)) },
        { enabled ->
            viewModel.updateSettings(viewModel.localSettings.value.copy(haptic = enabled))
            if (enabled) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }, notice.ifBlank { readNotice }, onPersonality = viewModel::setPersonality)
}

@Composable
fun AppearanceContent(appearance: AppearancePreferences, settings: LocalPreferences, onBack: () -> Unit,
    onTheme: (String) -> Unit, onFont: (String) -> Unit, onHaptic: (Boolean) -> Unit, notice: String = "",
    onPersonality: (DuaPersonality) -> Unit = {}) {
    DuaPageScaffold("Appearance", onBack) {
        val switcher = com.dua.ai.ui.components.LocalThemeSwitcher.current
        Text("Choose your appearance", style = MaterialTheme.typography.titleLarge)
        Text("Accent color is a separate preference.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup(null, Modifier.selectableGroup()) {
            listOf("system" to "System default", "light" to "Light", "dark" to "Dark").forEachIndexed { i, (key, title) ->
                if (i > 0) SettingsRowDivider()
                var rowCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                Box(Modifier.onGloballyPositioned { rowCenter = it.boundsInRoot().center }) {
                    PreferenceChoice(title, appearance.theme == key) { if (appearance.theme != key) switcher.switch(rowCenter) { onTheme(key) } }
                }
            }
        }
        Text("System default follows your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup("Font", Modifier.selectableGroup()) {
            listOf("default" to "Inter (default)", "system" to "Device font", "serif" to "Serif", "monospace" to "Monospace").forEachIndexed { i, (key, title) ->
                if (i > 0) SettingsRowDivider()
                PreferenceChoice(title, settings.font == key) { onFont(key) }
            }
        }
        SettingsGroup(null) { SettingsToggleRow(Icons.Outlined.Vibration, "Haptic feedback", checked = settings.haptic, onCheckedChange = onHaptic) }
        Text("Text size follows your phone's font size setting.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup("Dua AI Personality", Modifier.selectableGroup()) {
            DuaPersonality.entries.forEachIndexed { i, personality ->
                if (i > 0) SettingsRowDivider()
                SettingsChoiceRow(personality.label, appearance.personality == personality,
                    subtitle = personality.description, onClick = { onPersonality(personality) })
            }
        }
        Text("Choose how Dua AI responds. Saved on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notice.isNotBlank()) Text(notice)
    }
}

@Composable
private fun PreferenceChoice(title: String, selected: Boolean, onClick: () -> Unit) = SettingsChoiceRow(title, selected, onClick = onClick)

@Composable
fun AccentContent(appearance: AppearancePreferences, onBack: () -> Unit, onAccent: (AccentPreset?) -> Unit, notice: String = "") {
    DuaPageScaffold("Accent color", onBack) {
        Text("Choose your accent", style = MaterialTheme.typography.titleLarge)
        Text("One selection for both appearances.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val effective = appearance.resolvedAccent(DuaTheme.colors.isDark)
        SettingsGroup(null) { Column(Modifier.fillMaxWidth().selectableGroup().padding(SettingsSizes.rowPadding), verticalArrangement = Arrangement.spacedBy(SettingsSizes.rowPadding)) {
            AccentPreset.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { preset ->
                        val colors = accentColors(preset, DuaTheme.colors.isDark)
                        Column(Modifier.weight(1f).selectable(effective == preset, role = Role.RadioButton, onClick = { onAccent(preset) })
                            .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(56.dp).then(if (effective == preset) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier).padding(4.dp)
                                .background(colors.color, CircleShape), contentAlignment = Alignment.Center) {
                                if (effective == preset) Icon(Icons.Outlined.Check, null, tint = colors.onColor)
                            }
                            Text(preset.label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } }
        Text("Live preview", style = MaterialTheme.typography.titleMedium)
        // Stack previews at large font sizes so labels and switches keep their full touch-free space.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f || maxWidth < 300.dp
            if (large) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AccentPreview(appearance, false); AccentPreview(appearance, true) }
            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { AccentPreview(appearance, false) }
                Box(Modifier.weight(1f)) { AccentPreview(appearance, true) }
            }
        }
        OutlinedButton(onClick = { onAccent(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Reset to default") }
        Text(if (appearance.accent == null) "Default: Purple in Light · Orange in Dark" else "${appearance.accent.label} override shown in both previews.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notice.isNotBlank()) Text(notice)
    }
}

@Composable
private fun AccentPreview(appearance: AppearancePreferences, dark: Boolean) {
    DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light"), darkTheme = dark, accent = appearance.accent, updateSystemBars = false) {
        Surface(Modifier.fillMaxWidth(), shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (dark) "Dark" else "Light")
                Surface(shape = DuaShape.chip, color = DuaTheme.accent.color, contentColor = DuaTheme.accent.onColor, modifier = Modifier.fillMaxWidth()) {
                    Text("New chat", Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(true, onCheckedChange = null, modifier = Modifier.size(width = 48.dp, height = 48.dp))
                    Text("Enabled", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
