package com.dua.ai.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dua.ai.ui.chat.ChatRoute
import com.dua.ai.ui.screens.AccountScreen
import com.dua.ai.ui.screens.DataControlsScreen
import com.dua.ai.ui.screens.DeviceSettingsScreen
import com.dua.ai.ui.screens.HandsFreeScreen
import com.dua.ai.ui.screens.IntegrationSetupScreen
import com.dua.ai.ui.settings.SettingsScreen
import com.dua.ai.ui.voice.ReplyVoice
import com.dua.ai.ui.voice.VoiceScreen
import com.dua.ai.viewmodel.ChatViewModel

/**
 * Navigation rules carried over from the hand-built navigator: nothing navigates while
 * local data is being imported, exported or cleared, navigation gives haptic feedback
 * when enabled, and any reply read-aloud stops when the destination changes.
 */
@Stable
class DuaNavigator(private val nav: NavHostController, private val viewModel: ChatViewModel, private val haptics: HapticFeedback) {
    private fun pulse() { if (viewModel.localSettings.value.haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    private fun blocked() = viewModel.dataBusy.value

    fun open(destination: DuaDestination) {
        if (blocked()) return
        pulse()
        nav.navigate(destination.route) { launchSingleTop = true }
    }
    fun openPage(name: String) { DuaDestination.fromPage(name)?.let(::open) }
    fun back() {
        if (blocked()) return
        if (!nav.popBackStack()) toChat()
    }
    fun toChat() {
        if (blocked()) return
        pulse()
        if (!nav.popBackStack(DuaDestination.Chat.route, inclusive = false)) nav.navigate(DuaDestination.Chat.route) { launchSingleTop = true }
    }
}

/** Swallows system Back while local data operations are running, as the old navigator did. */
@Composable
private fun BusyBackGuard(viewModel: ChatViewModel) {
    val busy by viewModel.dataBusy.collectAsState()
    BackHandler(enabled = busy) {}
}

@Composable
fun DuaNavHost(viewModel: ChatViewModel, voice: ReplyVoice, notificationRoute: Pair<String, String>?, nav: NavHostController = rememberNavController()) {
    val haptics = LocalHapticFeedback.current
    val navigator = remember(nav, viewModel, haptics) { DuaNavigator(nav, viewModel, haptics) }
    var attachRequest by remember { mutableIntStateOf(0) }
    DisposableEffect(nav, voice) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ -> voice.stopAll() }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    LaunchedEffect(notificationRoute) {
        notificationRoute?.second?.let { id ->
            if (viewModel.savedChats.value.any { it.id == id }) { viewModel.openChat(id); navigator.toChat() }
        }
    }
    NavHost(
        navController = nav,
        startDestination = DuaDestination.Chat.route,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(DuaDestination.Chat.route) {
            BusyBackGuard(viewModel)
            ChatRoute(viewModel, voice,
                onOpenSettings = { navigator.open(DuaDestination.Settings) },
                onOpenVoice = { navigator.open(DuaDestination.Voice) },
                onOpenSetup = { navigator.open(DuaDestination.FeatureSetup) },
                attachRequest = attachRequest,
                onAttachRequestHandled = { attachRequest = 0 })
        }
        composable(DuaDestination.Voice.route) {
            val draft by viewModel.draft.collectAsState()
            val settings by viewModel.localSettings.collectAsState()
            VoiceScreen(draft = draft, onBack = navigator::back, onHandsFree = { navigator.open(DuaDestination.HandsFree) },
                onUse = { text -> viewModel.updateDraft(text); navigator.toChat() }, inputLanguage = settings.inputLanguage)
        }
        composable(DuaDestination.HandsFree.route) {
            HandsFreeScreen(viewModel, onBack = navigator::back, onChat = navigator::toChat)
        }
        composable(DuaDestination.Settings.route) {
            BusyBackGuard(viewModel)
            SettingsScreen(viewModel, onBack = navigator::back, onOpen = navigator::open)
        }
        listOf(DuaDestination.Profile to "Profile", DuaDestination.CloudSync to "CloudSync", DuaDestination.Logout to "Logout").forEach { (destination, page) ->
            composable(destination.route) {
                BusyBackGuard(viewModel)
                AccountScreen(viewModel, page, onBack = navigator::back, onOpen = navigator::openPage)
            }
        }
        listOf(DuaDestination.Appearance to "Appearance", DuaDestination.Memory to "Memory", DuaDestination.Privacy to "Privacy",
            DuaDestination.Sharing to "Sharing", DuaDestination.Permissions to "Permissions", DuaDestination.Notifications to "Notifications").forEach { (destination, page) ->
            composable(destination.route) {
                BusyBackGuard(viewModel)
                DeviceSettingsScreen(viewModel, page, onBack = navigator::back, onPage = navigator::openPage,
                    onAttachments = { attachRequest++; navigator.toChat() })
            }
        }
        composable(DuaDestination.Imagine.route) {
            BusyBackGuard(viewModel)
            com.dua.ai.ui.screens.ImagineSettingsScreen(viewModel, onBack = navigator::back)
        }
        composable(DuaDestination.VoiceSettings.route) {
            BusyBackGuard(viewModel)
            com.dua.ai.ui.voice.VoiceSettingsScreen(viewModel, voice, onBack = navigator::back, onPicker = { kind ->
                navigator.open(when (kind) {
                    com.dua.ai.ui.voice.VoicePickerKind.REPLY_LANGUAGE -> DuaDestination.ReplyLanguage
                    com.dua.ai.ui.voice.VoicePickerKind.INPUT_LANGUAGE -> DuaDestination.InputLanguage
                    else -> DuaDestination.VoicePicker
                })
            })
        }
        listOf(DuaDestination.VoicePicker to com.dua.ai.ui.voice.VoicePickerKind.VOICE,
            DuaDestination.ReplyLanguage to com.dua.ai.ui.voice.VoicePickerKind.REPLY_LANGUAGE,
            DuaDestination.InputLanguage to com.dua.ai.ui.voice.VoicePickerKind.INPUT_LANGUAGE).forEach { (destination, kind) ->
            composable(destination.route) {
                BusyBackGuard(viewModel)
                com.dua.ai.ui.voice.VoicePickerScreen(viewModel, voice, kind, onBack = navigator::back)
            }
        }
        composable(DuaDestination.DataControls.route) {
            BusyBackGuard(viewModel)
            DataControlsScreen(viewModel, onBack = navigator::back)
        }
        composable(DuaDestination.FeatureSetup.route) {
            BusyBackGuard(viewModel)
            IntegrationSetupScreen(viewModel, onBack = navigator::back,
                onProfile = { navigator.open(DuaDestination.Profile) }, onCloud = { navigator.open(DuaDestination.CloudSync) })
        }
    }
}
