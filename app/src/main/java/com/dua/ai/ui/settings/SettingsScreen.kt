package com.dua.ai.ui.settings

import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.chat.DuaDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.BuildConfig
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.*
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
    /** Automatic cloud sync on this device ("On"/"Off"); null shows no value. */
    val sync: String? = null,
    val accent: String = "Purple",
    val accountBusy: Boolean = false,
    val accountNotice: String = "",
)

internal fun themeLabel(theme: String) = when (theme) { "light" -> "Light"; "system" -> "Match system"; else -> "Dark" }
internal fun fontLabel(font: String) = when (font) { "system" -> "Device font"; "serif" -> "Serif"; "monospace" -> "Monospace"; else -> "Söhne" }

/** Profile & Settings hub: approved groups plus preserved existing feature destinations. */
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    val preferences by viewModel.localSettings.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val sync by viewModel.automaticSyncState.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val state = SettingsUiState(
        displayName = account.user?.displayName.orEmpty(), accent = appearance.resolvedAccent(DuaTheme.colors.isDark).label, accountBusy = account.busy, accountNotice = account.notice,
        email = account.user?.email, theme = appearance.theme, font = preferences.font, haptic = preferences.haptic,
        memoryEnabled = preferences.memoryEnabled, notifications = preferences.notifications,
        sync = if (sync.enabled) "On" else "Off",
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

/**
 * Settings hub laid out exactly like the Dabba reference (header + three cards). Every existing destination is kept;
 * settings the reference has no slot for sit in the closest card.
 */
@Composable
fun SettingsContent(state: SettingsUiState, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit, onLogOut: () -> Unit = {}) {
    var logout by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    val c = SettingsTheme.colors
    val name = state.displayName.ifBlank { if (state.email == null) "Profile & Settings" else "Google account" }
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
        .padding(SettingsSizes.screenPadding).verticalScroll(rememberScrollState()).testTag("settings-screen")) {
        Row(Modifier.fillMaxWidth().padding(bottom = SettingsSizes.headerBottom), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(SettingsSizes.avatar).clickable(role = Role.Button, onClickLabel = "Edit profile photo") { onOpen(DuaDestination.AvatarEdit) }
                    .semantics { contentDescription = "Edit profile photo" }, contentAlignment = Alignment.BottomEnd) {
                    Box(Modifier.size(SettingsSizes.avatar).clip(SettingsShapes.circle).background(c.avatar), contentAlignment = Alignment.Center) {
                        // An account initial, never a fabricated photo.
                        val initial = state.displayName.firstOrNull { it.isLetterOrDigit() } ?: state.email?.firstOrNull { it.isLetterOrDigit() }
                        if (initial != null) Text(initial.uppercaseChar().toString(), style = SettingsAvatarStyle, color = c.onAvatar)
                        else Icon(Icons.Outlined.PersonOutline, null, tint = c.onAvatar)
                    }
                    Box(Modifier.size(SettingsSizes.editBadge).clip(SettingsShapes.circle).background(c.editBadge)
                        .border(SettingsSizes.border, c.circleBorder, SettingsShapes.circle), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(SettingsSizes.editIcon), tint = c.editIcon)
                    }
                }
                Spacer(Modifier.height(SettingsSizes.nameTop))
                Text(name, style = SettingsNameStyle, color = c.name, modifier = Modifier.semantics { heading() })
            }
            CircleButton(Icons.Outlined.Info, "About Dua AI") { about = true }
        }
        Spacer(Modifier.height(SettingsSizes.cardsTop))

        // CARD 1: Account
        SettingsCardBox {
            SettingsItemRow(Icons.Outlined.Person, "Account", state.email ?: "Not signed in") { onOpen(DuaDestination.Account) }
            SettingsItemRow(Icons.Outlined.WorkOutline, "Workspace") { onOpen(DuaDestination.Work) }
            SettingsItemRow(Icons.Outlined.CloudSync, "Sync", state.sync) { onOpen(DuaDestination.CloudSync) }
            if (state.email != null) SettingsItemRow(Icons.AutoMirrored.Outlined.Logout, "Logout", tint = c.danger, chevron = false,
                enabled = !state.accountBusy) { logout = true }
            Row(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch).clickable(role = Role.Button) { onOpen(DuaDestination.UsageLimits) }
                .padding(SettingsSizes.rowPadding), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = c.upgrade, modifier = Modifier.size(SettingsSizes.rowIcon))
                Text("Upgrade to Dua AI Pro", style = SettingsUpgradeStyle, color = c.upgrade)
            }
        }
        if (state.accountNotice.isNotBlank()) Text(state.accountNotice, style = SettingsValueStyle, color = c.value,
            modifier = Modifier.padding(top = SettingsSizes.rowGap).semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(SettingsSizes.cardGap))

        // CARD 2: Personalization
        SettingsCardBox {
            SettingsItemRow(Icons.Outlined.SentimentSatisfied, "Personalization", "${themeLabel(state.theme)} · ${fontLabel(state.font)}") { onOpen(DuaDestination.Appearance) }
            SettingsItemRow(Icons.AutoMirrored.Outlined.MenuBook, "Memory", if (state.memoryEnabled) "On" else "Off") { onOpen(DuaDestination.Memory) }
            SettingsItemRow(Icons.Outlined.Extension, "Plugins") { onOpen(DuaDestination.Plugins) }
            if (state.imagine != null) SettingsItemRow(Icons.Outlined.Brush, "Imagine", state.imagine) { onOpen(DuaDestination.Imagine) }
            Row(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch)
                .clickable(role = Role.Button, onClickLabel = "Change accent color") { onOpen(DuaDestination.Accent) }
                .padding(SettingsSizes.rowPadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = c.rowIcon, modifier = Modifier.size(SettingsSizes.rowIcon))
                    Text("Accent color", style = SettingsRowStyle, color = c.rowText)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SettingsSizes.accentGap)) {
                    Box(Modifier.size(SettingsSizes.accentDot).clip(SettingsShapes.circle).background(LocalDuaAccent.current.color))
                    Text(state.accent, style = SettingsValueStyle, color = c.accentValue)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.arrow, modifier = Modifier.size(SettingsSizes.arrow))
                }
            }
        }
        Spacer(Modifier.height(SettingsSizes.cardGap))

        // CARD 3: General
        SettingsCardBox {
            SettingsItemRow(Icons.Outlined.Settings, "General") { onOpen(DuaDestination.General) }
            SettingsItemRow(Icons.Outlined.Tune, "Feature setup", "Providers & connectors") { onOpen(DuaDestination.FeatureSetup) }
            SettingsItemRow(Icons.AutoMirrored.Outlined.TrendingUp, "Usage and limits") { onOpen(DuaDestination.UsageLimits) }
            SettingsItemRow(Icons.Outlined.Notifications, "Notifications", if (state.notifications) "On" else "Off") { onOpen(DuaDestination.Notifications) }
            SettingsItemRow(Icons.Outlined.GraphicEq, "Voice", state.voice) { onOpen(DuaDestination.VoiceSettings) }
            SettingsItemRow(Icons.Outlined.Shield, "Safety") { onOpen(DuaDestination.Safety) }
            SettingsItemRow(Icons.Outlined.Lock, "Security and login") { onOpen(DuaDestination.SecurityLogin) }
            SettingsItemRow(Icons.Outlined.Storage, "Storage") { onOpen(DuaDestination.Storage) }
            SettingsItemRow(Icons.Outlined.Search, "Search settings") { onOpen(DuaDestination.SettingsSearch) }
            SettingsItemRow(Icons.Outlined.Archive, "History and data", showDivider = false) { onOpen(DuaDestination.HistoryData) }
        }
    }
    if (logout) DuaDialog(onDismissRequest = { logout = false }, title = "Log out?",
        text = { Text(style = SheetBodyStyle, text = "Your chats and memories stay on the server. This phone only holds a cache.") },
        confirmButton = { SheetTextButton("Log out", onClick = { logout = false; onLogOut() }, enabled = !state.accountBusy, destructive = true) },
        dismissButton = { SheetTextButton("Cancel", onClick = { logout = false }) })
    if (about) DuaDialog(onDismissRequest = { about = false }, title = "About Dua AI",
        text = { Text(style = SheetBodyStyle, text = "Version ${state.version} · personal assistant") },
        confirmButton = { SheetTextButton("Close", onClick = { about = false }) })
}

@Composable
private fun CircleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = SettingsTheme.colors
    IconButton(onClick = onClick, modifier = Modifier.size(SettingsSizes.touch)) {
        Box(Modifier.size(SettingsSizes.circleButton).clip(SettingsShapes.circle).background(c.circleButton)
            .border(SettingsSizes.border, c.circleBorder, SettingsShapes.circle), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = c.circleIcon)
        }
    }
}

@Composable
fun SettingsCardBox(content: @Composable ColumnScope.() -> Unit) {
    val c = SettingsTheme.colors
    Column(Modifier.fillMaxWidth().clip(SettingsShapes.card).background(c.card).border(SettingsSizes.border, c.cardBorder, SettingsShapes.card), content = content)
}

/** Reference row: icon, label, optional right-hand value and chevron. Full-width 48 dp+ touch target. */
@Composable
fun SettingsItemRow(icon: ImageVector, label: String, value: String? = null, showDivider: Boolean = true, tint: Color = Color.Unspecified,
    chevron: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val c = SettingsTheme.colors
    val color = if (tint == Color.Unspecified) c.rowText else tint
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(SettingsSizes.rowPadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (tint == Color.Unspecified) c.rowIcon else tint, modifier = Modifier.size(SettingsSizes.rowIcon))
                Text(label, style = SettingsRowStyle, color = color, maxLines = 1)
            }
            Row(Modifier.weight(1f, fill = false).padding(start = SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsSizes.valueGap)) {
                if (!value.isNullOrBlank()) Text(value, Modifier.weight(1f, fill = false), style = SettingsValueStyle, color = c.value,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chevron) Icon(Icons.Filled.ChevronRight, null, tint = c.chevron, modifier = Modifier.size(SettingsSizes.chevron))
            }
        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = SettingsSizes.dividerStart), thickness = SettingsSizes.border, color = c.divider)
    }
}
