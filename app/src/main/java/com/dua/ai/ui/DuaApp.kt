package com.dua.ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dua.ai.ui.navigation.DuaNavHost
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.voice.rememberReplyVoice
import com.dua.ai.viewmodel.ChatViewModel

/** App root: theme from the saved appearance settings, shared reply voice, and navigation. */
@Composable
fun DuaApp(viewModel: ChatViewModel = viewModel(), notificationRoute: Pair<String, String>? = null) {
    val preferences by viewModel.localSettings.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.loadProviderConfiguration(); viewModel.loadIntegrations()
        // Refresh a saved session so the greeting picks up the current Google account name.
        if (viewModel.account.state.value.user != null) viewModel.loadAccount()
    }
    DuaTheme(preferences) {
        val voice = rememberReplyVoice(viewModel)
        DuaNavHost(viewModel, voice, notificationRoute)
    }
}
