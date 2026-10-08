package com.dua.ai.ui.chat

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
    attachRequest: Int = 0,
    onAttachRequestHandled: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialDrawerValue)
    val scope = rememberCoroutineScope()
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(attachRequest) { if (attachRequest > 0) { sheet = ChatSheet.Attach; onAttachRequestHandled() } }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    LaunchedEffect(state.messages.size, state.typing) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
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
                if (state.messages.isEmpty() && !state.typing) {
                    EmptyState(greeting = state.greeting, suggestions = state.suggestions, onSuggestion = { prompt ->
                        actions.onDraftChange(prompt)
                        runCatching { inputFocus.requestFocus() }
                    })
                } else {
                    MessageList(state = state, actions = actions, listState = listState)
                }
            }
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth()) {
                    ChatNotices(state = state, actions = actions)
                    Composer(
                        state = state,
                        focusRequester = inputFocus,
                        onDraftChange = actions.onDraftChange,
                        onWebMode = actions.onWebMode,
                        onAttach = { sheet = ChatSheet.Attach },
                        onClearAttachments = actions.onClearAttachments,
                        onMic = actions.onOpenVoice,
                        onSend = actions.onSend,
                        onStop = actions.onStop,
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
        )
        null -> Unit
    }

    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Delete chat?") },
            text = { Text("Delete this chat from this device?") },
            confirmButton = { TextButton(onClick = { actions.onDeleteChat(id); deletingId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancel") } },
        )
    }
}
