package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Record a voice note or pick an audio file. Audio is transcribed first, then answered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSheet(recordingSince: Long?, available: Boolean, hint: String, onRecord: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit, onPick: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { if (recordingSince == null) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { recordingSince == null }),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        AudioSheetContent(recordingSince, available, hint, onRecord, onStop, onCancel, onPick)
    }
}

@Composable
fun AudioSheetContent(recordingSince: Long?, available: Boolean, hint: String, onRecord: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit, onPick: () -> Unit,
    now: () -> Long = System::currentTimeMillis) {
    var tick by remember { mutableLongStateOf(now()) }
    LaunchedEffect(recordingSince) { while (recordingSince != null) { tick = now(); delay(500) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text("Audio", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (!available) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (recordingSince != null) {
            val seconds = ((tick - recordingSince).coerceAtLeast(0) / 1000).toInt()
            Text("Recording… %d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(onClick = onStop, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Icon(Icons.Outlined.Stop, null); Text("Stop and attach", Modifier.padding(start = Spacing.sm)) }
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 56.dp)) { Text("Discard") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(onClick = onRecord, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Icon(Icons.Outlined.Mic, null); Text("Record", Modifier.padding(start = Spacing.sm)) }
                FilledTonalButton(onClick = onPick, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Icon(Icons.Outlined.AudioFile, null); Text("Choose file", Modifier.padding(start = Spacing.sm)) }
            }
        }
        Text("Voice notes and audio files (M4A, MP3, WAV, OGG, OPUS, WEBM, FLAC, AAC) are uploaded to your private storage, transcribed, then answered. The transcript stays with the message for follow-ups.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
