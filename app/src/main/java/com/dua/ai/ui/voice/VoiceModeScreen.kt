package com.dua.ai.ui.voice

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dua.ai.ui.chat.ChatHaptic
import com.dua.ai.ui.chat.constant
import com.dua.ai.ui.theme.rememberMotionAllowed
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.voice.BargeInMonitor
import com.dua.ai.voice.NativeBargeInMonitor
import com.dua.ai.voice.GeneratedVoiceState
import com.dua.ai.voice.NativeVoiceInput
import com.dua.ai.voice.ReadAloudState
import com.dua.ai.voice.RecognizerError
import com.dua.ai.voice.SpeakableText
import com.dua.ai.voice.VoiceInputState
import com.dua.ai.voice.VoiceModeSession
import com.dua.ai.voice.VoiceModeState
import com.dua.ai.voice.VoicePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Maps a SpeechRecognizer error code to what the voice loop should do about it. */
internal fun recognizerErrorOf(code: Int?): RecognizerError = when (code) {
    null, SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RecognizerError.NoSpeech
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> RecognizerError.Busy
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, 11 /* ERROR_SERVER_DISCONNECTED */ -> RecognizerError.Network
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognizerError.Permission
    else -> RecognizerError.Unavailable
}

/**
 * Glue between [VoiceModeSession] and the device: SpeechRecognizer in, the current chat's request
 * (current model and modes, saved in the chat) and the reply voice out — the AI voice when it is enabled and
 * configured, else the device voice, falling back to the device voice if the AI voice fails.
 */
@Stable
internal class VoiceModeController(
    private val scope: CoroutineScope,
    private val viewModel: ChatViewModel,
    private val voice: ReplyVoice,
    val input: NativeVoiceInput,
    private val bargeIn: BargeInMonitor? = null,
) : VoiceModeSession.Actions {
    var state by mutableStateOf(VoiceModeState()); private set
    var userText by mutableStateOf(""); private set
    var replyText by mutableStateOf(""); private set
    var feedback by mutableIntStateOf(0)
    val session = VoiceModeSession(this)

    private var listening = false
    private var heardBusy = false
    private var awaiting = -1
    private var sentAt = 0
    private var speakId: String? = null
    private var speakStarted = false
    private var usingGenerated = false
    private var speakText = ""
    private var utterance = 0

    init {
        scope.launch { input.state.collect(::onInput) }
        scope.launch { combine(viewModel.isTyping, viewModel.error, viewModel.messages) { _, _, _ -> }.collect { checkReply() } }
        scope.launch { voice.speaker.state.collect(::onSpeaker) }
        scope.launch { voice.generated.state.collect(::onGenerated) }
    }

    private var monitoredToken: Int? = null
    override fun render(state: VoiceModeState) {
        this.state = state
        if (state.phase == VoicePhase.Speaking && !state.muted) {
            if (monitoredToken != session.token) {
                val token = session.token
                monitoredToken = token
                if (bargeIn?.start { session.bargeIn(token) } == false && state.notice.isBlank())
                    this.state = state.copy(notice = "Tap the waveform to interrupt on this device.")
            }
        } else { monitoredToken = null; bargeIn?.stop() }
    }

    override fun listen() {
        listening = true; heardBusy = false
        input.language = viewModel.localSettings.value.inputLanguage
        input.start()
        if (!input.state.value.busy) {
            // Never started (no recognizer on this device, or a permission problem).
            val failed = input.state.value
            scope.launch { if (listening && session.state.phase == VoicePhase.Listening) { listening = false; session.recognizerError(if (failed.permissionDenied) RecognizerError.Permission else RecognizerError.Unavailable) } }
        }
    }

    override fun stopListening() { listening = false; input.stop() }

    private fun onInput(value: VoiceInputState) {
        if (!listening || session.state.phase != VoicePhase.Listening) return
        if (value.busy) { heardBusy = true; return }
        if (!heardBusy) return
        listening = false; heardBusy = false
        if (value.error == null && value.transcript.isNotBlank()) session.transcript(value.transcript)
        else session.recognizerError(recognizerErrorOf(value.error))
    }

    override fun send(text: String, token: Int) {
        userText = text; replyText = ""; feedback = 0
        val blocked = when {
            !viewModel.online.value -> "You're offline. Reconnect, then tap the orb."
            viewModel.imagine.value -> "Imagine is on. Turn it off in the chat to talk with Dua."
            viewModel.isTyping.value || viewModel.readingFiles.value -> "Dua is still answering in the chat. Wait a moment, then tap the orb."
            viewModel.attachments.value.isNotEmpty() || viewModel.uploads.value.isNotEmpty() -> "Send or remove the attachments in the chat first."
            else -> null
        }
        if (blocked != null) { scope.launch { session.replyFailed(blocked, token) }; return }
        sentAt = viewModel.messages.value.size
        awaiting = token
        viewModel.sendMessage(text)
        scope.launch { checkReply() }
    }

    private fun checkReply() {
        val token = awaiting
        if (token < 0 || token != session.token || session.state.phase != VoicePhase.Thinking || viewModel.isTyping.value) return
        awaiting = -1
        val error = viewModel.error.value
        val messages = viewModel.messages.value
        val last = messages.lastOrNull()
        when {
            error != null -> session.replyFailed(if (!viewModel.online.value) "You're offline. Reconnect, then tap the orb." else error, token)
            last != null && !last.isUser && messages.size > sentAt + 1 && last.state != "streaming" -> { replyText = last.text; session.reply(last.text, token) }
            else -> session.replyFailed("Couldn't get a reply. Tap the orb to try again.", token)
        }
    }

    override fun cancelReply() { awaiting = -1; if (viewModel.isTyping.value) viewModel.stopReply() }

    override fun speak(text: String, token: Int) {
        speakText = SpeakableText.clean(text).ifBlank { text }
        val settings = viewModel.localSettings.value
        if (settings.generatedVoice && viewModel.integrations.value.generatedVoiceConfigured && viewModel.online.value) {
            usingGenerated = true; speakStarted = false
            speakId = "voice-mode-$token-${++utterance}"
            voice.speaker.stop()
            voice.generated.toggle(speakId!!, text, true)
        } else speakDevice(token)
    }

    private fun speakDevice(token: Int) {
        usingGenerated = false; speakStarted = false
        val id = "voice-mode-$token-${++utterance}"
        speakId = id
        voice.generated.stop()
        voice.speaker.toggle(id, speakText)
        if (voice.speaker.state.value.activeId != id) {
            val reason = voice.speaker.state.value.status.ifBlank { "Your phone's voice isn't ready yet." }
            speakId = null
            scope.launch { session.speakFailed(reason, token) }
        }
    }

    override fun stopSpeaking() { speakId = null; voice.stop(); bargeIn?.stop(); monitoredToken = null }

    private fun onSpeaker(value: ReadAloudState) {
        val id = speakId ?: return
        if (usingGenerated || session.state.phase != VoicePhase.Speaking) return
        if (value.activeId == id) { speakStarted = true; return }
        if (!speakStarted) return
        speakId = null
        if (value.status.isBlank()) session.spoken(session.token) else session.speakFailed("Your phone's voice stopped. The reply is in the captions.", session.token)
    }

    private fun onGenerated(value: GeneratedVoiceState) {
        val id = speakId ?: return
        if (!usingGenerated || session.state.phase != VoicePhase.Speaking) return
        if (value.activeId == id) { speakStarted = true; return }
        if (value.status.isBlank() && speakStarted) { speakId = null; session.spoken(session.token); return }
        if (value.status.isNotBlank()) speakDevice(session.token) // AI voice failed: read it with the phone's voice instead
    }

    fun close() { session.close(); bargeIn?.stop(); input.close() }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) { if (current is Activity) return current; current = current.baseContext }
    return null
}

/**
 * Full-screen voice mode. Opens listening right away when the microphone is allowed; everything stops when the
 * screen closes or the app goes to the background. Turns are saved in the current chat.
 */
@Composable
fun VoiceModeScreen(viewModel: ChatViewModel, voice: ReplyVoice, onClose: () -> Unit, onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbar = remember { SnackbarHostState() }
    val controller = remember { VoiceModeController(scope, viewModel, voice, NativeVoiceInput(context.applicationContext), NativeBargeInMonitor(context.applicationContext)) }
    val prefs = remember { context.getSharedPreferences("dua_voice_mode", Context.MODE_PRIVATE) }
    var captions by remember { mutableStateOf(prefs.getBoolean("captions", true)) }
    val account by viewModel.account.state.collectAsState()
    val preferences by viewModel.localSettings.collectAsState()
    val input by controller.input.state.collectAsState()
    val motion = rememberMotionAllowed()

    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(if (granted()) MicPermission.Granted else MicPermission.Rationale) }
    fun haptic(kind: ChatHaptic) { if (viewModel.localSettings.value.haptic) view.performHapticFeedback(kind.constant()) }
    fun say(message: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) } }
    fun openSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun startIfReady() { if (permission == MicPermission.Granted && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.session.start() }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permission = when {
            ok -> MicPermission.Granted
            context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } == true -> MicPermission.Rationale
            else -> MicPermission.Blocked
        }
        if (ok) controller.session.start()
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> controller.session.pause()
                Lifecycle.Event.ON_RESUME -> {
                    val now = granted()
                    if (now && permission != MicPermission.Granted) { permission = MicPermission.Granted; controller.session.start() }
                    else if (!now && permission == MicPermission.Granted) permission = MicPermission.Rationale
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.close() }
    }
    LaunchedEffect(Unit) { startIfReady() }
    var toChat by remember { mutableStateOf(false) }
    LaunchedEffect(controller.state.closed) { if (controller.state.closed) { if (toChat) onOpenChat() else onClose() } }

    val state = controller.state
    val ui = VoiceModeUi(
        phase = state.phase, muted = state.muted, captions = captions,
        firstName = firstNameOf(account.user?.displayName),
        heard = if (state.phase == VoicePhase.Listening) input.partial else "",
        userText = controller.userText, replyText = controller.replyText, notice = state.notice,
        permission = permission, feedback = controller.feedback,
    )
    VoiceModeContent(
        ui = ui,
        hapticEnabled = preferences.haptic,
        level = { controller.input.level.value },
        motion = motion,
        snackbar = snackbar,
        callbacks = VoiceModeCallbacks(
            onOrb = {
                haptic(ChatHaptic.Tap)
                if (permission != MicPermission.Granted) { if (permission == MicPermission.Rationale) launcher.launch(Manifest.permission.RECORD_AUDIO) else openSettings() } else controller.session.tapOrb()
            },
            onMute = { haptic(ChatHaptic.Tap); controller.session.setMuted(!controller.state.muted) },
            onClose = { controller.session.close() },
            onCaptions = { captions = it; prefs.edit().putBoolean("captions", it).apply() },
            onCamera = { say("Camera in voice mode is coming soon.") },
            onUpload = { say("Sharing your screen or a file is coming soon.") },
            onFeedback = {
                controller.feedback = it
                if (it != 0) say(if (it > 0) "Thanks! Glad that helped." else "Thanks for the feedback.")
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, controller.replyText)
                runCatching { context.startActivity(Intent.createChooser(send, "Share reply").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { say("No app can share this reply.") }
            },
            onCopy = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Dua reply", controller.replyText))
                say("Reply copied")
            },
            onOpenChat = { toChat = true; controller.session.close() },
            onAllowMic = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
            onOpenSettings = ::openSettings,
        ),
    )
}
