package com.dua.ai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dua.ai.imagine.ImagineCodec
import com.dua.ai.imagine.ImagineConfig
import com.dua.ai.imagine.ImagineUsage
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.components.SettingsDivider
import com.dua.ai.ui.components.SettingsSection
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.viewmodel.ChatViewModel

/** Settings › Imagine: default image model and aspect ratio, and today's usage against the server's caps. */
@Composable
fun ImagineSettingsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val settings by viewModel.localSettings.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val usage by viewModel.imagineUsage.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val status by viewModel.settingsStatus.collectAsState()
    LaunchedEffect(account.user?.id, integrations.imagine != null) { viewModel.loadImagineUsage() }
    ImagineSettingsContent(config = integrations.imagine, usage = usage, signedIn = account.user != null,
        provider = settings.imagineProvider, aspect = settings.imagineAspect, status = status, onBack = onBack,
        onProvider = { viewModel.updateSettings(viewModel.localSettings.value.copy(imagineProvider = it)) },
        onAspect = { viewModel.updateSettings(viewModel.localSettings.value.copy(imagineAspect = it)) })
}

@Composable
fun ImagineSettingsContent(config: ImagineConfig?, usage: ImagineUsage?, signedIn: Boolean, provider: String, aspect: String, status: String,
    onBack: () -> Unit, onProvider: (String) -> Unit, onAspect: (String) -> Unit) {
    DuaPageScaffold(title = "Imagine", onBack = onBack) {
        Text(when {
            config == null -> "Imagine needs the latest Dua backend. Deploy it and run the Imagine migration (see SUPABASE.md)."
            !signedIn -> "Sign in from Profile to create images and videos. Results are saved to your private storage."
            !config.enabled -> config.imageReason.ifBlank { config.videoReason }
            else -> "Create images and short videos from the composer's Imagine chip. Attach a photo to edit it or turn it into a video."
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (config != null) {
            SettingsSection("Today") {
                UsageRow("Images", usage?.images, usage?.imageLimit ?: config.dailyImages, config.imageEnabled, config.imageReason)
                UsageRow("Video seconds", usage?.videoSeconds, usage?.videoLimit ?: config.dailyVideoSeconds, config.videoEnabled, config.videoReason)
                Text(usage?.let { "Limits reset at midnight UTC (" + resetLabel(it.resetAt) + " here). Set them with DUA_IMAGINE_DAILY_IMAGES and DUA_IMAGINE_DAILY_VIDEO_SECONDS." }
                    ?: "Usage loads when you are signed in.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md))
            }
            if (config.providers.size > 1) SettingsSection("Default image model") {
                Choice("Server default (${config.providerName(null)})", provider == "") { onProvider("") }
                config.providers.forEach { item -> Choice("${item.name} · ${item.model}", provider == item.id) { onProvider(item.id) } }
            } else if (config.providers.size == 1) SettingsSection("Image model") {
                Text("${config.providers.first().name} · ${config.providers.first().model}", style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg))
            }
            SettingsSection("Default aspect ratio") {
                config.imageAspects.forEach { value -> Choice(if (value == "auto") "Auto (model decides)" else ImagineCodec.aspectLabel(value), aspect == value) { onAspect(value) } }
            }
            if (config.videoEnabled) SettingsSection("Video") {
                Text("${config.videoModel.ifBlank { "Grok Imagine" }} · up to ${config.durations.maxOrNull() ?: 15} s · ${config.resolutions.joinToString("/")}",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg))
                Text("Videos keep generating if you leave the chat or close the app; Dua checks again when it opens. Cancel stops waiting, but the provider may still count the job.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.lg))
            }
        }
        Text("Generated files are stored in your private Supabase bucket, not on the provider. Deleting a chat deletes its files; Data controls can delete all of them.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun resetLabel(epochSeconds: Long): String = if (epochSeconds <= 0) "—" else
    java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withSecond(0).toString()

@Composable
private fun UsageRow(label: String, used: Int?, limit: Int, enabled: Boolean, reason: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (!enabled) "Off" else "${used ?: "–"} of $limit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled && limit > 0) LinearProgressIndicator(progress = { ((used ?: 0).toFloat() / limit).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
        if (!enabled && reason.isNotBlank()) Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.lg))
    }
}
