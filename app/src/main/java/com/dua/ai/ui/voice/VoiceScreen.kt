package com.dua.ai.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.screens.AnimatedVoiceCube
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.voice.NativeVoiceInput
import com.dua.ai.voice.VoiceDraft
import kotlinx.coroutines.delay

/** Dictation: speak, review the transcript, then use it in the chat composer. */
@Composable
fun VoiceScreen(draft: String, onBack: () -> Unit, onHandsFree: () -> Unit, onUse: (String) -> Unit, inputLanguage: String = "auto", liveAvailable: Boolean = false) {
    val context = LocalContext.current
    val voice = remember(context) { NativeVoiceInput(context.applicationContext) }
    LaunchedEffect(voice, inputLanguage) { voice.language = inputLanguage }
    val input by voice.state.collectAsState()
    val listening = input.busy
    var reviewed by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    LaunchedEffect(input.transcript, input.busy) {
        if (input.transcript.isNotBlank()) reviewed = input.transcript
        else if (input.busy) reviewed = ""
    }
    LaunchedEffect(notice) { if (notice.isNotBlank()) { delay(2400); notice = "" } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) voice.start()
        else if (!granted) voice.permissionDenied()
    }
    DisposableEffect(voice, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) voice.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); voice.close() }
    }
    fun toggleListening() {
        if (listening) voice.stop()
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    DuaPageScaffold(title = "Voice", onBack = onBack, backLabel = "Back to chat") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Spacer(Modifier.height(Spacing.xl))
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                AnimatedVoiceCube(listening, Modifier.size(140.dp), stroke = DuaTheme.colors.voiceCubeStroke)
            }
            Text(input.status.ifBlank { "Tap the microphone and speak. Review your words, then use them in chat." },
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            if (input.partial.isNotBlank()) Text(input.partial.take(8000), style = MaterialTheme.typography.titleMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            FilledIconButton(onClick = ::toggleListening, modifier = Modifier.size(72.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = DuaTheme.colors.action, contentColor = DuaTheme.colors.onAction)) {
                Icon(if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, if (listening) "Stop voice input" else "Start voice input", Modifier.size(32.dp))
            }
            if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (input.transcript.isNotBlank() || reviewed.isNotBlank()) OutlinedTextField(value = reviewed,
                onValueChange = { if (it.length <= 8000) reviewed = it }, enabled = !listening,
                label = { Text("Review transcript") }, maxLines = 6, modifier = Modifier.fillMaxWidth())
            if (reviewed.isNotBlank()) Button(enabled = !listening, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DuaTheme.colors.action, contentColor = DuaTheme.colors.onAction), onClick = {
                    try { onUse(VoiceDraft.merge(draft, reviewed)) } catch (error: IllegalArgumentException) { notice = error.message.orEmpty() }
                }) { Text("Use in chat") }
            if (input.permissionDenied) TextButton(onClick = {
                try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                catch (_: android.content.ActivityNotFoundException) { notice = "App Settings unavailable." }
            }) { Text("Open app Settings") }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onHandsFree, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (liveAvailable) "Live conversation" else "Hands-free conversation", modifier = Modifier.padding(start = Spacing.sm))
            }
            Text("Listening in ${com.dua.ai.voice.SpeechLanguages.name(inputLanguage)} (change in Settings › Voice). Your device's speech service may process audio remotely.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
