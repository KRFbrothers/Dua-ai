package com.dua.ai.ui.voice

import com.dua.ai.ui.theme.DuaTheme

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.dua.ai.R
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.VoiceShapes
import com.dua.ai.ui.theme.VoiceSizes
import com.dua.ai.ui.theme.VoiceTheme
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.voice.live.LiveCaption
import com.dua.ai.voice.live.LivePhase
import com.dua.ai.voice.live.LiveState
import com.dua.ai.voice.live.LiveUsage
import com.dua.ai.voice.live.LiveVoiceController
import kotlin.math.sin

/** Full-screen live voice conversation with Grok (xAI realtime). Falls back to hands-free mode when unavailable. */
@Composable
fun LiveVoiceScreen(viewModel: ChatViewModel, onClose: () -> Unit, onHandsFree: () -> Unit) {
    val context = LocalContext.current
    val state by LiveVoiceController.state.collectAsState()
    val start by viewModel.liveStart.collectAsState()
    val usage by viewModel.liveUsage.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val settings by viewModel.localSettings.collectAsState()
    val voiceName = integrations.voice?.live?.let { config -> config.voices.firstOrNull { it.id == (viewModel.liveOptions()?.voice ?: settings.liveVoice) }?.name } ?: "Grok"
    LaunchedEffect(Unit) { viewModel.clearLiveError(); if (!LiveVoiceController.active) LiveVoiceController.reset(); viewModel.loadLiveUsage() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startLiveVoice() else viewModel.settingsNotice("Microphone permission is needed for live voice.")
    }
    fun begin() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.startLiveVoice()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LiveVoiceContent(state = state, starting = start.busy, error = start.error, fallback = start.fallback, usage = usage, voiceName = voiceName,
        onStart = ::begin, onMute = { LiveVoiceController.setMuted(!state.muted) }, onEnd = { LiveVoiceController.end() },
        onClose = { if (state.active) LiveVoiceController.end(); onClose() }, onHandsFree = onHandsFree)
}

@Composable
fun LiveVoiceContent(state: LiveState, starting: Boolean, error: String, fallback: Boolean, usage: LiveUsage?, voiceName: String,
    onStart: () -> Unit, onMute: () -> Unit, onEnd: () -> Unit, onClose: () -> Unit, onHandsFree: () -> Unit) = com.dua.ai.ui.theme.DuaPageTheme {
    LiveVoiceContentBody(state = state, starting = starting, error = error, fallback = fallback, usage = usage, voiceName = voiceName, onStart = onStart, onMute = onMute, onEnd = onEnd, onClose = onClose, onHandsFree = onHandsFree)
}

@Composable
private fun LiveVoiceContentBody(state: LiveState, starting: Boolean, error: String, fallback: Boolean, usage: LiveUsage?, voiceName: String,
    onStart: () -> Unit, onMute: () -> Unit, onEnd: () -> Unit, onClose: () -> Unit, onHandsFree: () -> Unit) {
    val v = VoiceTheme.colors
    // No Surface here, so set the content colour for icons and titles (dark theme).
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides v.text) {
    Box(Modifier.fillMaxSize().background(v.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = if (state.active) "End and close" else "Close") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Live voice", style = MaterialTheme.typography.titleMedium)
                    Text("$voiceName · Grok", style = MaterialTheme.typography.bodySmall, color = v.subText)
                }
                Surface(shape = VoiceShapes.pill, color = v.pill) {
                    Text(timerLabel(state, usage), style = MaterialTheme.typography.labelMedium, color = v.subText,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            VoiceOrb(state.phase, state.level, state.muted, Modifier.size(VoiceSizes.liveOrb))
            Spacer(Modifier.height(Spacing.lg))
            Text(statusLabel(state, starting, error), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                color = if (error.isNotBlank() || state.phase == LivePhase.FAILED) v.error else v.text,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            Captions(state.captions, Modifier.weight(1f).fillMaxWidth().padding(top = Spacing.lg))
            if (state.active) Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onMute, modifier = Modifier.size(VoiceSizes.liveMute)) {
                    Icon(if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, contentDescription = if (state.muted) "Unmute microphone" else "Mute microphone", modifier = Modifier.size(VoiceSizes.liveMuteIcon))
                }
                FilledIconButton(onClick = onEnd, modifier = Modifier.size(VoiceSizes.liveEnd),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DuaTheme.colors.danger,
                        contentColor = DuaTheme.colors.onDanger)) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "End live voice", modifier = Modifier.size(VoiceSizes.liveEndIcon))
                }
            } else Column(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onStart, enabled = !starting && !(fallback && error.isNotBlank() && usage?.leftSeconds == 0),
                    modifier = Modifier.fillMaxWidth().widthIn(max = VoiceSizes.contentMaxWidth).height(VoiceSizes.startButtonHeight), shape = VoiceShapes.startButton,
                    colors = ButtonDefaults.buttonColors(containerColor = v.accent, contentColor = v.onAccent)) {
                    Icon(painterResource(R.drawable.ls_live_wave), null); Text(if (state.phase == LivePhase.IDLE) "Start talking" else "Start again", modifier = Modifier.padding(start = Spacing.sm))
                }
                if (fallback || error.isNotBlank()) TextButton(onClick = onHandsFree) { Text("Use hands-free mode instead") }
                Text("Audio streams to xAI only while a session runs. The conversation is saved to this chat when it ends.",
                    style = MaterialTheme.typography.bodySmall, color = v.subText, textAlign = TextAlign.Center)
            }
        }
    }
    }
}

private fun timerLabel(state: LiveState, usage: LiveUsage?): String = when {
    state.active && state.maxSeconds > 0 -> clock(state.remainingSeconds) + " left"
    usage != null && usage.limitSeconds > 0 -> "${usage.leftSeconds / 60} of ${usage.limitSeconds / 60} min left today"
    else -> "Live"
}
private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun statusLabel(state: LiveState, starting: Boolean, error: String): String = when {
    error.isNotBlank() -> error
    starting -> "Getting a secure session…"
    state.phase == LivePhase.IDLE -> "Talk with Dua hands-free. Interrupt any time."
    state.status.isNotBlank() -> state.status
    else -> ""
}

@Composable
private fun Captions(captions: List<LiveCaption>, modifier: Modifier) {
    val list = rememberLazyListState()
    val shown = captions.filter { it.text.isNotBlank() }.takeLast(40)
    LaunchedEffect(shown.size, shown.lastOrNull()?.text?.length) { if (shown.isNotEmpty()) list.animateScrollToItem(shown.size - 1) }
    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        items(shown, key = { it.id }) { caption ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (caption.user) Alignment.End else Alignment.Start) {
                Text(if (caption.user) "You" else "Dua", style = MaterialTheme.typography.labelSmall, color = VoiceTheme.colors.subText)
                Text(caption.text + if (caption.interrupted) " —" else "", textAlign = if (caption.user) TextAlign.End else TextAlign.Start,
                    style = if (caption.user) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    color = if (caption.user) VoiceTheme.colors.subText else VoiceTheme.colors.text.copy(alpha = if (caption.final) 1f else 0.85f))
            }
        }
    }
}

/** Animated orb: breathes while listening, swells with the voice level and ripples while Dua speaks. */
@Composable
fun VoiceOrb(phase: LivePhase, level: Float, muted: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val time by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "time")
    val smoothed by animateFloatAsState(if (muted) 0f else level, tween(120), label = "level")
    val accent = VoiceTheme.colors.accent
    val speaking = phase == LivePhase.SPEAKING
    val idle = phase == LivePhase.IDLE || phase == LivePhase.ENDED || phase == LivePhase.FAILED
    val core = if (muted || idle) VoiceTheme.colors.idle else accent
    val voiceColors = DuaTheme.colors.voice
    val glow = if (speaking) voiceColors.speakingGlow else accent
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        val breathe = if (idle) 0f else (sin(time * 2 * Math.PI).toFloat() + 1f) / 2f
        val scale = 0.62f + 0.06f * breathe + 0.22f * smoothed
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = if (idle) 0.12f else 0.35f), Color.Transparent), center, radius), radius, center)
        if (speaking || phase == LivePhase.USER_SPEAKING) for (ring in 0..2) {
            val progress = (time + ring / 3f) % 1f
            drawCircle(glow.copy(alpha = (1f - progress) * 0.35f), radius * (scale + 0.35f * progress), center, style = Stroke(width = 3f))
        }
        drawCircle(Brush.radialGradient(listOf(core.copy(alpha = 0.95f), glow.copy(alpha = 0.75f)), center, radius * scale), radius * scale, center)
        drawCircle(voiceColors.highlight.copy(alpha = 0.18f), radius * scale * 0.45f, Offset(center.x - radius * scale * 0.25f, center.y - radius * scale * 0.3f))
    }
}
