package com.dua.ai.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.components.SettingsChoiceRow
import com.dua.ai.ui.components.SettingsGroup
import com.dua.ai.ui.components.SettingsRowDivider
import com.dua.ai.ui.components.SettingsToggleRow
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.voice.offline.ModelStatus
import com.dua.ai.voice.offline.OfflineModelManager
import com.dua.ai.voice.offline.OfflineModels

/** Settings › Voice › Offline voice typing, wired to the shared model manager and preferences. */
@Composable
fun OfflineVoiceTypingSection(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val manager = androidx.compose.runtime.remember(context) { OfflineModelManager.get(context) }
    val status by manager.status.collectAsState()
    val preferences by viewModel.localSettings.collectAsState()
    OfflineVoiceTypingGroup(preferences.offlineDictation, preferences.offlineModel, status,
        onMode = { viewModel.updateSettings(viewModel.localSettings.value.copy(offlineDictation = it)) },
        onModel = { viewModel.updateSettings(viewModel.localSettings.value.copy(offlineModel = it)) },
        onDownload = manager::download, onCancel = manager::cancel, onDelete = manager::delete)
}

@Composable
fun OfflineVoiceTypingGroup(mode: String, selected: String, status: Map<String, ModelStatus>, onMode: (String) -> Unit,
    onModel: (String) -> Unit, onDownload: (String) -> Unit, onCancel: (String) -> Unit, onDelete: (String) -> Unit) {
    val enabled = mode != "off"
    SettingsGroup("Offline voice typing") {
        SettingsToggleRow(null, "Offline voice typing", "Type by voice in chat without internet. Needs a one-time download.",
            checked = enabled, onCheckedChange = { onMode(if (it) "fallback" else "off") })
        if (enabled) {
            SettingsRowDivider()
            SettingsToggleRow(null, "Always use offline", "Off = only when your phone has no speech service",
                checked = mode == "always", onCheckedChange = { onMode(if (it) "always" else "fallback") })
            OfflineModels.catalog.forEach { model ->
                SettingsRowDivider()
                val state = status[model.id] ?: ModelStatus.NotInstalled
                Column(Modifier.fillMaxWidth().testTag("offline-model-${model.id}")) {
                    SettingsChoiceRow(model.label, model.id == selected, when (state) {
                        ModelStatus.Installed -> "Downloaded · used for offline typing"
                        is ModelStatus.Downloading -> "Downloading… ${(state.fraction * 100).toInt()}% of ${model.sizeText}"
                        ModelStatus.Installing -> "Checking and unpacking…"
                        is ModelStatus.Failed -> state.message
                        ModelStatus.NotInstalled -> "${model.sizeText} · download on Wi-Fi"
                    }, trailing = {
                        when (state) {
                            ModelStatus.Installed -> TextButton(onClick = { onDelete(model.id) }, modifier = Modifier.semantics { contentDescription = "Delete ${model.label} model" }) { Text("Delete") }
                            is ModelStatus.Downloading -> TextButton(onClick = { onCancel(model.id) }, modifier = Modifier.semantics { contentDescription = "Cancel ${model.label} download" }) { Text("Cancel") }
                            ModelStatus.Installing -> {}
                            else -> TextButton(onClick = { onModel(model.id); onDownload(model.id) }, modifier = Modifier.semantics { contentDescription = "Download ${model.label} model" }) { Text("Download") }
                        }
                    }) { onModel(model.id) }
                    if (state is ModelStatus.Downloading) LinearProgressIndicator(progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs))
                }
            }
        }
    }
    if (enabled) Text("Offline voice typing uses free Vosk models from alphacephei.com. Speech stays on your phone. No Urdu model is available yet.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
