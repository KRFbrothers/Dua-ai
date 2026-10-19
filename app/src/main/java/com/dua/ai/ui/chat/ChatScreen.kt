package com.dua.ai.ui.chat

import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.theme.SheetTheme
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.dua.ai.ui.history.HistoryDrawer
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.DrawerSizes
import com.dua.ai.ui.theme.HomeSizes
import com.dua.ai.ui.navigation.DuaDestination
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.dua.ai.model.ChatMessage
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.agent.AgentContent
import com.dua.ai.ui.agent.AgentModePill
import com.dua.ai.ui.screens.ProjectsFeaturePage


private data class ConversationResults(val messages: List<ChatMessage>, val query: String, val matches: List<ConversationMatch>)

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
    topBar: (@Composable () -> Unit)? = null,
    requestComposerFocus: String? = null,
    dismissOverlaysRequest: String? = null,
) {
    val drawerState = rememberDrawerState(initialDrawerValue)
    var lastDrawerValue by remember { mutableStateOf(drawerState.currentValue) }
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue != lastDrawerValue) {
            lastDrawerValue = drawerState.currentValue
            actions.haptic(ChatHaptic.Tap)
        }
    }
    val scope = rememberCoroutineScope()
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    LaunchedEffect(dismissOverlaysRequest) { if (dismissOverlaysRequest != null) sheet = null }
    if (sheet == ChatSheet.Projects) {
        BackHandler { sheet = null }
        ProjectsFeaturePage(state, actions, onBack = { sheet = null })
        return
    }
    var taskHome by rememberSaveable { mutableStateOf(false) }
    val feature = state.activeFeature.takeUnless { taskHome && state.feature == null && it == ChatFeature.Agent }
    // Switching + → Agent keeps the existing request context and attachments, but shows only
    // turns written in the Agent view. The saved conversation and handlers stay unchanged.
    var agentStartAfterId by rememberSaveable(state.chatId) { mutableStateOf<String?>(null) }
    val boundaryIndex = state.messages.indexOfFirst { it.id == agentStartAfterId }
    val visibleState = if (state.agent && !taskHome && boundaryIndex >= 0) state.copy(messages = state.messages.drop(boundaryIndex + 1)) else state
    /** Empty conversations show Home's Chat/Task hero or their dedicated feature starters. */
    val home = visibleState.messages.isEmpty() && !state.typing
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingServerId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.chatDeletion.id, state.chatDeletion.completed) {
        if (state.chatDeletion.completed && state.chatDeletion.id == deletingServerId) {
            deletingServerId = null; actions.onDismissChatDeletion()
        }
    }
    var viewer by remember { mutableStateOf(initialViewer) }
    val mediaActions = remember(actions) { ImagineActions(onOpen = { id, index -> viewer = id to index }, onSave = actions.imagine.onSave, onShare = actions.imagine.onShare,
        onEdit = actions.imagine.onEdit, onAnimate = actions.imagine.onAnimate, onCancelVideo = actions.imagine.onCancelVideo, onReuse = actions.imagine.onReuse,
        onRemoveBackground = actions.imagine.onRemoveBackground) }
    val conversationKey = "${feature?.name}:" + state.chatId.ifEmpty { state.messages.firstOrNull()?.id.orEmpty() }
    var searchOpen by rememberSaveable(conversationKey) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(conversationKey) { mutableStateOf("") }
    var selectedSearchId by rememberSaveable(conversationKey) { mutableStateOf<String?>(null) }
    var searchResults by remember(conversationKey) { mutableStateOf<ConversationResults?>(null) }
    var readyQuery by remember(conversationKey) { mutableStateOf<String?>(null) }
    var lastSearchJump by remember(conversationKey) { mutableStateOf<Triple<String, String, Int>?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissInput() { focusManager.clearFocus(); keyboard?.hide() }
    LaunchedEffect(sheet) {
        if (sheet != null) dismissInput()
    }
    val needle = searchQuery.trim()
    // Incoming tokens must not restart the query's debounce timer.
    LaunchedEffect(searchOpen, needle) {
        readyQuery = null
        if (!searchOpen || needle.isEmpty()) return@LaunchedEffect
        delay(150)
        readyQuery = needle
    }
    LaunchedEffect(searchOpen, state.messages, needle, readyQuery) {
        searchResults = null
        if (!searchOpen || needle.isEmpty() || readyQuery != needle) return@LaunchedEffect
        val matches = withContext(Dispatchers.Default) { ConversationSearch.search(state.messages, needle) { ensureActive() } }
        searchResults = ConversationResults(state.messages, needle, matches)
    }
    val matches = searchResults?.takeIf { searchOpen && it.messages == state.messages && it.query == needle }?.matches.orEmpty()
    val matchIndex = matches.indexOfFirst { it.messageId == selectedSearchId }.coerceAtLeast(0)
    val selectedMatch = matches.getOrNull(matchIndex)
    val listState = key(conversationKey) {
        rememberLazyListState(initialFirstVisibleItemIndex = state.messages.lastIndex.coerceAtLeast(0))
    }
    var followTail by remember(conversationKey) { mutableStateOf(true) }
    var automaticScroll by remember(conversationKey) { mutableStateOf(false) }
    var dragging by remember(conversationKey) { mutableStateOf(false) }
    suspend fun goToLatest() {
        if (searchOpen || automaticScroll || dragging) return
        val latestIndex = listState.layoutInfo.totalItemsCount - 1
        if (latestIndex < 0) return
        automaticScroll = true
        try {
            listState.scrollToItem(latestIndex)
            // A large code block can exceed one scroll step; continue to its actual end.
            while (!searchOpen && listState.canScrollForward) {
                if (listState.scrollBy(STREAM_FOLLOW_PX) <= 0f) break
            }
        } finally { automaticScroll = false }
    }
    fun closeSearch() {
        followTail = false; searchOpen = false; searchQuery = ""; selectedSearchId = null; lastSearchJump = null
        focusManager.clearFocus(); keyboard?.hide()
    }
    BackHandler(enabled = searchOpen && !drawerState.isOpen && sheet == null) { closeSearch() }
    LaunchedEffect(searchOpen, selectedMatch?.messageId, selectedMatch?.messageIndex, needle) {
        val target = selectedMatch?.let { Triple(needle, it.messageId, it.messageIndex) }
        if (searchOpen && selectedMatch != null && target != lastSearchJump) {
            selectedSearchId = selectedMatch.messageId
            followTail = false
            listState.scrollToItem(selectedMatch.messageIndex)
            lastSearchJump = target
        }
    }
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(dismissOverlaysRequest) {
        if (dismissOverlaysRequest != null) { searchOpen = false; viewer = null; drawerState.close() }
    }
    LaunchedEffect(requestComposerFocus) {
        if (requestComposerFocus != null) {
            androidx.compose.runtime.withFrameNanos { }
            inputFocus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(state.editingId) { if (state.editingId != null) runCatching { inputFocus.requestFocus() } }
    // Mode toggles (chips and + sheet switches) give a light haptic.
    val tap = { actions.haptic(ChatHaptic.Tap) }
    val onWebMode: (String?) -> Unit = { tap(); if (it != null) taskHome = false; actions.onWebMode(it) }
    val onReasoning: (Boolean) -> Unit = { tap(); if (it) taskHome = false; actions.onReasoning(it) }
    val onAgent: (Boolean) -> Unit = { enabled ->
        tap()
        taskHome = false
        if (enabled && !state.agent) agentStartAfterId = state.messages.lastOrNull()?.id
        actions.onAgent(enabled)
        if (enabled) sheet = null
    }
    val onXSearch: (Boolean) -> Unit = { tap(); if (it) taskHome = false; actions.onXSearch(it) }
    val onImagine: (Boolean) -> Unit = { tap(); if (it) taskHome = false; actions.onImagine(it) }
    val onRunCode: (Boolean) -> Unit = { tap(); if (it) taskHome = false; actions.onRunCode(it) }
    val taskReady = !taskHome || (state.agent && state.agentAvailable)
    val sendFromComposer: () -> Unit = { if (taskReady) actions.onSend() }
    // Swipe up on the input card or the chips above it opens the + sheet, except from inside the text field while typing.
    var inputFocused by remember { mutableStateOf(false) }
    var inputBounds by remember { mutableStateOf(Rect.Zero) }
    var bottomCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BackHandler(enabled = feature != null && sheet == null && viewer == null && !searchOpen) { actions.onExitFeature() }
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
                if (!searchOpen && !scrolling && !userDragging && !moreBelow) followTail = true
                // Markdown/media can grow after the initial message layout, even without a new token.
                if (!searchOpen && followTail && moreBelow && !scrolling && !automatic && !userDragging) goToLatest()
            }
    }
    // Messages the reader has seen at the bottom; later Dua replies show as "new" on the Latest reply pill.
    var seenMessages by remember(conversationKey) { mutableStateOf(state.messages.size) }
    LaunchedEffect(followTail, state.messages.size) { if (followTail) seenMessages = state.messages.size }
    // Keyboard opening shrinks the list; keep the newest message visible when the reader was at the bottom (Jetchat).
    val imeBottom = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    LaunchedEffect(imeBottom) { if (imeBottom > 0 && followTail && !searchOpen) goToLatest() }
    // Stop/completion never move a reader who has scrolled up.
    val lastUserId = state.messages.lastOrNull { it.isUser }?.id
    var previousUserId by remember(conversationKey) { mutableStateOf(lastUserId) }
    val streamingSize = state.messages.lastOrNull()?.takeIf { it.streaming }?.let { it.text.length + (it.thinking?.length ?: 0) } ?: -1
    val lastMessageId = state.messages.lastOrNull()?.id
    val streamHaptic = remember(conversationKey) { StreamHapticThrottle() }
    LaunchedEffect(streamingSize) {
        if (streamingSize > 0) streamHaptic.tick { actions.haptic(ChatHaptic.Tap) }
    }
    LaunchedEffect(conversationKey, lastUserId, lastMessageId, streamingSize) {
        val newPrompt = lastUserId != previousUserId
        if (newPrompt) {
            previousUserId = lastUserId
            if (!searchOpen) followTail = true
        }
        if (followTail && (!listState.isScrollInProgress || newPrompt)) goToLatest()
    }
    fun closeDrawerThen(action: () -> Unit) { scope.launch { drawerState.close() }; action() }

    var dropHover by remember { mutableStateOf(false) }
    val dropMotion = com.dua.ai.ui.theme.rememberMotionAllowed()
    key(conversationKey) {
        BoxWithConstraints(Modifier.fillMaxSize().chatDropTarget(enabled = sheet == null && viewer == null,
            maxBytes = state.storedFilesMaxBytes, onHover = { dropHover = it }, onDrop = actions.onDrop)) {
            // Keep the entire right-hand Home touch target in the scrim, including on narrow windows.
            val endInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
                .calculateEndPadding(LocalLayoutDirection.current)
            val controlInset = maxOf(HomeSizes.topBarPaddingH, HomeSizes.composerHorizontalMargin(maxWidth) + HomeSizes.barPaddingH)
            val drawerWidth = minOf(DrawerSizes.width, (maxWidth - endInset - controlInset - Sizes.touchTarget).coerceAtLeast(0.dp))
            ModalNavigationDrawer(
                drawerState = drawerState,
                // Only swipe to close: an edge swipe to open would fight horizontal scrolling in code blocks.
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    if (feature == null) HistoryDrawer(
                        drawerState = drawerState,
                        profileName = state.profileName, profilePhotoUrl = state.profilePhotoUrl,
                        onOpenSettings = { closeDrawerThen(actions.onOpenSettings) },
                        chats = state.savedChats.filter { it.projectId == state.projectId },
                        currentChatId = state.chatId,
                        newChatEnabled = !state.typing,
                        onNewChat = { if (!state.typing) closeDrawerThen { taskHome = false; actions.onNewChat() } },
                        onSelectChat = { id -> closeDrawerThen { taskHome = false; actions.onOpenChat(id) } },
                        onDeleteChat = { id -> deletingId = id },
                        onRenameChat = { id, title -> actions.onRenameChat(id, title) },
                        pinEnabled = state.chatPinEnabled,
                        onPinChat = { id, pinned -> actions.onPinChat(id, pinned) },
                        serverDeletionEnabled = state.serverChatDeletionEnabled,
                        onDeleteServerChat = { id -> actions.onDismissChatDeletion(); deletingServerId = id },
                        onProjects = { closeDrawerThen { taskHome = false; actions.onOpenDestination(DuaDestination.Projects) } },
                        onDestination = { destination -> closeDrawerThen { taskHome = false; actions.onOpenDestination(destination) } },
                        modifier = Modifier.width(drawerWidth),
                    )
                },
            ) {
                Column(Modifier.fillMaxSize().background(com.dua.ai.ui.theme.ChatTheme.colors.background)
                    .let { if (feature == null) it.windowInsetsPadding(
                        WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)) else it }
                    .then(if (feature == null && state.homeTab != HomeTab.Work) Modifier.homeBackdrop(taskHome && home, home) else Modifier)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                    if (topBar != null) topBar()
                    else if (feature != null) FeatureTopBar(feature.title, actions.onExitFeature, actions.onNewFeatureSession,
                        newSessionEnabled = !state.typing && (feature != ChatFeature.Agent || state.agentAvailable))
                    else ChatTopBar(
                        onMenu = { dismissInput(); scope.launch { drawerState.open() } },
                        task = taskHome, modelName = homeModelName(state),
                        profileName = state.profileName, profilePhotoUrl = state.profilePhotoUrl,
                        onModel = { sheet = ChatSheet.Model }, onProfile = { sheet = ChatSheet.Profile },
                        onNewChat = actions.onNewChat, newChatEnabled = !state.typing,
                        onFindInChat = if (state.messages.isEmpty() || state.homeTab == HomeTab.Work) null else ({ followTail = false; searchOpen = true }),
                    )
                    if (searchOpen) ConversationSearchBar(searchQuery,
                        onQuery = { searchQuery = it; selectedSearchId = null; lastSearchJump = null },
                        searching = needle.isNotEmpty() && searchResults?.takeIf { it.messages == state.messages && it.query == needle } == null,
                        matches = matches, currentIndex = matchIndex,
                        onPrevious = { if (matches.isNotEmpty()) selectedSearchId = matches[(matchIndex - 1 + matches.size) % matches.size].messageId },
                        onNext = { if (matches.isNotEmpty()) selectedSearchId = matches[(matchIndex + 1) % matches.size].messageId },
                        onClose = { closeSearch() })
                    Box(Modifier.weight(1f).fillMaxWidth().let { canvas ->
                        if (home && feature == null) canvas.pointerInput(Unit) { detectTapGestures { dismissInput() } } else canvas
                    }) {
                        if (feature == null && state.homeTab == HomeTab.Work) {
                            Text("Coming soon", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge)
                        } else if (home && feature == ChatFeature.Agent) {
                            AgentContent(state.agentAvailable, busy = state.typing, onBack = actions.onExitFeature,
                                onNewTask = actions.onNewFeatureSession, onRefresh = actions.onRefreshAgent,
                                onTask = actions.onAgentTask, onSend = {}, showChrome = false)
                        } else if (home && feature != null) {
                            Text(if (feature == ChatFeature.Projects) state.projects.firstOrNull { it.id == state.projectId }?.name ?: "Projects" else "Create an image with Dua",
                                Modifier.align(Alignment.Center).padding(Spacing.lg), style = MaterialTheme.typography.titleLarge)
                        } else if (home) {
                            ChatContent(task = taskHome, profileName = state.profileName,
                                composing = state.draft.isNotBlank() || state.attachments.isNotEmpty() || state.uploads.isNotEmpty(),
                                onTask = { prompt -> actions.onDraftChange(prompt); inputFocus.requestFocus(); keyboard?.show() })
                        } else {
                            key(conversationKey) {
                                androidx.compose.runtime.CompositionLocalProvider(LocalCodeExecutionUi provides CodeExecutionUi(
                                    state.codeSnippetUnavailableReason, actions.onRunCodeSnippet)) {
                                    MessageList(state = visibleState, actions = actions, listState = listState, imagineActions = mediaActions,
                                        searchMessageId = selectedMatch?.messageId)
                                }
                            }
                            JumpToLatest(visible = !searchOpen && !followTail && listState.canScrollForward,
                                newReplies = MessageGrouping.newReplies(state.messages, seenMessages),
                                onClick = { scope.launch { followTail = true; goToLatest() } },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md))
                        }
                    }
                    if (feature != null || state.homeTab != HomeTab.Work) Column(Modifier.fillMaxWidth()
                        .let { if (feature != null) it.imePadding().navigationBarsPadding() else it }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.widthIn(max = if (feature == null) HomeSizes.composerMaxWidth else Sizes.contentMaxWidth).fillMaxWidth()
                            .onGloballyPositioned { bottomCoordinates = it }
                            .swipeUpToOpen(enabled = sheet == null && !state.typing,
                                isExcluded = { local -> inputFocused && bottomCoordinates?.let { inputBounds.contains(it.localToRoot(local)) } == true },
                                onSwipeUp = { tap(); sheet = ChatSheet.Attach })
                            .semantics { customActions = listOf(CustomAccessibilityAction("Open add to chat") { sheet = ChatSheet.Attach; true }) }) {
                            ChatNotices(state = state, actions = actions)
                            if (state.editingId != null) EditingBanner(onCancel = actions.onCancelEdit)
                            val visibleModes = modeToggles(state, onWebMode, onReasoning, onXSearch, onRunCode, onAgent)
                                .filter { feature == null && !home && it.on && !(taskHome && it.label == "Agent") }
                            if (feature == ChatFeature.Agent) Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), contentAlignment = Alignment.Center) { AgentModePill() }
                            if (visibleModes.isNotEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().let { if (visibleModes.size > 3) it.horizontalScroll(rememberScrollState()) else it }
                                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    val fill = visibleModes.size <= 3
                                    visibleModes.forEach { toggle -> HomeActionButton(toggle, if (fill) Modifier.weight(1f) else Modifier) }
                                }
                            }
                            val dictation = if (actions.inlineDictation) rememberChatDictation(actions.dictationLanguage, actions.dictationAutoSend,
                                draft = rememberUpdatedState(state.draft).let { d -> remember { { d.value } } }, onDraft = actions.onDraftChange, onSend = sendFromComposer, onNotice = actions.onDictationNotice) else null
                            Composer(
                                homeLayout = feature == null,
                                dictation = dictation,
                                // Only what the composer needs, so streamed tokens do not recompose it.
                                controlled = true,
                                typing = state.typing,
                                uploading = state.uploads.any { it.uploading },
                                focusRequester = inputFocus,
                                onDraftChange = actions.onDraftChange,
                                onAttach = { sheet = ChatSheet.Attach },
                                onClearAttachments = actions.onClearAttachments,
                                onMic = actions.onOpenVoice,
                                onVoiceClick = { actions.haptic(ChatHaptic.Tap); actions.onOpenVoiceMode() },
                                onVoiceMode = { actions.haptic(ChatHaptic.Tap); actions.onOpenVoiceMode() },
                                onTextFieldFocus = { inputFocused = it },
                                onTextFieldBounds = { inputBounds = it },
                                value = state.draft,
                                onSend = { sendFromComposer() },
                                onStop = actions.onStop,
                                onModelClick = { sheet = ChatSheet.Model },
                                currentModel = state.modelLabel,
                                onCancelUpload = rememberUpdatedState(state.uploads).let { uploads -> remember(actions) { { uploads.value.filter { it.uploading }.forEach { actions.onCancelUpload(it.id) } } } },
                                onImagineOptions = { sheet = ChatSheet.Imagine },
                                showModelSelector = false,
                                placeholder = if (feature == null && taskHome) "Describe..." else "Ask Dua AI...",
                                sendEnabled = taskReady && state.canSend,
                                attachments = state.attachments,
                                uploads = state.uploads,
                                onRemoveAttachment = actions.onRemoveAttachment,
                                onRemoveAttachmentBackground = if (state.removeBackground) actions.onRemoveAttachmentBackground else null,
                                writingTools = { task -> actions.onWriting(state.draft, true, task) },
                                onRemoveUpload = actions.onCancelUpload,
                                aboveInput = if (feature != null) null else { {
                                    HomeModeSwitcher(if (taskHome) HomeMode.Task else HomeMode.Chat,
                                        onSelect = { mode ->
                                            if (!state.typing) { tap(); taskHome = mode == HomeMode.Task; actions.onAgent(taskHome) }
                                        }, enabled = !state.typing)
                                } },
                                attachmentPopup = {
                                    if (feature == ChatFeature.Projects) com.dua.ai.ui.projects.ProjectAttachmentMenu(sheet == ChatSheet.Attach, { sheet = null },
                                        { actions.onPickCamera() }, { actions.onPickPhotos() }, { actions.onPickFiles() }, { actions.onOpenDestination(DuaDestination.Plugins) })
                                },
                            )
                        }
                    }
                }
            }
            ChatDropOverlay(dropHover, dropMotion)
        }
    }
    when (sheet) {
        ChatSheet.Profile -> HomeProfileDialog(state,
            onDismiss = { sheet = null },
            onSettings = { sheet = null; actions.onOpenSettings() },
            onDestination = { destination -> sheet = null; actions.onOpenDestination(destination) })
        ChatSheet.Projects -> ProjectsSheet(state.projects, state.projectId, state.projectNotice, !state.typing,
            actions.onSelectProject, actions.onSaveProject, onDismiss = { sheet = null }, tasksEnabled = state.projectTasksEnabled,
            onSaveTask = actions.onSaveTask, onToggleTask = actions.onToggleTask, onDeleteTask = actions.onDeleteTask, dueDatesEnabled = state.taskDueDatesEnabled,
            memoryEnabled = state.projectMemoryEnabled, onSaveMemory = actions.onSaveProjectMemory)
        ChatSheet.Model -> ModelPickerSheet(
            current = state.model, providers = state.providers,
            freeModels = state.freeModels, currentFree = state.freeModel?.id, onSelectFree = actions.onSelectFreeModel,
            onSelect = actions.onSelectModel, onOpenSetup = { sheet = null; actions.onOpenSetup() },
            onDismiss = { sheet = null },
        )
        ChatSheet.Attach -> if (feature != ChatFeature.Projects) AttachmentSheet(
            webMode = state.webMode, pageReadingAvailable = state.pageReadingAvailable,
            showModeControls = feature == null,
            enabled = !state.typing && !state.readingFiles,
            onCamera = { sheet = null; actions.onPickCamera() },
            onPhotos = { sheet = null; actions.onPickPhotos() },
            onFiles = { sheet = null; actions.onPickFiles() },
            onReadLink = { on -> sheet = null; onWebMode(if (on) "page" else null) },
            onDismiss = { sheet = null },
            onAudio = { sheet = ChatSheet.Audio },
            storedFiles = state.storedFiles, storedFilesHint = state.storedFilesHint, storedFilesMaxBytes = state.storedFilesMaxBytes,
            inlineChatFiles = state.inlineChatFiles,
            modes = AttachModes(
                imagine = state.imagineConfig?.let { config -> if (config.enabled) "Create images from your message" else config.imageReason.ifBlank { "Turned off on the server" } },
                imagineOn = state.imagine,
                build = state.buildHint.ifEmpty { if (state.buildRepo != null) "On: ${state.buildRepo}" else "Chat about one of your GitHub repositories, read-only" },
                toggles = modeToggles(state, onWebMode, onReasoning, onXSearch, onRunCode, onAgent),
            ),
            onTurnOffModes = if (state.auto) null else ({ sheet = null; taskHome = false; actions.onAuto() }),
            onImagine = { on -> sheet = null; onImagine(on) },
            onBuild = { sheet = ChatSheet.Build; actions.onOpenBuild() },
        )
        ChatSheet.Audio -> AudioSheet(
            recordingSince = state.recordingSince, available = state.storedFiles || state.inlineChatFiles, hint = state.storedFilesHint,
            onRecord = actions.onStartRecording,
            onStop = { actions.onStopRecording(); sheet = null },
            onCancel = actions.onCancelRecording,
            onPick = { sheet = null; actions.onPickAudio() },
            onDismiss = { sheet = null },
        )
        ChatSheet.Imagine -> state.imagineConfig?.let { config ->
            ImagineSheet(options = state.imagineOptions, config = config, usage = state.imagineUsage, hasSource = state.imagineSource,
                onChange = actions.onImagineOptions, onDismiss = { sheet = null },
                polish = state.promptPolish?.let { polish -> polish.copy(canPolish = polish.canPolish && state.draft.isNotBlank()) },
                onPolish = actions.onPolishPrompt)
        }
        ChatSheet.Build -> BuildSheet(
            current = state.buildRepo, hint = state.buildHint, repositories = state.buildRepositories, enabled = !state.typing,
            onSelect = { repository -> sheet = null; taskHome = false; actions.onBuildRepository(repository) },
            onTurnOff = { sheet = null; actions.onBuildRepository(null) },
            onRetry = actions.onRetryBuildRepositories,
            onOpenSetup = { sheet = null; actions.onOpenSetup() },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    state.writing?.let { session -> WritingToolsSheet(session, actions.writing) }
    viewer?.let { (id, index) ->
        val media = state.messages.firstOrNull { it.id == id }?.media.orEmpty()
        if (media.isNotEmpty()) MediaViewer(media, index, mediaActions, state.videoAvailable, onDismiss = { viewer = null })
    }

    deletingId?.let { id ->
        DuaDialog(
            onDismissRequest = { deletingId = null },
            title = "Remove cached chat?",
            text = { Text("Remove this chat from the phone cache? Server history and stored files are kept.", style = SheetBodyStyle) },
            confirmButton = { SheetTextButton("Remove", { actions.onDeleteChat(id); deletingId = null }, destructive = true) },
            dismissButton = { SheetTextButton("Cancel", { deletingId = null }) },
        )
    }
    deletingServerId?.let { id ->
        val deletion = state.chatDeletion.takeIf { it.id == id }
        val busy = deletion?.busy == true
        DuaDialog(onDismissRequest = { if (!busy) { deletingServerId = null; actions.onDismissChatDeletion() } },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
            modifier = Modifier.padding(horizontal = Spacing.xl).widthIn(max = 400.dp),
            title = "Delete chat?",
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                val title = state.savedChats.firstOrNull { it.id == id }?.title ?: "this chat"
                Text("Delete “$title” from server history and this phone? Other devices with this update remove it when they sync. Edits to this conversation are removed; stored files are kept.", style = SheetBodyStyle)
                if (busy) Text("Deleting…", Modifier.padding(top = Spacing.md), style = SheetBodyStyle)
                deletion?.error?.let { Text(it, style = SheetBodyStyle, color = SheetTheme.colors.destructive, modifier = Modifier.padding(top = Spacing.md)) }
            } },
            confirmButton = { SheetTextButton(if (deletion?.error != null) "Retry" else "Delete", { actions.onDeleteServerChat(id) }, enabled = !busy, destructive = true) },
            dismissButton = { SheetTextButton("Cancel", { deletingServerId = null; actions.onDismissChatDeletion() }, enabled = !busy) })
    }
}

/** Mode switches for the + sheet in a conversation: Run code (when offered), Reasoning, Search and X (Grok only). */
private fun modeToggles(
    state: ChatUiState, onWebMode: (String?) -> Unit, onReasoning: (Boolean) -> Unit,
    onXSearch: (Boolean) -> Unit, onRunCode: (Boolean) -> Unit, onAgent: (Boolean) -> Unit = {},
): List<ModeToggle> = buildList {
    if (state.agentAvailable) add(ModeToggle("Agent", "Dua Agent: research latest AI news and suggest Dua AI ideas", Icons.Outlined.TravelExplore, state.agent) { onAgent(it) })
    if (state.codeConfig != null) add(ModeToggle("Run code", "Let the AI run Python code", Icons.Outlined.Terminal, state.runCode) { onRunCode(it) })
    if (state.reasoningSupported) add(ModeToggle("Think", "Think before answering", Icons.Outlined.Lightbulb, state.reasoning) { onReasoning(it) })
    add(ModeToggle("Search", "Search the web", Icons.Outlined.Public, state.webMode == "search") { onWebMode(if (it) "search" else null) })
    if (state.xSearchAvailable) add(ModeToggle("X", "Search posts on X with Grok", Icons.Outlined.Tag, state.xSearch) { onXSearch(it) })
}

/** Scrolls to the end of the growing reply; LazyColumn clamps at the content end. */
private const val STREAM_FOLLOW_PX = 100_000f
