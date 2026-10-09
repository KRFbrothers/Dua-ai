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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    onOpenComputer: () -> Unit = {},
    onOpenVoiceMode: () -> Unit = {},
    attachRequest: Int = 0, onAttachRequestHandled: () -> Unit = {}) {
    val preferences by viewModel.localSettings.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val savedChats by viewModel.savedChats.collectAsState()
    val model by viewModel.selectedModel.collectAsState()
    val webMode by viewModel.webMode.collectAsState()
    val reasoning by viewModel.reasoning.collectAsState()
    val xSearch by viewModel.xSearch.collectAsState()
    val summary by viewModel.summary.collectAsState()
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
    val uploads by viewModel.uploads.collectAsState()
    val recordingSince by viewModel.recordingSince.collectAsState()
    val imagine by viewModel.imagine.collectAsState()
    val imagineOptions by viewModel.imagineOptions.collectAsState()
    val imagineUsage by viewModel.imagineUsage.collectAsState()
    val runCode by viewModel.runCode.collectAsState()
    val buildRepo by viewModel.buildRepo.collectAsState()
    val buildRepositories by viewModel.buildRepositories.collectAsState()
    val buildPr by viewModel.buildPr.collectAsState()
    val scope = rememberCoroutineScope()
    var attachmentNotice by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(failedPrompt) { failedPrompt?.let { viewModel.consumeFailedPrompt() } }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    fun pulse() { if (viewModel.localSettings.value.haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { attachmentNotice = ""; viewModel.selectAttachments(uris) }
    }
    val audioFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { attachmentNotice = ""; viewModel.selectAttachments(uris) }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording() else attachmentNotice = "Microphone permission is needed to record a voice note. You can choose an audio file instead."
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
        providers = integrations.providers, webMode = webMode, reasoning = reasoning,
        webSearchConfigured = integrations.searchAvailable(model), pageReadingAvailable = integrations.pageReadingAvailable,
        xSearchAvailable = model == "Grok" && integrations.xSearchAvailable, xSearch = xSearch,
        summarisedThroughId = summary?.throughId,
        attachments = attachments, readingFiles = readingFiles,
        uploads = uploads, recordingSince = recordingSince,
        storedFiles = integrations.filesEnabled && account.user != null,
        storedFilesHint = remember(integrations.filesEnabled, account.user, integrations.filesMaxBytes) { viewModel.storedFilesHint() },
        storedFilesMaxBytes = integrations.filesMaxBytes,
        attachmentStatus = if (readingFiles) "Reading files…" else fileError.ifBlank { attachmentNotice },
        savedChats = savedChats,
        speakingId = speech.activeId ?: generatedSpeech.activeId,
        speechAvailable = speech.available, generatedVoice = preferences.generatedVoice,
        deviceVoiceStatus = speech.status, deviceVoiceActive = speech.activeId != null,
        generatedVoiceStatus = generatedSpeech.status, generatedVoiceActive = generatedSpeech.activeId != null,
        greeting = greeting, suggestions = viewModel.promptSuggestions,
        imagine = imagine, imagineOptions = imagineOptions, imagineConfig = integrations.imagine, imagineUsage = imagineUsage,
        runCode = runCode, codeConfig = integrations.codeExecution,
        buildRepo = buildRepo, buildConfig = integrations.build, buildRepositories = buildRepositories,
        buildPr = buildPr, buildPrHint = remember(integrations.build, account.user) { viewModel.buildPrUnavailableReason().orEmpty() },
        buildHint = remember(integrations.build, integrations.providers, account.user) { viewModel.buildUnavailableReason().orEmpty() },
    )
    LaunchedEffect(imagine) { if (imagine) viewModel.loadImagineUsage() }
    // Saving to the gallery on Android 8–9 needs the legacy storage permission; Android 10+ uses MediaStore directly.
    var pendingSave by remember { mutableStateOf<List<com.dua.ai.imagine.GeneratedMedia>>(emptyList()) }
    fun saveMedia(items: List<com.dua.ai.imagine.GeneratedMedia>) {
        scope.launch {
            try {
                val files = items.map { item -> (viewModel.mediaCache.file(item) ?: error("This file is no longer available.")) to item }
                files.forEachIndexed { index, (file, item) -> com.dua.ai.imagine.MediaExport.saveToGallery(context, file, item, index) }
                val video = items.all { it.isVideo }
                attachmentNotice = (if (items.size == 1) (if (video) "Video" else "Image") else "${items.size} files") + " saved to " + (if (video) "Movies/Dua." else "Pictures/Dua.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { attachmentNotice = failure.message ?: "Could not save to the gallery." }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val items = pendingSave; pendingSave = emptyList()
        if (granted) saveMedia(items) else attachmentNotice = "Storage permission is needed to save on this Android version."
    }
    val actions = remember(viewModel, voice) {
        ChatActions(
            onDraftChange = viewModel::updateDraft,
            onSend = {
                if (viewModel.localSettings.value.haptic) view.performChatHaptic(ChatHaptic.Confirm)
                voice.stopAll()
                val current = viewModel.draft.value
                if ((current.isNotBlank() || viewModel.attachments.value.isNotEmpty() || viewModel.uploads.value.any { it.ref != null }) && !viewModel.isTyping.value && !viewModel.readingFiles.value)
                    viewModel.sendMessage(current)
            },
            onStop = { pulse(); viewModel.stopReply() },
            onRetry = { id -> voice.stopAll(); viewModel.retryResponse(id) },
            retryWebMode = viewModel::replyWebMode,
            onSpeak = { message -> voice.toggle(message, viewModel.localSettings.value.generatedVoice) },
            onStopSpeech = voice::stopAll,
            onUseDeviceVoice = { voice.stopAll(); viewModel.updateSettings(viewModel.localSettings.value.copy(generatedVoice = false)) },
            onWebMode = viewModel::setWebMode,
            onReasoning = viewModel::setReasoning,
            onXSearch = viewModel::setXSearch,
            onAuto = viewModel::selectAuto,
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
            onPickFiles = { pick { files.launch(if (viewModel.storedFilesAvailable()) AttachmentReader.types + com.dua.ai.files.StoredFileTypes.PICKER_EXTRA else AttachmentReader.types) } },
            onPickAudio = { pick { audioFiles.launch(com.dua.ai.files.StoredFileTypes.AUDIO_PICKER) } },
            onStartRecording = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) viewModel.startRecording()
                else microphone.launch(android.Manifest.permission.RECORD_AUDIO)
            },
            onStopRecording = viewModel::stopRecording,
            onCancelRecording = viewModel::cancelRecording,
            onCancelUpload = viewModel::cancelUpload,
            onOpenFile = { ref ->
                scope.launch {
                    try {
                        val url = viewModel.storedFileUrl(ref)
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: ActivityNotFoundException) { attachmentNotice = "No app can open this file." }
                    catch (failure: Exception) { attachmentNotice = failure.message ?: "File could not be opened." }
                }
            },
            onClearAttachments = { viewModel.clearAttachments(); attachmentNotice = "" },
            onOpenSettings = onOpenSettings,
            onOpenVoice = onOpenVoice,
            onOpenComputer = onOpenComputer,
            onOpenVoiceMode = onOpenVoiceMode,
            haptic = { kind -> if (viewModel.localSettings.value.haptic) view.performChatHaptic(kind) },
            onOpenSetup = onOpenSetup,
            onImagine = viewModel::setImagine,
            onImagineOptions = viewModel::setImagineOptions,
            onRunCode = viewModel::setRunCode,
            onOpenBuild = { viewModel.loadBuildRepositories() },
            onBuildRepository = viewModel::setBuildRepository,
            onRetryBuildRepositories = { viewModel.loadBuildRepositories(force = true) },
            onCreatePr = viewModel::createPullRequest,
            imagine = ImagineActions(
                onSave = { items ->
                    if (com.dua.ai.imagine.MediaExport.needsPermission() && androidx.core.content.ContextCompat.checkSelfPermission(context,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        pendingSave = items; storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else saveMedia(items)
                },
                onShare = { items ->
                    scope.launch {
                        try {
                            val files = items.map { item -> (viewModel.mediaCache.file(item) ?: error("This file is no longer available.")) to item }
                            context.startActivity(com.dua.ai.imagine.MediaExport.shareIntent(context, files))
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: ActivityNotFoundException) { attachmentNotice = "No app can share this file." }
                        catch (failure: Exception) { attachmentNotice = failure.message ?: "Could not share." }
                    }
                },
                onEdit = { media -> attachmentNotice = ""; viewModel.useGeneratedImage(media) },
                onAnimate = { media -> attachmentNotice = ""; viewModel.useGeneratedImage(media, com.dua.ai.imagine.ImagineInfo.VIDEO) },
                onCancelVideo = viewModel::cancelVideo,
                onReuse = { info ->
                    viewModel.setImagine(true)
                    viewModel.setImagineOptions(viewModel.imagineOptions.value.copy(kind = info.kind, aspectRatio = info.aspectRatio))
                    viewModel.updateDraft(info.prompt)
                },
            ),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMediaLoader provides viewModel.mediaCache) {
        ChatScreen(state, actions, attachRequest = attachRequest, onAttachRequestHandled = onAttachRequestHandled)
    }
}

private fun discardPending(captures: CameraCaptureStore, name: String) { if (name.isNotEmpty()) captures.discard(name) }

internal fun greetingFor(hour: Int, name: String?): String {
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val first = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    return if (first != null) "$part, $first" else part
}
