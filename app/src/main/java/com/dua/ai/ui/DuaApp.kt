package com.dua.ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dua.ai.ui.navigation.DuaNavHost
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.voice.rememberReplyVoice
import com.dua.ai.viewmodel.ChatViewModel

/** App root: theme from the saved appearance settings, shared reply voice, and navigation. */
@Composable
fun DuaApp(viewModel: ChatViewModel = viewModel(), notificationRoute: Pair<String, String>? = null,
    externalEntry: com.dua.ai.entry.ExternalEntry? = null, onEntryConsumed: (String) -> Unit = {}) {
    val preferences by viewModel.localSettings.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.loadProviderConfiguration(); viewModel.loadIntegrations()
        // Refresh a saved session so the greeting picks up the current Google account name.
        if (viewModel.account.state.value.user != null) viewModel.loadAccount()
    }
    // Write a debounced draft and queued history when the app leaves the foreground, before Android may kill the process.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushHistory()
            if (event == Lifecycle.Event.ON_RESUME) { com.dua.ai.tasks.TaskReminders.refresh(context); viewModel.refreshRemoteSwitches() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Recent chats in Android's share sheet (Direct Share) and launcher long-press actions; deleted chats drop out.
    val chats by viewModel.savedChats.collectAsState()
    val shortcutTargets = remember(chats) { com.dua.ai.entry.ChatShortcuts.recent(chats) }
    LaunchedEffect(shortcutTargets) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.dua.ai.entry.ChatShortcuts.publish(context, shortcutTargets) }
        }
    }
    DuaTheme(preferences.copy(theme = appearance.theme), accent = appearance.accent) {
        com.dua.ai.ui.components.TelegramThemeSwitchHost(com.dua.ai.ui.theme.DuaTheme.colors.isDark, com.dua.ai.ui.theme.rememberMotionAllowed()) {
        val switches by viewModel.switches.collectAsState()
        val checking by viewModel.switchesChecking.collectAsState()
        // DUA_MAINTENANCE=1 on dua-api: everything pauses behind one friendly screen (data stays on the phone).
        if (switches.maintenance) AppPausedScreen(switches.message, checking, onRetry = { viewModel.refreshRemoteSwitches(force = true) })
        else {
            val voice = rememberReplyVoice(viewModel)
            DuaNavHost(viewModel, voice, notificationRoute, externalEntry = externalEntry, onEntryConsumed = onEntryConsumed)
        }
        }
    }
}
