package com.dua.ai.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.components.FeatureTopBar
import com.dua.ai.ui.chat.ChatFeature
import com.dua.ai.ui.chat.ChatRoute
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.voice.ReplyVoice
import com.dua.ai.ui.voice.rememberReplyVoice
import com.dua.ai.ui.theme.*
import com.dua.ai.viewmodel.ChatViewModel
import java.time.LocalDate

enum class AgentTask { Research, GitHub, Briefing }

internal const val GITHUB_AGENT_PROMPT = "Help me research Dua AI (KRFbrothers/Dua-ai) and prepare a prioritized improvement roadmap and PR review plan. Use repository or diff content I provide, and ask for private files you cannot access."
internal fun briefingPrompt(date: LocalDate = LocalDate.now()) =
    "Create my morning AI and technology briefing for $date. Research current news, cite source links, and summarize the key updates and why they matter."

/** Every task uses the existing capability-gated Agent mode and ordinary chat/error handling. */
@Composable
fun AgentScreen(viewModel: ChatViewModel, onBack: () -> Unit, voice: ReplyVoice? = null,
    onOpenSettings: () -> Unit = {}, onOpenDestination: (DuaDestination) -> Unit = {},
    onOpenVoice: () -> Unit = {}, onOpenSetup: () -> Unit = {}, onOpenVoiceMode: () -> Unit = {}) {
    val available by viewModel.agentAvailable.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val dataBusy by viewModel.dataBusy.collectAsState()
    val agent by viewModel.agent.collectAsState()
    var showFiles by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshEngineCapabilities() }
    if (showFiles && !agent) { AgentFilesScreen(viewModel.account, onBack = { showFiles = false }); return }
    if (agent) {
        ChatRoute(viewModel, voice ?: rememberReplyVoice(viewModel), onOpenSettings, onOpenVoice, onOpenSetup,
            onOpenDestination = onOpenDestination, onOpenVoiceMode = onOpenVoiceMode,
            feature = ChatFeature.Agent, onExitFeature = onBack)
        return
    }
    fun openTask(prompt: String = "", send: Boolean = false) {
        if (!available || typing || dataBusy) return
        viewModel.startNewChat()
        viewModel.setAgent(true)
        if (!viewModel.agent.value) return
        viewModel.updateDraft(prompt)
        if (send) viewModel.sendMessage(prompt)
    }
    AgentContent(available = available, busy = typing || dataBusy, onBack = onBack,
        onNewTask = { openTask() }, onRefresh = viewModel::refreshEngineCapabilities,
        onTask = { task -> when (task) {
            AgentTask.Research -> openTask()
            AgentTask.GitHub -> openTask(GITHUB_AGENT_PROMPT)
            AgentTask.Briefing -> openTask(briefingPrompt(), send = true)
        } }, onSend = { openTask(it, send = true) }, onOpenFiles = { showFiles = true })
}

@Composable
internal fun AgentContent(available: Boolean, busy: Boolean = false, onBack: () -> Unit,
    onNewTask: () -> Unit, onRefresh: () -> Unit = {}, onTask: (AgentTask) -> Unit, onSend: (String) -> Unit,
    showChrome: Boolean = true, onOpenFiles: (() -> Unit)? = null) {
    var draft by rememberSaveable { mutableStateOf("") }
    val c = PageTheme.colors
    val enabled = available && !busy
    val taskSurface = if (DuaTheme.colors.isDark) AgentTaskSurfaceDark else AgentTaskSurfaceLight
    val send = { if (enabled && draft.isNotBlank()) onSend(draft.trim()) }
    Column(Modifier.fillMaxSize().background(c.background)
        .then(if (showChrome) Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding() else Modifier)) {
        if (showChrome) FeatureTopBar("AI Agent", onBack, onNewTask, newSessionEnabled = enabled)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport)
                .padding(AgentSizes.padding), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Column(Modifier.widthIn(max = AgentSizes.maxWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(ReferenceIcons.AgentMode, "Dua Agent", Modifier.size(AgentSizes.heroIcon), tint = c.text)
                    Spacer(Modifier.height(AgentSizes.heroGap))
                    Text("What should Dua Agent do for you?", style = MaterialTheme.typography.headlineSmall.merge(AgentHeroStyle),
                        textAlign = TextAlign.Center, color = c.text, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(AgentSizes.heroBottom))
                    AgentTaskCard("Deep Web Research", "Live", taskSurface, enabled, purpleBadge = true) { onTask(AgentTask.Research) }
                    Spacer(Modifier.height(AgentSizes.cardGap))
                    AgentTaskCard("GitHub Repo & PR Assistant", "Ready", taskSurface, enabled) { onTask(AgentTask.GitHub) }
                    Spacer(Modifier.height(AgentSizes.cardGap))
                    AgentTaskCard("Daily Morning Briefing", "9:00 AM", taskSurface, enabled, hint = "Runs when tapped") { onTask(AgentTask.Briefing) }
                    onOpenFiles?.let { open ->
                        Spacer(Modifier.height(AgentSizes.cardGap))
                        AgentTaskCard("Files", "Text", taskSurface, enabled = !busy, hint = "Documents Agent can search") { open() }
                    }
                    if (!available) {
                        Spacer(Modifier.height(Spacing.md))
                        Text("Agent mode needs the Google Cloud engine. Refresh to check availability.", style = MaterialTheme.typography.bodyMedium,
                            color = c.subText, textAlign = TextAlign.Center)
                        TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Refresh") }
                    } else if (busy) {
                        Text("Finish the current action before starting an Agent task.", style = MaterialTheme.typography.bodyMedium,
                            color = c.subText, modifier = Modifier.padding(top = Spacing.md))
                    }
                }
            }
        }
        if (showChrome) Column(Modifier.widthIn(max = AgentSizes.maxWidth).fillMaxWidth().align(Alignment.CenterHorizontally)
            .padding(horizontal = AgentSizes.padding, vertical = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            AgentModePill()
            Spacer(Modifier.height(Spacing.sm))
            Surface(shape = AgentShapes.pill, color = c.card, border = BorderStroke(AgentSizes.border, c.cardBorder)) {
                Row(Modifier.fillMaxWidth().padding(start = AgentSizes.composerPaddingH, end = Spacing.xs,
                    top = AgentSizes.composerPaddingV, bottom = AgentSizes.composerPaddingV), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTarget).testTag("agent-input"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text), cursorBrush = SolidColor(c.accent),
                        maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }), decorationBox = { input ->
                            Box(contentAlignment = Alignment.CenterStart) { if (draft.isEmpty()) Text("Ask Dua AI", style = MaterialTheme.typography.bodyLarge, color = c.subText); input() }
                        })
                    IconButton(onClick = send, enabled = enabled && draft.isNotBlank(), modifier = Modifier.size(Sizes.touchTarget)) {
                        Surface(Modifier.size(AgentSizes.circle), shape = AgentShapes.circle,
                            color = if (enabled && draft.isNotBlank()) c.accent else c.accentContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ArrowUpward, "Send Agent prompt", Modifier.size(AgentSizes.icon),
                                    tint = if (enabled && draft.isNotBlank()) c.onAccent else c.onAccentContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AgentModePill(modifier: Modifier = Modifier) {
    val c = PageTheme.colors
    Surface(modifier.wrapContentWidth().testTag("agent-mode-pill"), shape = AgentShapes.pill,
        color = c.accentContainer, contentColor = c.onAccentContainer) {
        Text("✨ Agent", Modifier.heightIn(min = Sizes.touchTarget).wrapContentHeight().padding(horizontal = AgentSizes.composerPaddingH),
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AgentTaskCard(title: String, badge: String, surface: androidx.compose.ui.graphics.Color, enabled: Boolean,
    purpleBadge: Boolean = false, hint: String? = null, onClick: () -> Unit) {
    val c = PageTheme.colors
    Surface(onClick = onClick, enabled = enabled, shape = AgentShapes.card, color = surface,
        border = BorderStroke(AgentSizes.border, c.cardBorder), modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)) {
        Row(Modifier.padding(AgentSizes.cardPadding), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.subText) }
            }
            Surface(shape = AgentShapes.pill, color = if (purpleBadge) c.accentContainer else c.card,
                contentColor = if (purpleBadge) c.onAccentContainer else c.text) {
                Text(badge, Modifier.padding(horizontal = AgentSizes.badgePaddingH, vertical = AgentSizes.badgePaddingV),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
