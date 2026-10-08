package com.dua.ai.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.dua.ai.BuildConfig
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.components.SettingsDivider
import com.dua.ai.ui.components.SettingsRow
import com.dua.ai.ui.components.SettingsSection
import com.dua.ai.ui.components.SettingsSwitchRow
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
    val generatedVoice: Boolean = false,
    val generatedVoiceConfigured: Boolean = false,
    val busy: Boolean = false,
    val version: String = BuildConfig.VERSION_NAME,
    /** Imagine row subtitle; null hides the row (backend without Imagine). */
    val imagine: String? = null,
    /** Voice settings row subtitle; null keeps the single AI-voice switch (backend before Voice v2). */
    val voice: String? = null,
    /** Live voice (xAI realtime) is available: the hands-free row opens it. */
    val liveVoice: Boolean = false,
) {
    val generatedVoiceToggleEnabled: Boolean get() = !busy && (generatedVoice || (generatedVoiceConfigured && email != null))
}

internal fun themeLabel(theme: String) = when (theme) { "light" -> "Light"; "system" -> "Match system"; else -> "Dark" }
internal fun fontLabel(font: String) = when (font) { "system" -> "Device font"; "serif" -> "Serif"; "monospace" -> "Monospace"; else -> "Inter" }

/** Settings home: five groups, one entry per page. */
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    val preferences by viewModel.localSettings.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val haptics = LocalHapticFeedback.current
    val state = SettingsUiState(
        email = account.user?.email, theme = preferences.theme, font = preferences.font, haptic = preferences.haptic,
        memoryEnabled = preferences.memoryEnabled, notifications = preferences.notifications,
        generatedVoice = preferences.generatedVoice, generatedVoiceConfigured = integrations.generatedVoiceConfigured,
        busy = integrations.busy,
        liveVoice = integrations.voice?.live?.enabled == true && account.user != null,
        voice = integrations.voice?.let { config ->
            val options = if (preferences.generatedVoice && account.user != null) com.dua.ai.voice.VoiceCodec.ttsOptions(config, preferences.voiceProvider,
                preferences.voiceName, preferences.replyLanguage, preferences.voiceSpeed) else null
            val provider = options?.let { config.provider(it.provider) }
            if (provider == null || options == null) "Device voice · " + com.dua.ai.voice.SpeechLanguages.name(preferences.replyLanguage)
            else listOf(com.dua.ai.ui.voice.providerLabel(provider), provider.voice(options.voice).name, provider.language(options.language).name).joinToString(" · ")
        },
        imagine = integrations.imagine?.let { config ->
            if (!config.enabled) "Off on the server" else listOfNotNull(config.providerName(preferences.imagineProvider.ifBlank { null }),
                com.dua.ai.imagine.ImagineCodec.aspectLabel(preferences.imagineAspect), if (config.videoEnabled) "video on" else null).joinToString(" · ")
        },
    )
    SettingsContent(state, onBack, onOpen,
        onHaptic = { enabled ->
            viewModel.updateSettings(viewModel.localSettings.value.copy(haptic = enabled))
            if (enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onGeneratedVoice = { enabled -> viewModel.updateSettings(viewModel.localSettings.value.copy(generatedVoice = enabled)) })
}

@Composable
fun SettingsContent(state: SettingsUiState, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit,
    onHaptic: (Boolean) -> Unit, onGeneratedVoice: (Boolean) -> Unit) {
    DuaPageScaffold(title = "Settings", onBack = onBack, backLabel = "Back to chat") {
        SettingsSection("Account") {
            SettingsRow(Icons.Outlined.PersonOutline, "Profile", state.email ?: "Not signed in · optional") { onOpen(DuaDestination.Profile) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.CloudSync, "Cloud sync", "Back up chats and notes across your devices") { onOpen(DuaDestination.CloudSync) }
            SettingsDivider()
            SettingsRow(Icons.AutoMirrored.Outlined.Logout, "Log out", titleColor = MaterialTheme.colorScheme.error) { onOpen(DuaDestination.Logout) }
        }
        SettingsSection("Personalization") {
            SettingsRow(Icons.Outlined.Palette, "Appearance", "${themeLabel(state.theme)} · ${fontLabel(state.font)}") { onOpen(DuaDestination.Appearance) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Psychology, "Memory", if (state.memoryEnabled) "On · stored on this device" else "Off · stored on this device") { onOpen(DuaDestination.Memory) }
            SettingsDivider()
            SettingsSwitchRow(Icons.Outlined.Vibration, "Haptic feedback", checked = state.haptic, onCheckedChange = onHaptic)
            if (state.imagine != null) {
                SettingsDivider()
                SettingsRow(Icons.Outlined.Brush, "Imagine", state.imagine) { onOpen(DuaDestination.Imagine) }
            }
        }
        SettingsSection("Voice") {
            SettingsRow(Icons.Outlined.Mic, "Voice input", "Dictate, review, then send") { onOpen(DuaDestination.Voice) }
            SettingsDivider()
            if (state.liveVoice) SettingsRow(Icons.Outlined.GraphicEq, "Live conversation", "Talk with Grok in real time · interrupt any time") { onOpen(DuaDestination.LiveVoice) }
            else SettingsRow(Icons.Outlined.GraphicEq, "Hands-free conversation", "Speak, then hear the reply read aloud") { onOpen(DuaDestination.HandsFree) }
            SettingsDivider()
            if (state.voice != null) SettingsRow(Icons.AutoMirrored.Outlined.VolumeUp, "Reply voice & language", state.voice) { onOpen(DuaDestination.VoiceSettings) }
            else SettingsSwitchRow(Icons.AutoMirrored.Outlined.VolumeUp, "AI reply voice",
                subtitle = when {
                    !state.generatedVoiceConfigured && !state.generatedVoice -> "Needs backend setup · device voice stays available"
                    state.email == null && !state.generatedVoice -> "Sign in to use a generated voice"
                    else -> "Generates audio only for the reply you tap"
                },
                checked = state.generatedVoice, enabled = state.generatedVoiceToggleEnabled, onCheckedChange = onGeneratedVoice)
        }
        SettingsSection("Data & privacy") {
            SettingsRow(Icons.Outlined.Storage, "Data controls", "Export, import or clear chats and uploaded files") { onOpen(DuaDestination.DataControls) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Shield, "Privacy", "What stays on this device and what is sent") { onOpen(DuaDestination.Privacy) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.IosShare, "Sharing", "Share a conversation as text") { onOpen(DuaDestination.Sharing) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Security, "Permissions", "Microphone, camera and files") { onOpen(DuaDestination.Permissions) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.NotificationsNone, "Notifications", if (state.notifications) "Reply notifications on" else "Off") { onOpen(DuaDestination.Notifications) }
        }
        SettingsSection("About") {
            SettingsRow(Icons.Outlined.Tune, "Feature setup", "Providers, web search, connectors and diagnostics") { onOpen(DuaDestination.FeatureSetup) }
            SettingsDivider()
            SettingsRow(Icons.Outlined.Info, "Dua AI", "Version ${state.version} · personal build")
        }
    }
}
