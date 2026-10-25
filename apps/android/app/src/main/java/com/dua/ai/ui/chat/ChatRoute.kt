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
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.viewmodel.ChatViewModel
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import com.dua.ai.entry.EntryAction
import com.dua.ai.entry.ExternalEntry
import com.dua.ai.entry.LocalEntryDispatch
import com.dua.ai.ui.agent.AgentTask
import com.dua.ai.ui.agent.GITHUB_AGENT_PROMPT
import com.dua.ai.ui.agent.briefingPrompt

/** Binds [ChatScreen] to [ChatViewModel]. Activity-result launchers live here so they survive sheets closing. */
@Composable
fun ChatRoute(viewModel: ChatViewModel, voice: ReplyVoice, onOpenSettings: () -> Unit, onOpenVoice: () -> Unit, onOpenSetup: () -> Unit,
    onOpenDestination: (DuaDestination) -> Unit = {},
    onOpenComputer: () -> Unit = { onOpenDestination(DuaDestination.Computer) },
    onOpenVoiceMode: () -> Unit = {}, feature: ChatFeature? = null, homeTab: HomeTab = HomeTab.Chat,
    onExitFeature: (() -> Unit)? = null, topBar: (@Composable () -> Unit)? = null) {
    val preferences by viewModel.localSettings.collectAsState()
    val chatId by viewModel.activeChatId.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val editingId by viewModel.editingId.collectAsState()
    val savedChats by viewModel.savedChats.collectAsState()
    val projectId by viewModel.projectId.collectAsState()
    val projectNotice by viewModel.projectNotice.collectAsState()
    val model by viewModel.selectedModel.collectAsState()
    val webMode by viewModel.webMode.collectAsState()
    val reasoning by viewModel.reasoning.collectAsState()
    val agent by viewModel.agent.collectAsState()
    val agentAvailable by viewModel.agentAvailable.collectAsState()
    val freeModels by viewModel.freeModels.collectAsState()
    val freeModel by viewModel.freeModel.collectAsState()
    val xSearch by viewModel.xSearch.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val chatDeletion by viewModel.chatDeletion.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val readingInlineFiles by viewModel.readingFiles.collectAsState()
    val preparingFiles by viewModel.preparingFiles.collectAsState()
    val readingFiles = readingInlineFiles || preparingFiles
    val fileError by viewModel.attachmentError.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val error by viewModel.error.collectAsState()
    val failedPrompt by viewModel.failedPrompt.collectAsState()
    val online by viewModel.online.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val speech by voice.speaker.state.collectAsState()
    val generatedSpeech by voice.generated.state.collectAsState()
    val uploads by viewModel.uploads.collectAsState()
    val recordingSince by viewModel.recordingSince.collectAsState()
    val imagine by viewModel.imagine.collectAsState()
    val imagineSettings by viewModel.imagineSettings.collectAsState()
    val imagineOptions by viewModel.imagineOptions.collectAsState()
    val imagineUsage by viewModel.imagineUsage.collectAsState()
    val onDeviceStatus by viewModel.onDeviceAi.status.collectAsState()
    val polishing by viewModel.polishing.collectAsState()
    val polishNotice by viewModel.polishNotice.collectAsState()
    val writing by viewModel.writing.collectAsState()
    val runCode by viewModel.runCode.collectAsState()
    val buildRepo by viewModel.buildRepo.collectAsState()
    val buildRepositories by viewModel.buildRepositories.collectAsState()
    val buildPr by viewModel.buildPr.collectAsState()
    val scope = rememberCoroutineScope()
    var attachmentNotice by rememberSaveable { mutableStateOf("") }
    val dispatch = LocalEntryDispatch.current
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
        profileName = account.user?.displayName.orEmpty().ifBlank { account.user?.email.orEmpty() },
        profilePhotoUrl = account.user?.avatarUrl.orEmpty(),
        profileEmail = account.user?.email,
        chatId = chatId, messages = messages, draft = draft, editingId = editingId?.takeIf { id -> messages.any { it.id == id } }, typing = typing, error = error, offline = !online, model = model,
        feature = feature, homeTab = homeTab,
        providers = integrations.providers, webMode = webMode, reasoning = reasoning,
        webSearchConfigured = integrations.searchAvailable(model), pageReadingAvailable = integrations.pageReadingAvailable,
        xSearchAvailable = model == "Grok" && integrations.xSearchAvailable, xSearch = xSearch,
        agentAvailable = agentAvailable, agent = agent, freeModels = freeModels, freeModel = freeModel,
        summarisedThroughId = summary?.throughId,
        attachments = attachments, readingFiles = readingFiles,
        uploads = uploads, recordingSince = recordingSince,
        storedFiles = integrations.filesEnabled && account.user != null,
        inlineChatFiles = viewModel.inlineChatFilesAvailable(),
        storedFilesHint = viewModel.storedFilesHint(),
        storedFilesMaxBytes = integrations.filesMaxBytes,
        attachmentStatus = if (readingFiles) "Reading files…" else fileError.ifBlank { dispatch?.notice.orEmpty().ifBlank { attachmentNotice } },
        savedChats = savedChats,
        serverChatDeletionEnabled = integrations.chatDeletionEnabled && account.user != null,
        chatPinEnabled = integrations.chatPinEnabled,
        chatDeletion = chatDeletion,
        projects = preferences.projects, projectId = projectId, projectNotice = projectNotice, projectTasksEnabled = integrations.projectTasksEnabled, taskDueDatesEnabled = integrations.taskDueDatesEnabled, projectMemoryEnabled = integrations.projectMemoryEnabled,
        speakingId = speech.activeId ?: generatedSpeech.activeId,
        speechAvailable = speech.available, generatedVoice = preferences.generatedVoice,
        deviceVoiceStatus = speech.status, deviceVoiceActive = speech.activeId != null,
        generatedVoiceStatus = generatedSpeech.status, generatedVoiceActive = generatedSpeech.activeId != null,
        greeting = greeting, suggestions = viewModel.promptSuggestions,
        imagine = imagine, imagineOptions = imagineOptions, imagineConfig = imagineSettings, imagineUsage = imagineUsage,
        promptPolish = if (onDeviceStatus != null && viewModel.onDeviceAiEnabled() && preferences.onDeviceAi) PromptPolishState(polishing, polishNotice) else null,
        removeBackground = viewModel.backgroundRemovalAvailable,
        writing = writing,
        runCode = runCode, codeConfig = integrations.codeExecution,
        codeSnippetUnavailableReason = viewModel.codeSnippetUnavailableReason(),
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
    // Remove background: cut out on the phone, then save the transparent PNG with the same gallery path as Save.
    var pendingCutout by remember { mutableStateOf<(suspend () -> ByteArray?)?>(null) }
    fun saveCutout(load: suspend () -> ByteArray?) {
        scope.launch {
            attachmentNotice = "Removing the background on this phone…"
            try {
                val bytes = load() ?: error("This image is no longer available.")
                val file = viewModel.removeBackground(bytes)
                com.dua.ai.imagine.MediaExport.saveToGallery(context, file, com.dua.ai.imagine.GeneratedMedia(file.path, "image/png", file.length()))
                attachmentNotice = "Image without background saved to Pictures/Dua."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { attachmentNotice = failure.message ?: "Could not remove the background." }
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val items = pendingSave; pendingSave = emptyList()
        val cutout = pendingCutout; pendingCutout = null
        if (!granted) attachmentNotice = "Storage permission is needed to save on this Android version."
        else if (cutout != null) saveCutout(cutout) else saveMedia(items)
    }
    fun removeBackground(load: suspend () -> ByteArray?) {
        if (com.dua.ai.imagine.MediaExport.needsPermission() && androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingCutout = load; storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else saveCutout(load)
    }
    val actions = remember(viewModel, voice) {
        ChatActions(
            onDraftChange = viewModel::updateDraft,
            onSend = {
                if (viewModel.localSettings.value.haptic) view.performChatHaptic(ChatHaptic.Confirm)
                voice.stopAll()
                val current = viewModel.draft.value
                if ((current.isNotBlank() || viewModel.attachments.value.isNotEmpty() || viewModel.uploads.value.any { it.ref != null }) && !viewModel.isTyping.value && !viewModel.readingFiles.value && !viewModel.preparingFiles.value)
                    viewModel.sendMessage(current)
            },
            onSendReply = { quote ->
                if (viewModel.localSettings.value.haptic) view.performChatHaptic(ChatHaptic.Confirm)
                voice.stopAll()
                val current = viewModel.draft.value
                if (current.isNotBlank() && !viewModel.isTyping.value && !viewModel.readingFiles.value && !viewModel.preparingFiles.value)
                    viewModel.sendMessage(ReplyQuote.compose(quote, current))
            },
            onStop = { pulse(); viewModel.stopReply() },
            onRetry = { id -> voice.stopAll(); viewModel.retryResponse(id) },
            onEditMessage = viewModel::startEdit,
            onCancelEdit = viewModel::cancelEdit,
            retryWebMode = viewModel::replyWebMode,
            onSpeak = { message -> voice.toggle(message, viewModel.localSettings.value.generatedVoice) },
            onStopSpeech = voice::stopAll,
            onUseDeviceVoice = { voice.stopAll(); viewModel.updateSettings(viewModel.localSettings.value.copy(generatedVoice = false)) },
            onWebMode = viewModel::setWebMode,
            onReasoning = viewModel::setReasoning,
            onAgent = viewModel::setAgent,
            onXSearch = viewModel::setXSearch,
            onAuto = viewModel::selectAuto,
            onSelectModel = viewModel::setModel,
            onSelectFreeModel = viewModel::selectFreeModel,
            onNewChat = { pulse(); voice.stopAll(); viewModel.startNewChat(); viewModel.clearAttachments(); attachmentNotice = ""; if (homeTab == HomeTab.Work) onOpenDestination(DuaDestination.Chat) },
            onExitFeature = { if (!viewModel.dataBusy.value) { voice.stopAll(); if (onExitFeature != null) onExitFeature() else { viewModel.startNewChat(); viewModel.selectAuto() } } },
            onNewFeatureSession = { if (!viewModel.dataBusy.value && !viewModel.isTyping.value) {
                voice.stopAll(); viewModel.startNewChat(); attachmentNotice = ""
                if (feature == ChatFeature.Agent || viewModel.agent.value) viewModel.setAgent(true)
            } },
            onRefreshAgent = viewModel::refreshEngineCapabilities,
            onAgentTask = { task ->
                if (viewModel.agentAvailable.value && !viewModel.isTyping.value && !viewModel.dataBusy.value) {
                    voice.stopAll(); viewModel.startNewChat(); viewModel.setAgent(true)
                    val prompt = when (task) {
                        AgentTask.Research -> ""
                        AgentTask.GitHub -> GITHUB_AGENT_PROMPT
                        AgentTask.Briefing -> briefingPrompt()
                    }
                    viewModel.updateDraft(prompt)
                    if (task == AgentTask.Briefing) viewModel.sendMessage(prompt)
                }
            },
            onSelectProject = { id -> voice.stopAll(); viewModel.selectProject(id) },
            onSaveProject = viewModel::saveProject,
            onCreateProject = { name, instructions, icon, color -> viewModel.createProject(name, instructions, icon, color) != null },
            onSaveProjectMemory = viewModel::saveProjectMemory,
            onSaveTask = viewModel::saveTask, onToggleTask = viewModel::toggleTask, onDeleteTask = viewModel::deleteTask,
            onOpenChat = { id -> pulse(); voice.stopAll(); viewModel.openChat(id); viewModel.clearAttachments(); if (homeTab == HomeTab.Work) onOpenDestination(DuaDestination.Chat) },
            onDeleteChat = viewModel::deleteChat,
            onRenameChat = viewModel::renameChat,
            onPinChat = viewModel::setChatPinned,
            onDeleteServerChat = viewModel::deleteServerChat,
            onDismissChatDeletion = viewModel::dismissChatDeletion,
            onPickCamera = {
                pick {
                    val file = captures.create()
                    pendingCapture = file.name
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.camera", file))
                }
            },
            onPickPhotos = { pick { files.launch(arrayOf("image/jpeg", "image/png", "image/webp")) } },
            onDrop = { drop ->
                attachmentNotice = drop.notice
                if (drop.text.isNotEmpty()) {
                    val current = viewModel.draft.value
                    viewModel.updateDraft(if (current.isBlank()) drop.text else current.trimEnd() + "\n" + drop.text)
                }
                if (drop.uris.isNotEmpty()) viewModel.selectAttachments(drop.uris)
            },
            onPickFiles = { pick { files.launch(
                if (viewModel.inlineChatFilesAvailable()) AttachmentReader.types + com.dua.ai.files.StoredFileTypes.AUDIO_PICKER
                else if (viewModel.storedFilesAvailable()) AttachmentReader.types + com.dua.ai.files.StoredFileTypes.PICKER_EXTRA else AttachmentReader.types) } },
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
            onRemoveAttachment = viewModel::removeAttachment,
            onRemoveAttachmentBackground = { index -> viewModel.attachmentImageBytes(index)?.let { bytes -> removeBackground { bytes } } },
            onPolishPrompt = viewModel::polishImaginePrompt,
            onWriting = viewModel::openWriting,
            writing = WritingActions(onStyle = { style -> viewModel.runWriting(com.dua.ai.ondevice.WritingTask.Rewrite(style)) },
                onUse = viewModel::useWritingResult, onAskServer = viewModel::askServerForWriting, onDismiss = viewModel::closeWriting),
            onOpenSettings = onOpenSettings,
            // Drawer and Home use the same destination dispatch, including the existing Computer screen.
            onOpenDestination = { destination ->
                if (destination == DuaDestination.Computer) onOpenComputer() else onOpenDestination(destination)
            },
            onOpenVoice = onOpenVoice,
            onOpenComputer = onOpenComputer,
            onOpenVoiceMode = onOpenVoiceMode,
            haptic = { kind -> if (viewModel.localSettings.value.haptic) view.performChatHaptic(kind) },
            inlineDictation = true,
            dictationLanguage = { viewModel.localSettings.value.inputLanguage },
            dictationAutoSend = { viewModel.localSettings.value.dictationAutoSend },
            onDictationNotice = { attachmentNotice = it },
            dictationOfflineModel = { phoneAvailable ->
                val settings = viewModel.localSettings.value
                val models = com.dua.ai.voice.offline.OfflineModelManager.get(context)
                models.ready(settings.offlineModel)?.takeIf {
                    com.dua.ai.voice.offline.OfflineModels.useOffline(settings.offlineDictation, true, phoneAvailable)
                }
            },
            onOpenSetup = onOpenSetup,
            onImagine = viewModel::setImagine,
            onImagineOptions = viewModel::setImagineOptions,
            onRunCode = viewModel::setRunCode,
            onRunCodeSnippet = { code -> voice.stopAll(); viewModel.runCodeSnippet(code) },
            onOpenBuild = { viewModel.loadBuildRepositories() },
            onBuildRepository = viewModel::setBuildRepository,
            onRetryBuildRepositories = { viewModel.loadBuildRepositories(force = true) },
            onCreatePr = viewModel::createPullRequest,
            onAgentApproval = viewModel::decideAgentApproval,
            imagine = ImagineActions(
                onRemoveBackground = if (!viewModel.backgroundRemovalAvailable) null else { item ->
                    removeBackground { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        viewModel.mediaCache.file(item)?.takeIf { it.length() in 1..MAX_CUTOUT_SOURCE }?.readBytes() } }
                },
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
    var entryFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var entryUiKey by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(dispatch?.entry?.id, dispatch?.ready) {
        val entry = dispatch?.entry ?: return@LaunchedEffect
        if (!dispatch.ready) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
        if (viewModel.dataBusy.value || viewModel.isTyping.value || viewModel.readingFiles.value || viewModel.preparingFiles.value) return@LaunchedEffect
        // Direct Share to a chat: open that chat first (before attachments are accepted, since switching clears them).
        val sharedChat = entry.chatId?.takeIf { entry.action == EntryAction.Share }
        val missingChat = sharedChat != null && viewModel.savedChats.value.none { it.id == sharedChat }
        if (sharedChat != null && !missingChat && viewModel.activeChatId.value != sharedChat) viewModel.openChat(sharedChat)
        if (entry.action == EntryAction.Share && entry.uris.isNotEmpty() && !viewModel.acceptSharedAttachments(entry.uris)) return@LaunchedEffect
        dispatch.consumed(entry.id)
        entryUiKey = entry.id
        attachmentNotice = listOf(entry.notice, if (missingChat) "That chat is no longer on this phone, so the shared item was added here." else "")
            .filter { it.isNotBlank() }.joinToString(" ")
        when (entry.action) {
            EntryAction.Share -> {
                viewModel.updateDraft(ExternalEntry.mergedDraft(viewModel.draft.value, entry.text))
                entryFocus = entry.id
            }
            EntryAction.Query -> entryFocus = entry.id
            EntryAction.NewChat -> { voice.stopAll(); viewModel.startNewChat(); entryFocus = entry.id }
            EntryAction.Imagine -> { voice.stopAll(); viewModel.startNewChat(); viewModel.setImagine(true); entryFocus = entry.id }
            EntryAction.Camera -> actions.onPickCamera()
            EntryAction.Photos -> actions.onPickPhotos()
            EntryAction.Files -> actions.onPickFiles()
            else -> Unit
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMediaLoader provides viewModel.mediaCache, LocalChatHaptic provides actions.haptic) {
        ChatScreen(state, actions, topBar = topBar, requestComposerFocus = entryFocus, dismissOverlaysRequest = entryUiKey,
            useSuppliedChatUi = feature == null && homeTab == HomeTab.Chat && topBar == null)
    }
}

private fun discardPending(captures: CameraCaptureStore, name: String) { if (name.isNotEmpty()) captures.discard(name) }

internal fun greetingFor(hour: Int, name: String?): String {
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val first = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    return if (first != null) "$part, $first" else part
}

/** Largest Imagine image read for "Remove background" (generated images are far smaller). */
private const val MAX_CUTOUT_SOURCE = 25L * 1024 * 1024
