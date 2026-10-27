package com.dua.ai.ui.chat

import com.dua.ai.ui.theme.SheetNoteStyle
import com.dua.ai.ui.theme.SheetTitleStyle
import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/** Record a voice note or pick an audio file. Audio is transcribed first, then answered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSheet(recordingSince: Long?, available: Boolean, hint: String, onRecord: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit, onPick: () -> Unit, onDismiss: () -> Unit) {
    DuaSheet(onDismiss = { if (recordingSince == null) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { recordingSince == null })) {
        AudioSheetContent(recordingSince, available, hint, onRecord, onStop, onCancel, onPick)
    }
}

@Composable
fun AudioSheetContent(recordingSince: Long?, available: Boolean, hint: String, onRecord: () -> Unit, onStop: () -> Unit, onCancel: () -> Unit, onPick: () -> Unit,
    now: () -> Long = System::currentTimeMillis) {
    var tick by remember { mutableLongStateOf(now()) }
    LaunchedEffect(recordingSince) { while (recordingSince != null) { tick = now(); delay(500) } }
    val c = SheetTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = SheetSizes.paddingH).padding(bottom = SheetSizes.paddingBottom), verticalArrangement = Arrangement.spacedBy(SheetSizes.sectionGap)) {
        SheetTitle("Audio")
        if (!available) {
            Text(hint, style = SheetBodyStyle, color = c.subText)
            return@Column
        }
        if (recordingSince != null) {
            val seconds = ((tick - recordingSince).coerceAtLeast(0) / 1000).toInt()
            Text("Recording… %d:%02d".format(seconds / 60, seconds % 60), style = SheetTitleStyle, color = c.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Row(horizontalArrangement = Arrangement.spacedBy(SheetSizes.tileGap)) {
                SheetFilledButton("Stop and attach", onClick = onStop, icon = Icons.Outlined.Stop, modifier = Modifier.weight(1f).heightIn(min = SheetSizes.rowMin))
                SheetTextButton("Discard", onClick = onCancel, destructive = true, modifier = Modifier.heightIn(min = SheetSizes.rowMin))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(SheetSizes.tileGap)) {
                SheetFilledButton("Record", onClick = onRecord, icon = Icons.Outlined.Mic, modifier = Modifier.weight(1f).heightIn(min = SheetSizes.rowMin))
                SheetTonalButton("Choose file", onClick = onPick, icon = Icons.Outlined.AudioFile, modifier = Modifier.weight(1f).heightIn(min = SheetSizes.rowMin))
            }
        }
        Text("Voice notes and audio files (M4A, MP3, WAV, OGG, OPUS, WEBM, FLAC, AAC) are uploaded to your private storage, transcribed, then answered. The transcript stays with the message for follow-ups.",
            style = SheetNoteStyle, color = c.subText)
    }
}
