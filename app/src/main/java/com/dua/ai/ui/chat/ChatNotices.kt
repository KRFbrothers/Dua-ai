package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Read-aloud status and inline error card shown just above the composer. */
@Composable
fun ChatNotices(state: ChatUiState, actions: ChatActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (state.deviceVoiceActive || state.deviceVoiceStatus.isNotBlank()) StatusLine(
            text = state.deviceVoiceStatus, actionLabel = if (state.deviceVoiceActive) "Stop" else null, onAction = actions.onStopSpeech)
        if (state.generatedVoice || state.generatedVoiceStatus.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.generatedVoiceStatus.ifBlank { "AI voice selected · sends only the reply you tap" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            if (state.generatedVoiceActive) TextButton(onClick = actions.onStopSpeech) { Text("Stop") }
            TextButton(onClick = actions.onUseDeviceVoice) { Text("Use device voice") }
        }
        state.error?.let { message ->
            val needsSetup = message.contains("setup", ignoreCase = true)
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = DuaShape.card, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(Sizes.iconSmall))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = Spacing.md, vertical = Spacing.sm).semantics { liveRegion = LiveRegionMode.Polite })
                    if (needsSetup) TextButton(onClick = actions.onOpenSetup) { Text("Setup", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, actionLabel: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
