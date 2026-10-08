package com.dua.ai.ui.screens

import com.dua.ai.ui.components.DuaPageScaffold
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.core.content.ContextCompat
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.voice.*

@Composable
fun HandsFreeScreen(viewModel: ChatViewModel, onBack: () -> Unit, onChat: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val voice = remember { NativeVoiceInput(context.applicationContext) }
    val speaker = remember { NativeReplySpeaker(context.applicationContext) }
    val input by voice.state.collectAsState()
    val speech by speaker.state.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val error by viewModel.error.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val preferences by viewModel.localSettings.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    LaunchedEffect(preferences.inputLanguage) { voice.language = preferences.inputLanguage }
    LaunchedEffect(preferences.replyLanguage, preferences.deviceVoice, preferences.voiceSpeed) {
        speaker.configure(preferences.replyLanguage, preferences.deviceVoice, preferences.voiceSpeed)
    }
    var phase by remember { mutableStateOf("idle") }
    var notice by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }
    var heardBusy by remember { mutableStateOf(false) }
    var spoke by remember { mutableStateOf(false) }
    var thinkingSeen by remember { mutableStateOf(false) }
    var token by remember { mutableIntStateOf(0) }
    val session = remember {
        HandsFreeSession(object : HandsFreeSession.Actions {
            override fun render(value: String) { phase = value }
            override fun listen() { heardBusy = true; voice.start(); if (!voice.state.value.busy) notice = voice.state.value.status }
            override fun send(text: String, version: Int) { token = version; thinkingSeen = true; viewModel.sendMessage(text) }
            override fun speak(text: String) { spoke = true; speaker.toggle("hands-free-reply", SpeakableText.clean(text).ifBlank { text }) }
            override fun cancel() { voice.stop(); speaker.stop(); if (viewModel.isTyping.value) viewModel.stopReply() }
            override fun notice(text: String) { notice = text }
        })
    }
    fun start() {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        if (!speaker.state.value.available) { notice = speaker.state.value.status.ifBlank { "Wait for device speech output to initialize." }; return }
        if (viewModel.isTyping.value || viewModel.readingFiles.value || viewModel.attachments.value.isNotEmpty()) { notice = "Finish the reply and send/remove attachments in Chat first."; return }
        if (!started) { viewModel.startNewChat(); started = true }
        notice = "Final speech sends automatically."; session.start(); if (!voice.state.value.busy) session.stop(voice.state.value.status)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else session.stop("Microphone permission denied. Change it in app Settings.")
    }
    DisposableEffect(session, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) session.stop("Stopped when leaving the foreground.") }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.stop(); voice.close(); speaker.close() }
    }
    LaunchedEffect(input) {
        if (session.phase == "listening") {
            val current = voice.state.value
            if (current.busy) heardBusy = true
            else if (heardBusy && current.transcript.isNotBlank() && current.status.startsWith("Review")) session.transcript(current.transcript)
            else if (heardBusy || !current.busy) session.stop(current.status)
        }
    }
    LaunchedEffect(typing, messages, error, phase) {
        if (session.phase == "thinking" && thinkingSeen && !viewModel.isTyping.value) {
            thinkingSeen = false
            val last = viewModel.messages.value.lastOrNull()
            if (viewModel.error.value != null) session.stop("Reply failed. Open Chat to review and retry.")
            else if (last != null && !last.isUser && last.state == "complete") session.reply(last.text, token)
            else session.stop("Reply unavailable. Open Chat to retry.")
        }
    }
    LaunchedEffect(speech, phase) {
        if (session.phase == "speaking") {
            val current = speaker.state.value
            if (current.activeId != null) spoke = true
            else if (spoke || current.status.isNotBlank()) { if (current.status.isNotBlank()) session.stop(current.status) else session.spoken() }
        }
    }
    DuaPageScaffold(title = "Hands-free", onBack = onBack) {
        Text("Starts a new chat. When you stop speaking, your words are sent to $selectedModel and the reply is read aloud with the device voice " +
            "(${SpeechLanguages.name(preferences.replyLanguage)}). Listening in ${SpeechLanguages.name(preferences.inputLanguage)}. Keep this screen open.",
            style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { AnimatedVoiceCube(phase != "idle", Modifier.size(72.dp)) }
        Text(when (phase) { "listening" -> "Listening…"; "thinking" -> "Dua is thinking…"; "speaking" -> "Reading reply…"; else -> "Ready" }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(notice, style = MaterialTheme.typography.bodyMedium)
        if (phase == "listening") Text(input.partial.ifBlank { input.transcript })
        if (started) messages.takeLast(2).forEach { Text((if (it.isUser) "You: " else "Dua: ") + it.text) }
        FilledTonalButton(enabled = phase == "idle", onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start() else permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Start hands-free") }
        FilledTonalButton(enabled = phase != "idle", onClick = { session.stop() }) { Text("Stop") }
        TextButton(onClick = onChat) { Text("Open conversation in Chat") }
        Text("Stop, Back or leaving the app cancels listening, the pending reply and playback. Your phone's speech service may process audio remotely. Change languages in Settings › Voice.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
