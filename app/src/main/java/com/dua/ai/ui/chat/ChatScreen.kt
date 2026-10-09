package com.dua.ai.ui.chat

import androidx.compose.foundation.gestures.scrollBy
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
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(attachRequest) { if (attachRequest > 0) { sheet = ChatSheet.Attach; onAttachRequestHandled() } }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    LaunchedEffect(state.messages.size, state.typing) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    // Follow a streaming reply as it grows, unless the reader has scrolled up to earlier messages.
    val streamingSize = state.messages.lastOrNull()?.takeIf { it.streaming }?.let { it.text.length + (it.thinking?.length ?: 0) } ?: -1
    LaunchedEffect(streamingSize) {
        if (streamingSize <= 0) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= listState.layoutInfo.totalItemsCount - 1 && listState.canScrollForward) listState.scrollBy(STREAM_FOLLOW_PX)
    }
    fun closeDrawerThen(action: () -> Unit) { scope.launch { drawerState.close() }; action() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only swipe to close: an edge swipe to open would fight horizontal scrolling in code blocks.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            HistoryDrawer(
                chats = state.savedChats,
                newChatEnabled = !state.typing,
                onNewChat = { closeDrawerThen(actions.onNewChat) },
                onOpenChat = { id -> closeDrawerThen { actions.onOpenChat(id) } },
                onDeleteChat = { id -> deletingId = id },
                onOpenSettings = { closeDrawerThen(actions.onOpenSettings) },
            )
        },
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            ChatTopBar(
                model = state.modelLabel,
                newChatEnabled = !state.typing,
                onMenu = { scope.launch { drawerState.open() } },
                onModel = { sheet = ChatSheet.Model },
                onNewChat = actions.onNewChat,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (home) {
                    EmptyState()
                } else {
                    MessageList(state = state, actions = actions, listState = listState, imagineActions = mediaActions)
                }
            }
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth()) {
                    ChatNotices(state = state, actions = actions)
                    ModeChipRow(
                        state = state,
                        onWebMode = actions.onWebMode,
                        onReasoning = actions.onReasoning,
                        onXSearch = actions.onXSearch,
                        onImagine = actions.onImagine,
                        onRunCode = actions.onRunCode,
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
                        onSend = actions.onSend,
                        onStop = actions.onStop,
                        onAuto = actions.onAuto,
                        onCancelUpload = actions.onCancelUpload,
                        onImagineOptions = { sheet = ChatSheet.Imagine },
                    )
                }
            }
        }
    }

    when (sheet) {
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
            onReadLink = { on -> sheet = null; actions.onWebMode(if (on) "page" else null) },
            onDismiss = { sheet = null },
            onAudio = { sheet = ChatSheet.Audio },
            storedFiles = state.storedFiles, storedFilesHint = state.storedFilesHint, storedFilesMaxBytes = state.storedFilesMaxBytes,
            modes = AttachModes(
                imagine = state.imagineConfig?.let { config -> if (config.enabled) "Create images from your message" else config.imageReason.ifBlank { "Turned off on the server" } },
                imagineOn = state.imagine,
                build = state.buildHint.ifEmpty { if (state.buildRepo != null) "On: ${state.buildRepo}" else "Chat about one of your GitHub repositories, read-only" },
                toggles = if (home) emptyList() else modeToggles(state, actions),
            ),
            onImagine = { on -> sheet = null; actions.onImagine(on) },
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
            text = { Text("Delete this chat from this device? Files uploaded or generated in this chat are deleted from your storage too.") },
            confirmButton = { TextButton(onClick = { actions.onDeleteChat(id); deletingId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancel") } },
        )
    }
}

/** Scrolls to the end of the growing reply; LazyColumn clamps at the content end. */
private const val STREAM_FOLLOW_PX = 100_000f
