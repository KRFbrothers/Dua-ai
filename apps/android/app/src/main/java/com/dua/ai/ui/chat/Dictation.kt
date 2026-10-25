package com.dua.ai.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dua.ai.ui.theme.HomeShapes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.ComposerTextStyle
import com.dua.ai.ui.theme.VoiceSizes
import com.dua.ai.voice.DictationEngine
import com.dua.ai.voice.NativeVoiceInput
import com.dua.ai.voice.VoiceDraft
import kotlinx.coroutines.delay

/** Pure helpers for inline chat dictation (unit-tested). */
object DictationLogic {
    fun elapsed(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

    /** Ring buffer of recent input levels, oldest first; [push] drops the oldest. */
    fun push(levels: FloatArray, value: Float) {
        System.arraycopy(levels, 1, levels, 0, levels.size - 1)
        levels[levels.size - 1] = value.coerceIn(0f, 1f)
    }

    /** The merged draft and whether to send it now (auto-send only when the recognizer itself heard silence). */
    fun result(draft: String, transcript: String, autoSend: Boolean, endedBySilence: Boolean): Pair<String, Boolean>? =
        runCatching { VoiceDraft.merge(draft, transcript) }.getOrNull()?.let { it to (autoSend && endedBySilence) }
}

/** One inline dictation session for the chat composer. Stable (remembered once), so it never forces the composer to recompose. */
@Stable
class ChatDictation internal constructor(
    private val listeningState: State<Boolean>,
    val level: State<Float>,
    val start: () -> Unit,
    val cancel: () -> Unit,
    val confirm: () -> Unit,
) { val listening: Boolean get() = listeningState.value }

@Composable
fun rememberChatDictation(language: () -> String, autoSend: () -> Boolean, draft: () -> String,
    onDraft: (String) -> Unit, onSend: () -> Unit, onNotice: (String) -> Unit = {},
    /** Offline model folder to use for this session (given whether the phone's recognizer exists), or null for the phone's. */
    offlineModel: (phoneRecognizerAvailable: Boolean) -> java.io.File? = { null }): ChatDictation {
    val context = LocalContext.current
    // Cheap: only flows. The SpeechRecognizer is created when the mic is tapped and destroyed when listening ends.
    val native = remember(context) { NativeVoiceInput(context.applicationContext) }
    // Offline (Vosk) engine, created on first offline use and kept so the model stays loaded between taps.
    val offline = remember { androidx.compose.runtime.mutableStateOf<Pair<java.io.File, DictationEngine>?>(null) }
    val active = remember(native) { androidx.compose.runtime.mutableStateOf<DictationEngine>(native) }
    val input = active.value
    val state = input.state.collectAsState()
    val level = input.level.collectAsState()
    val currentOffline by rememberUpdatedState(offlineModel)
    val currentDraft by rememberUpdatedState(draft)
    val currentOnDraft by rememberUpdatedState(onDraft)
    val currentOnSend by rememberUpdatedState(onSend)
    val currentNotice by rememberUpdatedState(onNotice)
    val currentLanguage by rememberUpdatedState(language)
    val currentAutoSend by rememberUpdatedState(autoSend)
    val started = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(input) {
        input.state.collect { s ->
            if (!s.busy && started.value) {
                started.value = false
                if (s.transcript.isNotBlank()) DictationLogic.result(currentDraft(), s.transcript, currentAutoSend(), s.endedBySilence)?.let { (text, send) ->
                    currentOnDraft(text); if (send) currentOnSend()
                } else currentNotice(s.status)
            }
        }
    }
    val begin = remember(native) {
        {
            val dir = currentOffline(android.speech.SpeechRecognizer.isRecognitionAvailable(context))
            val engine = if (dir == null) native else offline.value?.takeIf { it.first == dir }?.second
                ?: com.dua.ai.voice.offline.VoskVoiceInput(dir).also { created -> offline.value?.second?.close(); offline.value = dir to created }
            active.value = engine
            engine.language = currentLanguage(); started.value = true; engine.start()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin()
        else { active.value.permissionDenied(); currentNotice("Microphone permission denied. Allow it in app Settings.") }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, native) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { started.value = false; active.value.stop() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); native.close(); offline.value?.second?.close() }
    }
    return remember(native, permission) {
        ChatDictation(
            listeningState = androidx.compose.runtime.derivedStateOf { state.value.busy }, level = level,
            start = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            cancel = { started.value = false; active.value.stop() },
            confirm = { active.value.confirm() },
        )
    }
}

/** Composer content while dictating: cancel · live waveform · timer · confirm. Replaces the text row in the same pill. */
@Composable
fun RowScope.DictationBar(level: State<Float>, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = HomeTheme.colors
    val levels = remember { FloatArray(VoiceSizes.waveSamples) }
    val tick = remember { mutableIntStateOf(0) }
    val seconds = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            delay(VoiceSizes.waveSampleMs)
            DictationLogic.push(levels, level.value)
            tick.intValue++
            seconds.longValue = (System.nanoTime() - start) / 1_000_000_000L
        }
    }
    IconButton(onClick = onCancel, modifier = Modifier.size(Sizes.touchTarget)) {
        Icon(Icons.Default.Close, contentDescription = "Cancel dictation", tint = c.barIcon, modifier = Modifier.size(HomeSizes.micIcon))
    }
    val wave = c.input
    // Reads tick/levels only in the draw phase: the waveform redraws without recomposing the composer.
    Canvas(Modifier.weight(1f).height(VoiceSizes.waveHeight).padding(horizontal = Spacing.sm).testTag("dictation-wave")
        .clearAndSetSemantics { contentDescription = "Listening" }) {
        tick.intValue
        val bar = VoiceSizes.waveBar.toPx(); val step = bar + VoiceSizes.waveGap.toPx(); val min = VoiceSizes.waveMinHeight.toPx()
        val count = (size.width / step).toInt().coerceAtMost(levels.size)
        for (i in 0 until count) {
            val v = levels[levels.size - count + i]
            val h = (min + v * (size.height - min)).coerceAtMost(size.height)
            drawRoundRect(wave, Offset(i * step, (size.height - h) / 2f), Size(bar, h), CornerRadius(bar / 2f))
        }
    }
    val secs = seconds.longValue
    Text(DictationLogic.elapsed(secs), color = c.placeholder, style = ComposerTextStyle,
        modifier = Modifier.padding(horizontal = Spacing.xs).semantics { contentDescription = "Recording, $secs seconds" })
    Box(Modifier.size(Sizes.touchTarget).clickable(role = Role.Button, onClick = onConfirm)
        .semantics(mergeDescendants = true) { contentDescription = "Done, use dictation" }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(HomeSizes.voiceButton).clip(HomeShapes.round).background(c.voice), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, tint = c.onVoice, modifier = Modifier.size(HomeSizes.voiceIcon))
        }
    }
}
