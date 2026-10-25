package com.dua.ai.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.IntegrationState
import com.dua.ai.ui.components.*
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.viewmodel.ChatViewModel

@Composable
internal fun GeneralScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    val settings by viewModel.localSettings.collectAsState()
    val status by viewModel.settingsStatus.collectAsState()
    val busy by viewModel.dataBusy.collectAsState()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val onDevice by viewModel.onDeviceAi.status.collectAsState()
    GeneralContent(settings, status, !busy, onBack, onOpen, onHaptic = { value ->
        viewModel.updateSettings(viewModel.localSettings.value.copy(haptic = value))
        if (value && viewModel.localSettings.value.haptic) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }, onDeviceSupported = onDevice?.let { it != com.dua.ai.ondevice.OnDeviceStatus.Unsupported },
        onOnDeviceAi = { value -> viewModel.updateSettings(viewModel.localSettings.value.copy(onDeviceAi = value)) })
}

@Composable
internal fun GeneralContent(settings: LocalPreferences, status: String, enabled: Boolean, onBack: () -> Unit,
    onOpen: (DuaDestination) -> Unit, onHaptic: (Boolean) -> Unit,
    /** null while checking; false shows the switch off and disabled. */
    onDeviceSupported: Boolean? = null, onOnDeviceAi: (Boolean) -> Unit = {}) {
    DuaPageScaffold("General", onBack) {
        SettingsGroup("Display and interaction") {
            SettingsNavRow(Icons.Outlined.Palette, "Appearance and font", value = fontLabel(settings.font)) { onOpen(DuaDestination.Appearance) }
            SettingsRowDivider()
            SettingsToggleRow(Icons.Outlined.Vibration, "Haptic feedback", "A small vibration for supported actions.", settings.haptic, enabled, onHaptic)
        }
        SettingsGroup("Device preferences") {
            SettingsNavRow(Icons.Outlined.Notifications, "Notifications", "Reply alerts and task reminders") { onOpen(DuaDestination.Notifications) }
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.GraphicEq, "Voice and language", "Dictation, reply voice and live voice") { onOpen(DuaDestination.VoiceSettings) }
            SettingsRowDivider()
            SettingsToggleRow(Icons.Outlined.PhoneAndroid, "On-device AI", when (onDeviceSupported) {
                true -> "Chat titles and Imagine prompt polish run on this phone. Off uses the Dua server."
                false -> "Not available on this phone. Dua uses the server instead."
                null -> "Checking this phone…"
            }, onDeviceSupported == true && settings.onDeviceAi, enabled && onDeviceSupported == true, onOnDeviceAi)
        }
        PageNote("Text size follows your phone's font size setting. Voice language is configured separately from the app interface.")
        if (status.isNotBlank()) PageStatus(status)
    }
}

/** No duplicate login or invented security switches: reuse the account's existing guarded actions. */
@Composable
internal fun SecurityLoginContent(email: String?, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit, onPermissions: () -> Unit, status: String = "") {
    DuaPageScaffold("Security and login", onBack) {
        SettingsGroup("Account access") {
            SettingsNavRow(Icons.Outlined.AccountCircle, if (email == null) "Sign in" else "Manage account",
                subtitle = email ?: "Google or email sign-in", value = null) { onOpen(DuaDestination.Account) }
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.CloudSync, "Cloud sync", "Review account data and sync status") { onOpen(DuaDestination.CloudSync) }
        }
        SettingsGroup("On this phone") {
            SettingsNavRow(Icons.Outlined.Security, "Android app permissions", "Review microphone, camera and notification access", onClick = onPermissions)
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.Storage, "History and data", "Export, import or clear cached chats") { onOpen(DuaDestination.HistoryData) }
        }
        PageNote("Password setup, sign-out and account deletion are managed in Account. Destructive account actions ask for confirmation.")
        if (status.isNotBlank()) PageStatus(status)
    }
}

@Composable
internal fun SafetyContent(memoryEnabled: Boolean, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit, onPermissions: () -> Unit, status: String = "") {
    DuaPageScaffold("Safety", onBack) {
        PagePaddedCard {
            PageCardTitle("Review before acting")
            PageBody("Dua AI can make mistakes. Check important answers and review code proposals before approving a pull request.")
        }
        SettingsGroup("Context and connected tools") {
            SettingsNavRow(Icons.Outlined.Bookmarks, "Memory", "Review or delete Personal notes", if (memoryEnabled) "On" else "Off") { onOpen(DuaDestination.Memory) }
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.Extension, "Plugins", "Review connected capabilities and setup") { onOpen(DuaDestination.Plugins) }
        }
        SettingsGroup("Data and device access") {
            SettingsNavRow(Icons.Outlined.Security, "Android app permissions", "Manage microphone, camera and notification access", onClick = onPermissions)
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.Storage, "Data controls", "Export chats or remove cached chats and uploaded files") { onOpen(DuaDestination.DataControls) }
        }
        PageNote("Turning off Personal memory keeps its notes. Review cloud sync before changing the account's stored copy.")
        if (status.isNotBlank()) PageStatus(status)
    }
}

@Composable
internal fun UsageLimitsContent(integrations: IntegrationState, signedIn: Boolean, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    DuaPageScaffold("Usage and limits", onBack) {
        PageNote("Usage is reported by each connected service. Open a category to view its current allowance and usage.")
        SettingsGroup("Services") {
            SettingsNavRow(Icons.Outlined.Brush, "Images and video", "Daily generation counts and server limits",
                value = if (integrations.imagine?.enabled == true) "Available" else "Not configured") { onOpen(DuaDestination.Imagine) }
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.GraphicEq, "Live voice", "Daily minutes and session limits",
                value = if (integrations.voice?.live?.enabled == true) "Available" else "Not configured") { onOpen(DuaDestination.VoiceSettings) }
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.Storage, "Uploaded files", "Private storage usage and per-file limits",
                value = if (integrations.filesEnabled) "Available" else "Not configured") { onOpen(DuaDestination.DataControls) }
        }
        if (!signedIn) SettingsGroup(null) {
            SettingsNavRow(Icons.Outlined.AccountCircle, "Sign in", "Required to load private usage") { onOpen(DuaDestination.Account) }
        }
        PagePaddedCard {
            PageCardTitle("Chat usage")
            PageBody("The connected chat backend does not report an overall token count or cost total to the app. Provider limits and charges still apply.")
        }
        SettingsGroup(null) {
            SettingsNavRow(Icons.Outlined.Tune, "Feature setup", "Check providers and connected services") { onOpen(DuaDestination.FeatureSetup) }
        }
    }
}
