package com.dua.ai.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dua.ai.BuildConfig
import com.dua.ai.ui.components.SettingsDivider
import com.dua.ai.ui.components.SettingsRow
import com.dua.ai.ui.components.SettingsSection
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.viewmodel.ChatViewModel

@Immutable
data class SettingsUiState(
    val email: String? = null,
    val theme: String = "dark",
    val font: String = "default",
    val haptic: Boolean = false,
    val memoryEnabled: Boolean = false,
    val notifications: Boolean = false,
    val version: String = BuildConfig.VERSION_NAME,
    /** Imagine row subtitle; null hides the row (backend without Imagine). */
    val imagine: String? = null,
    /** Summary shown on the single Settings → Voice entry. */
    val voice: String? = null,
    val displayName: String = "",
    val accent: String = "Purple",
    val accountBusy: Boolean = false,
    val accountNotice: String = "",
)

internal fun themeLabel(theme: String) = when (theme) { "light" -> "Light"; "system" -> "Match system"; else -> "Dark" }
internal fun fontLabel(font: String) = when (font) { "system" -> "Device font"; "serif" -> "Serif"; "monospace" -> "Monospace"; else -> "Inter" }

/** Profile & Settings hub: approved groups plus preserved existing feature destinations. */
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    val preferences by viewModel.localSettings.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val state = SettingsUiState(
        displayName = account.user?.displayName.orEmpty(), accent = appearance.resolvedAccent(com.dua.ai.ui.theme.DuaTheme.colors.isDark).label, accountBusy = account.busy, accountNotice = account.notice,
        email = account.user?.email, theme = appearance.theme, font = preferences.font, haptic = preferences.haptic,
        memoryEnabled = preferences.memoryEnabled, notifications = preferences.notifications,
        voice = integrations.voice?.let { config ->
            val options = if (preferences.generatedVoice && account.user != null) com.dua.ai.voice.VoiceCodec.ttsOptions(config, preferences.voiceProvider,
                preferences.voiceName, preferences.replyLanguage, preferences.voiceSpeed) else null
            val provider = options?.let { config.provider(it.provider) }
            if (provider == null) "Device voice · " + com.dua.ai.voice.SpeechLanguages.name(preferences.replyLanguage)
            else listOf(com.dua.ai.ui.voice.providerLabel(provider), provider.voice(options.voice).name, provider.language(options.language).name).joinToString(" · ")
        },
        imagine = integrations.imagine?.let { config ->
            if (!config.enabled) "Off on the server" else listOfNotNull(config.providerName(preferences.imagineProvider.ifBlank { null }),
                com.dua.ai.imagine.ImagineCodec.aspectLabel(preferences.imagineAspect), if (config.videoEnabled) "video on" else null).joinToString(" · ")
        },
    )
    SettingsContent(state, onBack, onOpen, onLogOut = viewModel::logOut)
}

@Composable
fun SettingsContent(state: SettingsUiState, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit, onLogOut: () -> Unit = {}) {
    var logout by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    ProfileSettingsScaffold(state.displayName.ifBlank { if (state.email == null) "Profile & Settings" else "Google account" }, onBack,
        onEdit = { onOpen(DuaDestination.AvatarEdit) }, onSearch = { onOpen(DuaDestination.SettingsSearch) }, onAbout = { about = true }) {
        SettingsSection(null) {
            SettingsRow(Icons.Outlined.PersonOutline, "Account", state.email ?: "Not signed in") { onOpen(DuaDestination.Account) }
        }
        if (state.accountNotice.isNotBlank()) androidx.compose.material3.Text(state.accountNotice,
            modifier = androidx.compose.ui.Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
        SettingsSection("Personalization") {
            SettingsRow(Icons.Outlined.Tune, "Personalization", "${themeLabel(state.theme)} · ${fontLabel(state.font)}") { onOpen(DuaDestination.Appearance) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Psychology, "Memory", if (state.memoryEnabled) "On" else "Off") { onOpen(DuaDestination.Memory) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Extension, "Plugins") { onOpen(DuaDestination.Plugins) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Palette, "Accent color", state.accent) { onOpen(DuaDestination.Accent) }
        }
        SettingsSection("Settings") {
            SettingsRow(Icons.Outlined.Settings, "General") { onOpen(DuaDestination.General) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Speed, "Usage and limits") { onOpen(DuaDestination.UsageLimits) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.NotificationsNone, "Notifications", if (state.notifications) "Reply notifications on" else "Off") { onOpen(DuaDestination.Notifications) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Mic, "Voice", state.voice) { onOpen(DuaDestination.VoiceSettings) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Shield, "Safety") { onOpen(DuaDestination.Safety) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Security, "Security and login") { onOpen(DuaDestination.SecurityLogin) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Storage, "Storage") { onOpen(DuaDestination.Storage) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.History, "History and data") { onOpen(DuaDestination.HistoryData) }
        }
        // Voice actions live in Settings → Voice; keep the other existing destinations here.
        SettingsSection("More settings") {
            SettingsRow(Icons.Outlined.CloudSync, "Sync", "Chats and notes follow your account") { onOpen(DuaDestination.CloudSync) }
            if (state.imagine != null) {
                SettingsDivider()
                SettingsRow(Icons.Outlined.Brush, "Imagine", state.imagine) { onOpen(DuaDestination.Imagine) }
            }
        }
        SettingsSection("Setup") {
            SettingsRow(Icons.Outlined.Tune, "Feature setup", "Providers, web search, connectors and diagnostics") { onOpen(DuaDestination.FeatureSetup) }
        }
        SettingsSection("About") {
            SettingsRow(Icons.Outlined.Info, "About Dua AI", "Version ${state.version}") { about = true }
            if (state.email != null) {
                SettingsDivider()
                SettingsRow(Icons.AutoMirrored.Outlined.Logout, "Logout", titleColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    enabled = !state.accountBusy) { logout = true }
            }
        }
    }
    if (logout) androidx.compose.material3.AlertDialog(onDismissRequest = { logout = false }, title = { androidx.compose.material3.Text("Log out?") },
        text = { androidx.compose.material3.Text("Your chats and memories stay on the server. This phone only holds a cache.") },
        confirmButton = { androidx.compose.material3.TextButton(enabled = !state.accountBusy, onClick = { logout = false; onLogOut() }) { androidx.compose.material3.Text("Log out") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { logout = false }) { androidx.compose.material3.Text("Cancel") } })
    if (about) androidx.compose.material3.AlertDialog(onDismissRequest = { about = false }, title = { androidx.compose.material3.Text("About Dua AI") },
        text = { androidx.compose.material3.Text("Version ${state.version} · personal assistant") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { about = false }) { androidx.compose.material3.Text("Close") } })

}
