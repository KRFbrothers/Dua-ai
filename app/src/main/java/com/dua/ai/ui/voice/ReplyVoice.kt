package com.dua.ai.ui.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dua.ai.model.ChatMessage
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.voice.GeneratedVoiceSession
import com.dua.ai.voice.NativeGeneratedAudioOutput
import com.dua.ai.voice.NativeReplySpeaker
import com.dua.ai.voice.SpeakableText

/** Reads replies aloud with the device voice or the optional generated (AI) voice. */
@Stable
class ReplyVoice internal constructor(val speaker: NativeReplySpeaker, val generated: GeneratedVoiceSession) {
    fun stop() = stopAll()

    fun stopAll() { speaker.stop(); generated.stop() }

    fun toggle(message: ChatMessage, useGenerated: Boolean) {
        if (useGenerated) { speaker.stop(); generated.toggle(message.id, message.text, message.state == "complete") }
        else { generated.stop(); speaker.toggle(message.id, SpeakableText.clean(message.text).ifBlank { message.text }) }
    }

    /** Plays [sample] with the saved voice settings; tapping the same preview again stops it. */
    fun preview(id: String, sample: String, useGenerated: Boolean) {
        if (useGenerated) { speaker.stop(); generated.toggle(id, sample, true) } else { generated.stop(); speaker.toggle(id, sample) }
    }
}

/**
 * Creates the reply speakers for the whole app and wires them to the lifecycle, account,
 * connectivity and settings exactly as the previous single-screen implementation did:
 * playback stops when the app leaves the foreground, the account changes or the
 * voice preference changes, and the ViewModel is told when the app is in the foreground.
 */
@Composable
fun rememberReplyVoice(viewModel: ChatViewModel): ReplyVoice {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = remember(context) { NativeReplySpeaker(context.applicationContext) }
    val output = remember(context) { NativeGeneratedAudioOutput(context.applicationContext) }
    val generated = remember(viewModel, output, scope) {
        GeneratedVoiceSession(scope, viewModel::generateReplyAudio, output,
            currentOwner = { viewModel.account.state.value.user?.id },
            configured = { viewModel.localSettings.value.generatedVoice && viewModel.integrations.value.generatedVoiceConfigured },
            plan = viewModel::replyVoicePlan)
    }
    val voice = remember(speaker, generated) { ReplyVoice(speaker, generated) }
    val preferences by viewModel.localSettings.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val online by viewModel.online.collectAsState()
    LaunchedEffect(generated, online) { generated.setOnline(online) }
    LaunchedEffect(generated, integrations.generatedVoiceConfigured) { if (!integrations.generatedVoiceConfigured) generated.stop() }
    LaunchedEffect(generated, account.user?.id, account.busy) {
        generated.accountChanged()
        if (account.busy) voice.stopAll()
    }
    LaunchedEffect(generated, preferences.generatedVoice) { voice.stopAll() }
    LaunchedEffect(speaker, preferences.replyLanguage, preferences.deviceVoice, preferences.voiceSpeed) {
        speaker.configure(preferences.replyLanguage, preferences.deviceVoice, preferences.voiceSpeed)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(speaker, generated, output, lifecycle) {
        val started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        viewModel.setForeground(started)
        generated.setForeground(started)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { voice.stopAll(); generated.setForeground(false); viewModel.setForeground(false) }
            if (event == Lifecycle.Event.ON_START) { viewModel.setForeground(true); generated.setForeground(true) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); viewModel.setForeground(false); generated.close(); output.close(); speaker.close() }
    }
    return voice
}
