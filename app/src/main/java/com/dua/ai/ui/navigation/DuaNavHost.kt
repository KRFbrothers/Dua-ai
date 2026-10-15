package com.dua.ai.ui.navigation

import android.widget.Toast
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dua.ai.entry.EntryAction
import com.dua.ai.entry.ExternalEntry
import com.dua.ai.entry.EntryDispatch
import com.dua.ai.entry.LocalEntryDispatch
import com.dua.ai.entry.RememberThisScreen
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dua.ai.ui.chat.ChatRoute
import com.dua.ai.ui.chat.ChatFeature
import com.dua.ai.ui.chat.HomeTab
import com.dua.ai.ui.projects.ProjectsFeatureFlow
import com.dua.ai.ui.projects.ProjectChatRoute
import com.dua.ai.ui.onboarding.OnboardingScreen
import com.dua.ai.ui.screens.CloudSyncScreen
import com.dua.ai.ui.screens.DataControlsScreen
import com.dua.ai.ui.screens.IntegrationSetupScreen
import com.dua.ai.ui.screens.ProfileScreen
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

    fun open(destination: DuaDestination, haptic: Boolean = true) {
        if (blocked()) return
        if (haptic) pulse()
        nav.navigate(destination.route) { launchSingleTop = true }
    }
    fun back() {
        if (blocked()) return
        if (!nav.popBackStack()) toChat()
    }
    fun toChat() {
        if (blocked()) return
        pulse()
        if (!nav.popBackStack(DuaDestination.Chat.route, inclusive = false)) nav.navigate(DuaDestination.Chat.route) {
            // Chat is not on the stack only when the app started on the welcome screen; replace it so Back does not return there.
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}

/** Swallows system Back while local data operations are running, as the old navigator did. */
@Composable
private fun BusyBackGuard(viewModel: ChatViewModel) {
    val busy by viewModel.dataBusy.collectAsState()
    BackHandler(enabled = busy) {}
}

@Composable
fun DuaNavHost(viewModel: ChatViewModel, voice: ReplyVoice, notificationRoute: Pair<String, String>?, nav: NavHostController = rememberNavController(),
    externalEntry: ExternalEntry? = null, onEntryConsumed: (String) -> Unit = {}) {
    val haptics = LocalHapticFeedback.current
    val navigator = remember(nav, viewModel, haptics) { DuaNavigator(nav, viewModel, haptics) }
    val exitFeature: () -> Unit = {
        if (!viewModel.dataBusy.value) {
            voice.stopAll()
            viewModel.startNewChat()
            viewModel.selectAuto()
            viewModel.selectProject(com.dua.ai.projects.ProjectCodec.PERSONAL)
            navigator.toChat()
        }
    }
    val openDestination: (DuaDestination) -> Unit = { if (it == DuaDestination.Chat) navigator.toChat() else navigator.open(it) }
    // Decided once per launch: signed-out users who have not skipped it start on the welcome screen.
    val startDestination = remember { if (viewModel.shouldShowOnboarding()) DuaDestination.Onboarding else DuaDestination.Chat }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val unstarted = remember { kotlinx.coroutines.flow.MutableStateFlow(androidx.lifecycle.Lifecycle.State.INITIALIZED) }
    val entryLifecycle by (backStack?.lifecycle?.currentStateFlow ?: unstarted).collectAsState()
    val entryReady = entryLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val dataBusy by viewModel.dataBusy.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val reading by viewModel.readingFiles.collectAsState()
    val preparing by viewModel.preparingFiles.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val uploads by viewModel.uploads.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val live by com.dua.ai.voice.live.LiveVoiceController.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val waitingForAttachments = externalEntry?.let { it.action == EntryAction.Share && it.uris.isNotEmpty() } == true &&
        (attachments.isNotEmpty() || uploads.isNotEmpty())
    val entryBlocked = !entryReady || dataBusy || typing || preparing || reading || account.busy || live.active || waitingForAttachments
    var memoryReviewId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(externalEntry?.id, route, entryBlocked) {
        val entry = externalEntry ?: return@LaunchedEffect
        // External entries wait for login/Skip; no authentication or busy-operation bypass.
        if (route == null || route in setOf(DuaDestination.Onboarding.route, DuaDestination.EmailAccount.route, DuaDestination.Account.route) || entryBlocked) return@LaunchedEffect
        when (entry.action) {
            EntryAction.Mic, EntryAction.Live, EntryAction.Screenshare -> {
                onEntryConsumed(entry.id)
                navigator.toChat()
                navigator.open(when (entry.action) { EntryAction.Mic -> DuaDestination.Voice; EntryAction.Live -> DuaDestination.LiveVoice; else -> DuaDestination.Screenshare })
            }
            EntryAction.Remember -> if (route != DuaDestination.Chat.route) navigator.toChat() else memoryReviewId = entry.id
            else -> if (route != DuaDestination.Chat.route) navigator.toChat()
        }
    }
    DisposableEffect(nav, voice) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ -> voice.stopAll() }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    LaunchedEffect(notificationRoute) {
        notificationRoute?.second?.let { id ->
            if (notificationRoute.first.startsWith("project:")) {
                if (viewModel.localSettings.value.projects.any { it.id == id && !it.archived && it.details?.deleted != true }) {
                    viewModel.selectProject(id)
                    if (viewModel.projectId.value == id) {
                        navigator.open(DuaDestination.Projects)
                        nav.currentBackStackEntry?.savedStateHandle?.set("open_project", id)
                    }
                }
            } else if (viewModel.savedChats.value.any { it.id == id }) { viewModel.openChat(id); navigator.toChat() }
        }
    }
    // Keep the graph builder stable as entry/route state changes; rebuilding the graph can reset an open voice destination.
    val entryDispatch = rememberUpdatedState(externalEntry?.takeIf {
        it.action !in setOf(EntryAction.Remember, EntryAction.Mic, EntryAction.Live, EntryAction.Screenshare) && route == DuaDestination.Chat.route
    }?.let { EntryDispatch(it, onEntryConsumed, ready = !entryBlocked,
        notice = if (waitingForAttachments) "Shared files are waiting. Send or remove current attachments first." else "") })
    val rememberedRequest = rememberUpdatedState(externalEntry?.takeIf { it.action == EntryAction.Remember && it.id == memoryReviewId })
    val consumeEntry = rememberUpdatedState(onEntryConsumed)
    val processAllowed = rememberUpdatedState(!entryBlocked)
    NavHost(
        navController = nav,
        startDestination = startDestination.route,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(DuaDestination.Onboarding.route) {
            OnboardingScreen(viewModel, onDone = navigator::toChat, onEmail = { navigator.open(DuaDestination.EmailAccount) })
        }
        composable(DuaDestination.Chat.route) {
            val remembered = rememberedRequest.value
            if (remembered != null) {
                val owner by viewModel.account.state.collectAsState()
                val dismiss = { consumeEntry.value(remembered.id) }
                BackHandler { dismiss() }
                RememberThisScreen(remembered, owner.user?.email, processAllowed.value, onSave = { text ->
                    viewModel.confirmSharedMemory(text, owner.user?.id.orEmpty()).also { error ->
                        if (error == null) { dismiss(); Toast.makeText(context, "Memory cached. Sync to save it to your account.", Toast.LENGTH_LONG).show() }
                    }
                }, onSignIn = { navigator.open(DuaDestination.Account) }, onDismiss = dismiss)
            } else CompositionLocalProvider(LocalEntryDispatch provides entryDispatch.value) {
                BusyBackGuard(viewModel)
                val activeProject by viewModel.projectId.collectAsState()
                if (activeProject != com.dua.ai.projects.ProjectCodec.PERSONAL) ProjectChatRoute(viewModel, voice,
                    onBack = exitFeature,
                    onDetail = { page ->
                        navigator.open(DuaDestination.Projects)
                        nav.currentBackStackEntry?.savedStateHandle?.set("open_project", activeProject)
                        nav.currentBackStackEntry?.savedStateHandle?.set("project_page", page)
                    }, onSettings = { navigator.open(DuaDestination.Profile) }, onDestination = openDestination,
                    onMic = { navigator.open(DuaDestination.Voice) }, onSetup = { navigator.open(DuaDestination.FeatureSetup) },
                    onVoice = { navigator.open(DuaDestination.HandsFree) })
                else ChatRoute(viewModel, voice,
                    onOpenSettings = { navigator.open(DuaDestination.Profile) },
                    onOpenDestination = openDestination,
                    onOpenVoice = { navigator.open(DuaDestination.Voice) },
                    onOpenSetup = { navigator.open(DuaDestination.FeatureSetup) },
                    onOpenComputer = { navigator.open(DuaDestination.Computer) },
                    // The mic long-press already gave its own haptic.
                    onOpenVoiceMode = { navigator.open(if (viewModel.liveVoiceAvailable()) DuaDestination.LiveVoice else DuaDestination.HandsFree, haptic = false) })
            }
        }
        functionalFeaturePages(images = {
            val back = { if (!viewModel.dataBusy.value) { viewModel.setImagine(false); navigator.toChat() }; Unit }
            BackHandler { back() }
            BusyBackGuard(viewModel)
            com.dua.ai.ui.screens.ImagesScreen(viewModel, back, onSetup = { navigator.open(DuaDestination.FeatureSetup) })
        }, library = {
            BackHandler { navigator.toChat() }
            BusyBackGuard(viewModel)
            com.dua.ai.ui.screens.LibraryScreen(viewModel, navigator::toChat,
                onSetup = { navigator.open(DuaDestination.FeatureSetup) }, onChat = navigator::toChat,
                onProject = { id -> navigator.open(DuaDestination.Projects); nav.currentBackStackEntry?.savedStateHandle?.set("open_project", id) })
        }, plugins = {
            BackHandler { navigator.toChat() }
            BusyBackGuard(viewModel)
            com.dua.ai.ui.screens.PluginsScreen(viewModel, navigator::toChat,
                onSetup = { navigator.open(DuaDestination.FeatureSetup) }, onCloud = { navigator.open(DuaDestination.CloudSync) }, onAgent = { navigator.open(DuaDestination.Agent) })
        })
        composable(DuaDestination.Computer.route) {
            BackHandler { exitFeature() }
            BusyBackGuard(viewModel)
            com.dua.ai.ui.screens.ComputerScreen(onBack = exitFeature)
        }
        composable(DuaDestination.Agent.route) {
            BackHandler { exitFeature() }
            BusyBackGuard(viewModel)
            com.dua.ai.ui.agent.AgentScreen(viewModel, onBack = exitFeature, voice = voice,
                onOpenSettings = { navigator.open(DuaDestination.Profile) }, onOpenDestination = openDestination,
                onOpenVoice = { navigator.open(DuaDestination.Voice) }, onOpenSetup = { navigator.open(DuaDestination.FeatureSetup) },
                onOpenVoiceMode = { navigator.open(if (viewModel.liveVoiceAvailable()) DuaDestination.LiveVoice else DuaDestination.HandsFree, haptic = false) })
        }
        composable(DuaDestination.Projects.route) {
            BackHandler { exitFeature() }
            BusyBackGuard(viewModel)
            val requestedProject by it.savedStateHandle.getStateFlow<String?>("open_project", null).collectAsState()
            val requestedPage by it.savedStateHandle.getStateFlow<String?>("project_page", null).collectAsState()
            ProjectsFeatureFlow(viewModel, onBack = exitFeature, onOpenChat = { navigator.open(DuaDestination.ProjectChat) },
                onPlugins = { navigator.open(DuaDestination.Plugins) }, onMic = { navigator.open(DuaDestination.Voice) },
                onVoice = { navigator.open(DuaDestination.HandsFree) },
                requestedProject = requestedProject, requestedPage = requestedPage,
                onRequestConsumed = { it.savedStateHandle["open_project"] = null; it.savedStateHandle["project_page"] = null })
        }
        composable(DuaDestination.ProjectChat.route) {
            val displayedProject by viewModel.projectId.collectAsState()
            fun detail(page: String? = null) {
                if (!viewModel.dataBusy.value) {
                    navigator.back()
                    nav.currentBackStackEntry?.savedStateHandle?.set("open_project", displayedProject)
                    nav.currentBackStackEntry?.savedStateHandle?.set("project_page", page)
                }
            }
            BusyBackGuard(viewModel)
            ProjectChatRoute(viewModel, voice, onBack = { detail() }, onDetail = { page -> detail(page) },
                onSettings = { navigator.open(DuaDestination.Profile) }, onDestination = openDestination,
                onMic = { navigator.open(DuaDestination.Voice) }, onSetup = { navigator.open(DuaDestination.FeatureSetup) },
                onVoice = { navigator.open(DuaDestination.HandsFree) })
        }
        listOf(DuaDestination.Work).forEach { destination ->
            composable(destination.route) {
                BackHandler { exitFeature() }
                BusyBackGuard(viewModel)
                ChatRoute(viewModel, voice,
                    onOpenSettings = { navigator.open(DuaDestination.Profile) }, onOpenDestination = openDestination,
                    onOpenVoice = { navigator.open(DuaDestination.Voice) }, onOpenSetup = { navigator.open(DuaDestination.FeatureSetup) },
                    onOpenVoiceMode = { navigator.open(if (viewModel.liveVoiceAvailable()) DuaDestination.LiveVoice else DuaDestination.HandsFree, haptic = false) },
                    feature = if (destination == DuaDestination.ProjectChat) ChatFeature.Projects else null,
                    homeTab = if (destination == DuaDestination.Work) HomeTab.Work else HomeTab.Chat,
                    onExitFeature = exitFeature)
            }
        }
        composable(DuaDestination.Voice.route) {
            val draft by viewModel.draft.collectAsState()
            val settings by viewModel.localSettings.collectAsState()
            val integrations by viewModel.integrations.collectAsState()
            val account by viewModel.account.state.collectAsState()
            // Same condition as viewModel.liveVoiceAvailable(), so the label matches where the button goes.
            val live = integrations.voice?.live?.enabled == true && account.user != null && viewModel.projectId.value == com.dua.ai.projects.ProjectCodec.PERSONAL
            VoiceScreen(draft = draft, onBack = navigator::back, onHandsFree = { navigator.open(if (viewModel.projectId.value == com.dua.ai.projects.ProjectCodec.PERSONAL && viewModel.liveVoiceAvailable()) DuaDestination.LiveVoice else DuaDestination.HandsFree) },
                onUse = { text -> viewModel.updateDraft(text)
                    if (nav.previousBackStackEntry?.destination?.route in setOf(DuaDestination.Projects.route, DuaDestination.ProjectChat.route)) navigator.back() else navigator.toChat()
                }, inputLanguage = settings.inputLanguage, liveAvailable = live)
        }
        composable(DuaDestination.LiveVoice.route) {
            com.dua.ai.ui.voice.LiveVoiceScreen(viewModel, onClose = navigator::back,
                onHandsFree = { nav.navigate(DuaDestination.HandsFree.route) { popUpTo(DuaDestination.LiveVoice.route) { inclusive = true } } })
        }
        composable(DuaDestination.HandsFree.route) {
            // Voice mode (device speech loop). The route keeps its old name so saved navigation state still resolves.
            com.dua.ai.ui.voice.VoiceModeScreen(viewModel, voice, onClose = navigator::back, onOpenChat = {
                if (runCatching { nav.getBackStackEntry(DuaDestination.ProjectChat.route) }.isSuccess) nav.popBackStack(DuaDestination.ProjectChat.route, false)
                else if (runCatching { nav.getBackStackEntry(DuaDestination.Projects.route) }.isSuccess) {
                    nav.popBackStack(DuaDestination.Projects.route, false); navigator.open(DuaDestination.ProjectChat)
                } else navigator.toChat()
            })
        }
        composable(DuaDestination.Settings.route) {
            BusyBackGuard(viewModel)
            SettingsScreen(viewModel, onBack = navigator::back, onOpen = navigator::open)
        }
        composable(DuaDestination.Profile.route) {
            BusyBackGuard(viewModel)
            SettingsScreen(viewModel, onBack = navigator::back, onOpen = navigator::open)
        }
        composable(DuaDestination.EmailAccount.route) {
            com.dua.ai.ui.auth.EmailAuthScreen(viewModel, onBack = navigator::back)
        }
        composable(DuaDestination.Account.route) {
            BusyBackGuard(viewModel)
            ProfileScreen(viewModel, onBack = navigator::back, onEmail = { navigator.open(DuaDestination.EmailAccount) },
                onSync = { navigator.open(DuaDestination.CloudSync) })
        }
        listOf(DuaDestination.Appearance, DuaDestination.Accent).forEach { destination ->
            composable(destination.route) {
                BusyBackGuard(viewModel)
                com.dua.ai.ui.settings.AppearanceScreen(viewModel, onBack = navigator::back, accentPage = destination == DuaDestination.Accent)
            }
        }
        comingSoonPages(onBack = navigator::back, onFeatureBack = exitFeature) { BusyBackGuard(viewModel) }
        composable(DuaDestination.CloudSync.route) {
            BusyBackGuard(viewModel)
            CloudSyncScreen(viewModel, onBack = navigator::back, onProfile = { navigator.open(DuaDestination.Profile) })
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
                    com.dua.ai.ui.voice.VoicePickerKind.LIVE_VOICE -> DuaDestination.LiveVoicePicker
                    com.dua.ai.ui.voice.VoicePickerKind.LIVE -> DuaDestination.LiveVoice
                    else -> DuaDestination.VoicePicker
                })
            }, onOpen = { destination -> navigator.open(destination) })
        }
        listOf(DuaDestination.VoicePicker to com.dua.ai.ui.voice.VoicePickerKind.VOICE,
            DuaDestination.ReplyLanguage to com.dua.ai.ui.voice.VoicePickerKind.REPLY_LANGUAGE,
            DuaDestination.InputLanguage to com.dua.ai.ui.voice.VoicePickerKind.INPUT_LANGUAGE,
            DuaDestination.LiveVoicePicker to com.dua.ai.ui.voice.VoicePickerKind.LIVE_VOICE).forEach { (destination, kind) ->
            composable(destination.route) {
                BusyBackGuard(viewModel)
                com.dua.ai.ui.voice.VoicePickerScreen(viewModel, voice, kind, onBack = navigator::back)
            }
        }
        composable(DuaDestination.DataControls.route) {
            BusyBackGuard(viewModel)
            DataControlsScreen(viewModel, onBack = navigator::back)
        }
        composable(DuaDestination.HistoryData.route) {
            BusyBackGuard(viewModel)
            com.dua.ai.ui.settings.HistoryDataScreen(onBack = navigator::back, onOpen = { navigator.open(it) })
        }
        composable(DuaDestination.FeatureSetup.route) {
            BusyBackGuard(viewModel)
            IntegrationSetupScreen(viewModel, onBack = navigator::back,
                onProfile = { navigator.open(DuaDestination.Profile) }, onCloud = { navigator.open(DuaDestination.CloudSync) })
        }
    }
}
