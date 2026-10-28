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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.components.SettingsGroup
import com.dua.ai.ui.components.SettingsCard
import com.dua.ai.ui.components.settingsValueFitsInline
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
    val avatarUrl: String = "",
    val workspace: String = "Personal",
    val personality: String? = null,
    val notificationStatus: String? = null,
)

internal fun themeLabel(theme: String) = when (theme) { "light" -> "Light"; "system" -> "Match system"; else -> "Dark" }
internal fun fontLabel(font: String) = when (font) { "system" -> "Device font"; "serif" -> "Serif"; "monospace" -> "Monospace"; else -> "Inter" }

/** Settings hub: account operations live in the dedicated Account destination. */
@Composable
fun SettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    val preferences by viewModel.localSettings.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val sync by viewModel.automaticSyncState.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val notifications = rememberDeviceNotifications()
    val state = SettingsUiState(
        displayName = account.user?.displayName.orEmpty(), accent = appearance.resolvedAccent(DuaTheme.colors.isDark).label, accountBusy = account.busy, accountNotice = account.notice,
        avatarUrl = account.user?.avatarUrl.orEmpty(), email = account.user?.email, theme = appearance.theme, font = preferences.font, haptic = preferences.haptic,
        memoryEnabled = preferences.memoryEnabled, notifications = preferences.notifications, personality = appearance.personality?.label,
        notificationStatus = notificationSummary(preferences.notifications, notifications),
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
    SettingsContent(state, onBack, onOpen)
}

/**
 * Grouped Settings with a compact header; existing feature destinations remain reachable.
 */
@Composable
fun SettingsContent(state: SettingsUiState, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    var about by rememberSaveable { mutableStateOf(false) }
    val c = SettingsTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
        .padding(SettingsSizes.screenPadding).verticalScroll(rememberScrollState()).testTag("settings-screen")) {
        Row(Modifier.fillMaxWidth().padding(bottom = SettingsSizes.headerBottom), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(SettingsSizes.touch)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.rowText)
            }
            Text("Settings", style = HomeHeaderStyle, color = c.name, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = { about = true }, modifier = Modifier.size(SettingsSizes.touch)) {
                Icon(ReferenceIcons.Info, "About Dua AI", tint = c.rowText)
            }
        }
        Spacer(Modifier.height(SettingsSizes.cardsTop))
        SettingsGroup("Your account") {
            SettingsItemRow(ReferenceIcons.Account, "Account", state.email ?: "Not signed in") { onOpen(DuaDestination.Account) }
            SettingsItemRow(Icons.Outlined.AccountCircle, "Edit profile photo", state.displayName.takeIf { it.isNotBlank() },
                onClickLabel = "Edit profile photo") { onOpen(DuaDestination.AvatarEdit) }
            SettingsItemRow(ReferenceIcons.Work, "Workspace", state.workspace, showDivider = false) { onOpen(DuaDestination.Work) }
        }
        if (state.accountNotice.isNotBlank()) Text(state.accountNotice, style = SettingsValueStyle, color = c.value,
            modifier = Modifier.padding(top = SettingsSizes.rowGap).semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(SettingsSizes.cardGap))

        // Preferences share the existing settings owners and persistence.
        SettingsGroup("Preferences") {
            SettingsItemRow(Icons.Outlined.SentimentSatisfied, "Personalization", listOfNotNull("${themeLabel(state.theme)} · ${fontLabel(state.font)}", state.personality).joinToString(" · ")) { onOpen(DuaDestination.Appearance) }
            SettingsItemRow(Icons.AutoMirrored.Outlined.MenuBook, "Memory", if (state.memoryEnabled) "On" else "Off") { onOpen(DuaDestination.Memory) }
            SettingsItemRow(Icons.Outlined.Extension, "Plugins") { onOpen(DuaDestination.Plugins) }
            if (state.imagine != null) SettingsItemRow(Icons.Outlined.Brush, "Imagine", state.imagine) { onOpen(DuaDestination.Imagine) }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val inline = settingsValueFitsInline(maxWidth - SettingsSizes.accentDot - SettingsSizes.accentGap, "Accent color", state.accent, icon = true, chevron = true)
                Row(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch)
                    .clickable(role = Role.Button, onClickLabel = "Change accent color") { onOpen(DuaDestination.Accent) }
                    .padding(SettingsSizes.rowPadding), horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = c.rowIcon, modifier = Modifier.size(SettingsSizes.rowIcon))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Accent color", style = SettingsRowStyle, color = c.rowText)
                        if (!inline) AccentValue(state.accent)
                    }
                    if (inline) AccentValue(state.accent)
                }
            }
        }
        Spacer(Modifier.height(SettingsSizes.cardGap))

        SettingsGroup("General and privacy") {
            SettingsItemRow(Icons.Outlined.Settings, "General") { onOpen(DuaDestination.General) }
            SettingsItemRow(Icons.Outlined.Tune, "Feature setup", "Providers & connectors") { onOpen(DuaDestination.FeatureSetup) }
            SettingsItemRow(Icons.AutoMirrored.Outlined.TrendingUp, "Usage and limits") { onOpen(DuaDestination.UsageLimits) }
            SettingsItemRow(Icons.Outlined.Notifications, "Notifications", state.notificationStatus ?: if (state.notifications) "On" else "Off") { onOpen(DuaDestination.Notifications) }
            SettingsItemRow(Icons.Outlined.GraphicEq, "Voice", state.voice) { onOpen(DuaDestination.VoiceSettings) }
            SettingsItemRow(Icons.Outlined.Shield, "Safety") { onOpen(DuaDestination.Safety) }
            SettingsItemRow(Icons.Outlined.Lock, "Security and login") { onOpen(DuaDestination.SecurityLogin) }
            SettingsItemRow(Icons.Outlined.Storage, "Storage") { onOpen(DuaDestination.Storage) }
            SettingsItemRow(Icons.Outlined.Search, "Search settings") { onOpen(DuaDestination.SettingsSearch) }
            SettingsItemRow(Icons.Outlined.Archive, "History and data", showDivider = false) { onOpen(DuaDestination.HistoryData) }
        }
    }
    if (about) DuaDialog(onDismissRequest = { about = false }, title = "About Dua AI",
        text = { Text(style = SheetBodyStyle, text = "Version ${state.version} · personal assistant") },
        confirmButton = { SheetTextButton("Close", onClick = { about = false }) })
}

@Composable
private fun AccentValue(accent: String) {
    val c = SettingsTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SettingsSizes.accentGap)) {
        Box(Modifier.size(SettingsSizes.accentDot).clip(SettingsShapes.circle).background(LocalDuaAccent.current.color))
        Text(accent, style = SettingsValueStyle, color = c.accentValue)
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.arrow, modifier = Modifier.size(SettingsSizes.arrow))
    }
}

@Composable
internal fun CircleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = SettingsTheme.colors
    IconButton(onClick = onClick, modifier = Modifier.size(SettingsSizes.touch)) {
        Box(Modifier.size(SettingsSizes.circleButton).clip(SettingsShapes.circle).background(c.circleButton)
            .border(SettingsSizes.border, c.circleBorder, SettingsShapes.circle), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = c.circleIcon)
        }
    }
}

@Composable
fun SettingsCardBox(content: @Composable ColumnScope.() -> Unit) = SettingsCard(content = content)

/** Reference row: icon, label, optional right-hand value and chevron. Full-width 48 dp+ touch target. */
@Composable
fun SettingsItemRow(icon: ImageVector, label: String, value: String? = null, showDivider: Boolean = true, tint: Color = Color.Unspecified,
    chevron: Boolean = true, enabled: Boolean = true, onClickLabel: String = label, onClick: () -> Unit) {
    val c = SettingsTheme.colors
    val color = if (tint == Color.Unspecified) c.rowText else tint
    BoxWithConstraints {
        val inline = settingsValueFitsInline(maxWidth, label, value, icon = true, chevron = chevron)
        var taps by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = SettingsSizes.touch).clickable(enabled = enabled, role = Role.Button, onClickLabel = onClickLabel, onClick = { taps++; onClick() })
                .padding(SettingsSizes.rowPadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically) {
                    val iconTint = if (tint == Color.Unspecified) c.rowIcon else tint
                    val lottie = com.dua.ai.ui.components.AnimatedAssets.settingsIcons[label]
                    if (lottie != null) com.dua.ai.ui.components.LottieIconOrFallback(lottie, icon, iconTint, SettingsSizes.rowIcon, playKey = taps)
                    else Icon(icon, null, tint = iconTint, modifier = Modifier.size(SettingsSizes.rowIcon))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(label, style = SettingsRowStyle, color = color)
                        if (!inline && !value.isNullOrBlank()) Text(value, style = SettingsValueStyle, color = c.value)
                    }
                }
                if ((inline && !value.isNullOrBlank()) || chevron) Row(Modifier.padding(start = SettingsSizes.rowGap), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SettingsSizes.valueGap)) {
                    if (inline && !value.isNullOrBlank()) Text(value, style = SettingsValueStyle, color = c.value, maxLines = 1)
                    if (chevron) Icon(Icons.Filled.ChevronRight, null, tint = c.chevron, modifier = Modifier.size(SettingsSizes.chevron))
                }
            }
            if (showDivider) HorizontalDivider(thickness = SettingsSizes.border, color = c.divider)
        }
    }
}
