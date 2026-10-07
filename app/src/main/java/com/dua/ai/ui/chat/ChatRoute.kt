package com.dua.ai.ui.chat

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.FileProvider
import com.dua.ai.attachments.AttachmentReader
import com.dua.ai.attachments.CameraCaptureStore
import com.dua.ai.ui.voice.ReplyVoice
import com.dua.ai.viewmodel.ChatViewModel
import java.time.LocalTime

/** Binds [ChatScreen] to [ChatViewModel]. Activity-result launchers live here so they survive sheets closing. */
@Composable
fun ChatRoute(viewModel: ChatViewModel, voice: ReplyVoice, onOpenSettings: () -> Unit, onOpenVoice: () -> Unit, onOpenSetup: () -> Unit,
    attachRequest: Int = 0, onAttachRequestHandled: () -> Unit = {}) {
    val preferences by viewModel.localSettings.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val savedChats by viewModel.savedChats.collectAsState()
    val model by viewModel.selectedModel.collectAsState()
    val webMode by viewModel.webMode.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val readingFiles by viewModel.readingFiles.collectAsState()
    val fileError by viewModel.attachmentError.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val error by viewModel.error.collectAsState()
    val failedPrompt by viewModel.failedPrompt.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val speech by voice.speaker.state.collectAsState()
    val generatedSpeech by voice.generated.state.collectAsState()
    var attachmentNotice by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(failedPrompt) { failedPrompt?.let { viewModel.consumeFailedPrompt() } }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    fun pulse() { if (viewModel.localSettings.value.haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { attachmentNotice = ""; viewModel.selectAttachments(uris) }
    }
    val captures = remember(context) { CameraCaptureStore(context.cacheDir) }
    var pendingCapture by rememberSaveable { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val name = pendingCapture
        pendingCapture = ""
        if (name.isNotEmpty()) {
            val file = captures.find(name)
            if (captured && file.exists() && file.length() > 0) { attachmentNotice = ""; viewModel.selectCameraFile(file) }
            else { captures.discard(name); attachmentNotice = if (captured) "Camera did not save a photo. Try again." else "Capture cancelled. Previous selection kept." }
        }
    }
    fun pick(action: () -> Unit) {
        if (readingFiles || pendingCapture.isNotEmpty()) return
        try { action() }
        catch (_: ActivityNotFoundException) { discardPending(captures, pendingCapture); pendingCapture = ""; attachmentNotice = "No camera or file app available. Try Photos or Files." }
        catch (_: SecurityException) { discardPending(captures, pendingCapture); pendingCapture = ""; attachmentNotice = "Camera access is unavailable. Try Photos or Files." }
        catch (_: java.io.IOException) { discardPending(captures, pendingCapture); pendingCapture = ""; attachmentNotice = "Camera storage unavailable. Try Photos or Files." }
    }

    val greeting = remember(account.user?.displayName) { greetingFor(LocalTime.now().hour, account.user?.displayName) }
    val state = ChatUiState(
        messages = messages, draft = draft, typing = typing, error = error, model = model,
        providers = integrations.providers, webMode = webMode,
        webSearchConfigured = integrations.webSearchConfigured, pageReadingAvailable = integrations.pageReadingAvailable,
        attachments = attachments, readingFiles = readingFiles,
        attachmentStatus = if (readingFiles) "Reading files…" else fileError.ifBlank { attachmentNotice },
        savedChats = savedChats,
        speakingId = speech.activeId ?: generatedSpeech.activeId,
        speechAvailable = speech.available, generatedVoice = preferences.generatedVoice,
        deviceVoiceStatus = speech.status, deviceVoiceActive = speech.activeId != null,
        generatedVoiceStatus = generatedSpeech.status, generatedVoiceActive = generatedSpeech.activeId != null,
        greeting = greeting, suggestions = viewModel.promptSuggestions,
    )
    val actions = remember(viewModel, voice) {
        ChatActions(
            onDraftChange = viewModel::updateDraft,
            onSend = {
                pulse(); voice.stopAll()
                val current = viewModel.draft.value
                if ((current.isNotBlank() || viewModel.attachments.value.isNotEmpty()) && !viewModel.isTyping.value && !viewModel.readingFiles.value)
                    viewModel.sendMessage(current)
            },
            onStop = { pulse(); viewModel.stopReply() },
            onRetry = { id -> voice.stopAll(); viewModel.retryResponse(id) },
            retryWebMode = viewModel::replyWebMode,
            onSpeak = { message -> voice.toggle(message, viewModel.localSettings.value.generatedVoice) },
            onStopSpeech = voice::stopAll,
            onUseDeviceVoice = { voice.stopAll(); viewModel.updateSettings(viewModel.localSettings.value.copy(generatedVoice = false)) },
            onWebMode = viewModel::setWebMode,
            onSelectModel = viewModel::setModel,
            onNewChat = { pulse(); voice.stopAll(); viewModel.startNewChat(); viewModel.clearAttachments(); attachmentNotice = "" },
            onOpenChat = { id -> pulse(); voice.stopAll(); viewModel.openChat(id); viewModel.clearAttachments() },
            onDeleteChat = viewModel::deleteChat,
            onPickCamera = {
                pick {
                    val file = captures.create()
                    pendingCapture = file.name
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.camera", file))
                }
            },
            onPickPhotos = { pick { files.launch(arrayOf("image/jpeg", "image/png", "image/webp")) } },
            onPickFiles = { pick { files.launch(AttachmentReader.types) } },
            onClearAttachments = { viewModel.clearAttachments(); attachmentNotice = "" },
            onOpenSettings = onOpenSettings,
            onOpenVoice = onOpenVoice,
            onOpenSetup = onOpenSetup,
        )
    }
    ChatScreen(state, actions, attachRequest = attachRequest, onAttachRequestHandled = onAttachRequestHandled)
}

private fun discardPending(captures: CameraCaptureStore, name: String) { if (name.isNotEmpty()) captures.discard(name) }

internal fun greetingFor(hour: Int, name: String?): String {
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val first = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    return if (first != null) "$part, $first" else part
}
