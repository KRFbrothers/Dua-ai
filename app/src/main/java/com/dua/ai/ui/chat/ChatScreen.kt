package com.dua.ai.ui.chat

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.dua.ai.ui.history.HistoryDrawer
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Chat surface: history drawer, top bar, message list or empty state, notices and composer.
 * Stateless apart from UI-only state (drawer, sheets, confirmation dialog).
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    actions: ChatActions,
    initialDrawerValue: DrawerValue = DrawerValue.Closed,
    initialSheet: ChatSheet? = null,
    /** Fullscreen media viewer: message id and index (screenshots open it directly). */
    initialViewer: Pair<String, Int>? = null,
    attachRequest: Int = 0,
    onAttachRequestHandled: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialDrawerValue)
    val scope = rememberCoroutineScope()
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    /** Empty Home (new chat): logo and tagline, full mode chip row. */
    val home = state.messages.isEmpty() && !state.typing
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf(initialViewer) }
    val mediaActions = remember(actions) { ImagineActions(onOpen = { id, index -> viewer = id to index }, onSave = actions.imagine.onSave, onShare = actions.imagine.onShare,
        onEdit = actions.imagine.onEdit, onAnimate = actions.imagine.onAnimate, onCancelVideo = actions.imagine.onCancelVideo, onReuse = actions.imagine.onReuse) }
    val conversationKey = state.chatId.ifEmpty { state.messages.firstOrNull()?.id.orEmpty() }
    val listState = key(conversationKey) {
        rememberLazyListState(initialFirstVisibleItemIndex = state.messages.lastIndex.coerceAtLeast(0))
    }
    var followTail by remember(conversationKey) { mutableStateOf(true) }
    var automaticScroll by remember(conversationKey) { mutableStateOf(false) }
    var dragging by remember(conversationKey) { mutableStateOf(false) }
    suspend fun goToLatest() {
        if (automaticScroll || dragging) return
        val latestIndex = listState.layoutInfo.totalItemsCount - 1
        if (latestIndex < 0) return
        automaticScroll = true
        try {
            listState.scrollToItem(latestIndex)
            // A large code block can exceed one scroll step; continue to its actual end.
            while (listState.canScrollForward) {
                if (listState.scrollBy(STREAM_FOLLOW_PX) <= 0f) break
            }
        } finally { automaticScroll = false }
    }
    val inputFocus = remember { FocusRequester() }
    // Mode toggles (chips and + sheet switches) give a light haptic.
    val tap = { actions.haptic(ChatHaptic.Tap) }
    val onWebMode: (String?) -> Unit = { tap(); actions.onWebMode(it) }
    val onReasoning: (Boolean) -> Unit = { tap(); actions.onReasoning(it) }
    val onXSearch: (Boolean) -> Unit = { tap(); actions.onXSearch(it) }
    val onImagine: (Boolean) -> Unit = { tap(); actions.onImagine(it) }
    val onRunCode: (Boolean) -> Unit = { tap(); actions.onRunCode(it) }
    // Swipe up on the input card or the chips above it opens the + sheet, except from inside the text field while typing.
    var inputFocused by remember { mutableStateOf(false) }
    var inputBounds by remember { mutableStateOf(Rect.Zero) }
    var bottomCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(attachRequest) { if (attachRequest > 0) { sheet = ChatSheet.Attach; onAttachRequestHandled() } }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> { dragging = true; followTail = false }
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragging = false
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listOf(listState.isScrollInProgress, listState.canScrollForward, automaticScroll, dragging) }
            .collect { (scrolling, moreBelow, automatic, userDragging) ->
                if (scrolling && !automatic) followTail = false
                if (!scrolling && !userDragging && !moreBelow) followTail = true
                // Markdown/media can grow after the initial message layout, even without a new token.
                if (followTail && moreBelow && !scrolling && !automatic && !userDragging) goToLatest()
            }
    }
    // Stop/completion never move a reader who has scrolled up.
    val lastUserId = state.messages.lastOrNull { it.isUser }?.id
    var previousUserId by remember(conversationKey) { mutableStateOf(lastUserId) }
    val streamingSize = state.messages.lastOrNull()?.takeIf { it.streaming }?.let { it.text.length + (it.thinking?.length ?: 0) } ?: -1
    val lastMessageId = state.messages.lastOrNull()?.id
    LaunchedEffect(conversationKey, lastUserId, lastMessageId, streamingSize) {
        val newPrompt = lastUserId != previousUserId
        if (newPrompt) {
            previousUserId = lastUserId
            followTail = true
        }
        if (followTail && (!listState.isScrollInProgress || newPrompt)) goToLatest()
    }
    fun closeDrawerThen(action: () -> Unit) { scope.launch { drawerState.close() }; action() }

    key(conversationKey) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Only swipe to close: an edge swipe to open would fight horizontal scrolling in code blocks.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                HistoryDrawer(
                    chats = state.savedChats.filter { it.projectId == state.projectId },
                    projectId = state.projectId,
                    newChatEnabled = !state.typing,
                    onNewChat = { closeDrawerThen(actions.onNewChat) },
                    onOpenChat = { id -> closeDrawerThen { actions.onOpenChat(id) } },
                    onDeleteChat = { id -> deletingId = id },
                    onRenameChat = actions.onRenameChat,
                    onOpenSettings = { closeDrawerThen(actions.onOpenSettings) },
                    onProjects = { closeDrawerThen { sheet = ChatSheet.Projects } },
                    onDestination = { destination -> closeDrawerThen { actions.onOpenDestination(destination) } },
                    profileName = state.profileName, activeChatId = state.chatId, drawerOpen = drawerState.isOpen,
                )
            },
        ) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                ChatTopBar(
                    newChatEnabled = !state.typing,
                    showComputer = home,
                    onMenu = { scope.launch { drawerState.open() } },
                    onComputer = actions.onOpenComputer,
                    onNewChat = actions.onNewChat,
                    projectName = state.projects.find { it.id == state.projectId }?.let { it.name + if (it.archived) " (archived)" else "" } ?: "Missing project",
                    onProjects = { sheet = ChatSheet.Projects },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (home) {
                        EmptyState(suggestions = if (state.draft.isBlank()) state.suggestions else emptyList(),
                            onSuggestion = { prompt -> actions.onDraftChange(prompt); inputFocus.requestFocus() })
                    } else {
                        key(conversationKey) {
                            MessageList(state = state, actions = actions, listState = listState, imagineActions = mediaActions)
                        }
                        if (!followTail && listState.canScrollForward) FilledTonalButton(
                            onClick = { scope.launch { followTail = true; goToLatest() } },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md)
                                .heightIn(min = Sizes.touchTarget).semantics { contentDescription = "Go to latest reply" },
                        ) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                            Text("Latest reply", modifier = Modifier.padding(start = Spacing.xs))
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth()
                        .onGloballyPositioned { bottomCoordinates = it }
                        .swipeUpToOpen(enabled = sheet == null && !state.typing,
                            isExcluded = { local -> inputFocused && bottomCoordinates?.let { inputBounds.contains(it.localToRoot(local)) } == true },
                            onSwipeUp = { tap(); sheet = ChatSheet.Attach })
                        .semantics { customActions = listOf(CustomAccessibilityAction("Open add to chat") { sheet = ChatSheet.Attach; true }) }) {
                        ChatNotices(state = state, actions = actions)
                        ModeChipRow(
                            state = state,
                            onWebMode = onWebMode,
                            onReasoning = onReasoning,
                            onXSearch = onXSearch,
                            onImagine = onImagine,
                            onRunCode = onRunCode,
                            onBuild = { sheet = ChatSheet.Build; actions.onOpenBuild() },
                            home = home,
                        )
                        Composer(
                            state = state,
                            focusRequester = inputFocus,
                            onDraftChange = actions.onDraftChange,
                            onAttach = { sheet = ChatSheet.Attach },
                            onClearAttachments = actions.onClearAttachments,
                            onMic = actions.onOpenVoice,
                            onVoiceMode = { actions.haptic(ChatHaptic.Tap); actions.onOpenVoiceMode() },
                            onTextFieldFocus = { inputFocused = it },
                            onTextFieldBounds = { inputBounds = it },
                            onSend = actions.onSend,
                            onStop = actions.onStop,
                            onModel = { sheet = ChatSheet.Model },
                            onCancelUpload = actions.onCancelUpload,
                            onImagineOptions = { sheet = ChatSheet.Imagine },
                        )
                    }
                }
            }
        }

    }
    when (sheet) {
        ChatSheet.Projects -> ProjectsSheet(state.projects, state.projectId, state.projectNotice, !state.typing,
            actions.onSelectProject, actions.onSaveProject, onDismiss = { sheet = null }, tasksEnabled = state.projectTasksEnabled,
            onSaveTask = actions.onSaveTask, onToggleTask = actions.onToggleTask, onDeleteTask = actions.onDeleteTask, dueDatesEnabled = state.taskDueDatesEnabled,
            memoryEnabled = state.projectMemoryEnabled, onSaveMemory = actions.onSaveProjectMemory)
        ChatSheet.Model -> ModelPickerSheet(
            current = state.model, providers = state.providers,
            onSelect = actions.onSelectModel, onOpenSetup = { sheet = null; actions.onOpenSetup() },
            onDismiss = { sheet = null },
        )
        ChatSheet.Attach -> AttachmentSheet(
            webMode = state.webMode, pageReadingAvailable = state.pageReadingAvailable,
            enabled = !state.typing && !state.readingFiles,
            onCamera = { sheet = null; actions.onPickCamera() },
            onPhotos = { sheet = null; actions.onPickPhotos() },
            onFiles = { sheet = null; actions.onPickFiles() },
            onReadLink = { on -> sheet = null; onWebMode(if (on) "page" else null) },
            onDismiss = { sheet = null },
            onAudio = { sheet = ChatSheet.Audio },
            storedFiles = state.storedFiles, storedFilesHint = state.storedFilesHint, storedFilesMaxBytes = state.storedFilesMaxBytes,
            modes = AttachModes(
                imagine = state.imagineConfig?.let { config -> if (config.enabled) "Create images from your message" else config.imageReason.ifBlank { "Turned off on the server" } },
                imagineOn = state.imagine,
                build = state.buildHint.ifEmpty { if (state.buildRepo != null) "On: ${state.buildRepo}" else "Chat about one of your GitHub repositories, read-only" },
                toggles = if (home) emptyList() else modeToggles(state, onWebMode, onReasoning, onXSearch, onRunCode),
            ),
            onTurnOffModes = if (state.auto) null else ({ sheet = null; actions.onAuto() }),
            onImagine = { on -> sheet = null; onImagine(on) },
            onBuild = { sheet = ChatSheet.Build; actions.onOpenBuild() },
        )
        ChatSheet.Audio -> AudioSheet(
            recordingSince = state.recordingSince, available = state.storedFiles, hint = state.storedFilesHint,
            onRecord = actions.onStartRecording,
            onStop = { actions.onStopRecording(); sheet = null },
            onCancel = actions.onCancelRecording,
            onPick = { sheet = null; actions.onPickAudio() },
            onDismiss = { sheet = null },
        )
        ChatSheet.Imagine -> state.imagineConfig?.let { config ->
            ImagineSheet(options = state.imagineOptions, config = config, usage = state.imagineUsage, hasSource = state.imagineSource,
                onChange = actions.onImagineOptions, onDismiss = { sheet = null })
        }
        ChatSheet.Build -> BuildSheet(
            current = state.buildRepo, hint = state.buildHint, repositories = state.buildRepositories, enabled = !state.typing,
            onSelect = { repository -> sheet = null; actions.onBuildRepository(repository) },
            onTurnOff = { sheet = null; actions.onBuildRepository(null) },
            onRetry = actions.onRetryBuildRepositories,
            onOpenSetup = { sheet = null; actions.onOpenSetup() },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    viewer?.let { (id, index) ->
        val media = state.messages.firstOrNull { it.id == id }?.media.orEmpty()
        if (media.isNotEmpty()) MediaViewer(media, index, mediaActions, state.videoAvailable, onDismiss = { viewer = null })
    }

    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Delete chat?") },
            text = { Text("Remove this chat from the phone cache? Review and upload in Cloud Sync to update server history. Stored files are kept.") },
            confirmButton = { TextButton(onClick = { actions.onDeleteChat(id); deletingId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancel") } },
        )
    }
}

/** Scrolls to the end of the growing reply; LazyColumn clamps at the content end. */
private const val STREAM_FOLLOW_PX = 100_000f
