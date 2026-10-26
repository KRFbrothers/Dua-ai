package com.dua.ai.ui.chat

import android.app.Activity
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.ChatWelcomeStyle
import com.dua.ai.ui.theme.ChatMessageStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dua.ai.model.ChatMessage
import com.dua.ai.ui.theme.DuaTheme
import androidx.compose.material3.MaterialTheme

/** Fareed's supplied UI, bound to the existing ChatRoute state/actions and ChatScreen host.
 * Colors, bubble geometry, composer and action transition follow the supplied design.
 * The ViewModel owns drafts, messages and streaming; no demo replies or local history.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun SuppliedChatScreen(
    state: ChatUiState,
    actions: ChatActions,
    listState: LazyListState,
    focusRequester: FocusRequester,
    dictation: ChatDictation?,
    onMenu: () -> Unit,
    onModel: () -> Unit,
    onAttach: () -> Unit,
    imagineActions: ImagineActions,
    showLatest: Boolean,
    newReplies: Int,
    onLatest: () -> Unit,
    homeTab: HomeTab = HomeTab.Chat,
    task: Boolean = false,
    onTaskMode: (HomeMode) -> Unit = {},
    onTaskStarter: (String) -> Unit = {},
) {
        Scaffold(
            modifier = Modifier.testTag("supplied-chat"),
            containerColor = DuaTheme.semantic.bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Dua AI ✨",
                            color = DuaTheme.semantic.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.heightIn(min = 48.dp)
                                .combinedClickable(role = Role.Button, onClick = onMenu,
                                    onLongClickLabel = "Choose model", onLongClick = onModel)
                                .semantics { contentDescription = "Dua AI, chats and settings" }.wrapContentHeight(),
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DuaTheme.semantic.bg),
                )
            },
            bottomBar = {
                if (homeTab != HomeTab.Work) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ChatNotices(state, actions)
                    if (state.editingId != null) EditingBanner(actions.onCancelEdit)
                    if (state.attachments.isNotEmpty() || state.uploads.isNotEmpty()) {
                        AttachmentBar(state.attachments, state.uploads, !state.typing,
                            actions.onRemoveAttachment, actions.onCancelUpload,
                            actions.onRemoveAttachmentBackground.takeIf { state.removeBackground })
                    }
                    if (dictation?.listening == true) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            DictationBar(dictation.level, dictation.cancel, dictation.confirm)
                        }
                    }
                    if (state.messages.isEmpty() && !state.typing) {
                        HomeModeSwitcher(if (task) HomeMode.Task else HomeMode.Chat,
                            onSelect = onTaskMode, enabled = !state.typing)
                    }
                    GeminiBottomBar(state, actions, onAttach, { dictation?.start?.invoke() ?: actions.onOpenVoice() }, focusRequester,
                        placeholder = if (task) "Describe..." else "Ask Dua AI...",
                        sendEnabled = state.canSend && (!task || (state.agent && state.agentAvailable)))
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
              if (homeTab == HomeTab.Work) {
                  Text("Coming soon", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge)
              } else {
              CompositionLocalProvider(LocalCodeExecutionUi provides CodeExecutionUi(state.codeSnippetUnavailableReason, actions.onRunCodeSnippet)) {
                MessageList(state, actions, listState, imagineActions = imagineActions, suppliedStyle = true,
                    emptyContent = {
                        if (task) {
                            if (state.draft.isBlank() && state.attachments.isEmpty() && state.uploads.isEmpty()) {
                                SuppliedTaskStarters(onTaskStarter)
                            }
                        } else {
                        val name = state.profileName.trim().substringBefore(" ").takeIf { it.isNotBlank() }
                        SuppliedAssistantTurn(Modifier.padding(vertical = 10.dp)) {
                            Text(
                                text = (if (name != null) "Hello $name! " else "Hello! ") +
                                    "Main Dua AI hoon. Aaj hum kis project par kaam kar rahe hain?",
                                color = DuaTheme.semantic.text, style = ChatWelcomeStyle,
                            )
                        }
                        }
                    })
              }
                JumpToLatest(showLatest, newReplies = newReplies, onClick = onLatest,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
              }
            }
        }
}

@Composable
private fun SuppliedTaskStarters(onTaskStarter: (String) -> Unit) {
    val haptic = LocalChatHaptic.current
    Column(Modifier.fillMaxWidth().testTag("supplied-task-starters"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HomeTaskPromptIds.forEach { id ->
            val prompt = stringResource(id)
            OutlinedButton(onClick = { haptic(ChatHaptic.Tap); onTaskStarter(prompt) },
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)) {
                Text(prompt, style = com.dua.ai.ui.theme.HomeTaskPromptStyle)
            }
        }
    }
}

@Composable
internal fun SuppliedUserMessage(message: ChatMessage, onOpenFile: (com.dua.ai.files.FileRef) -> Unit, modifier: Modifier = Modifier) {
    val (quote, text) = remember(message.text) { ReplyQuote.split(message.text) }
    val (body, names) = remember(text) { splitFileNames(text) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (message.files.isNotEmpty()) MessageFileChips(names, message.files, onOpenFile)
        else if (names.isNotEmpty()) Text(names.joinToString(", "), color = DuaTheme.semantic.text, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.widthIn(max = 280.dp)
            .clip(DuaShape.userBubble)
            .background(DuaTheme.colors.userBubble).padding(horizontal = 16.dp, vertical = 12.dp)) {
            SelectionContainer {
                Column {
                    quote?.let { BubbleQuote(it) }
                    Text(body, color = DuaTheme.semantic.text, style = ChatMessageStyle)
                }
            }
        }
    }
}

@Composable
internal fun SuppliedAssistantTurn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text("✨", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

private enum class SuppliedComposerAction { Mic, Send, Stop, CancelUpload }

/** Supplied expanding pill; only the callbacks and real request/upload states are added. */
@Composable
internal fun GeminiBottomBar(
    state: ChatUiState,
    actions: ChatActions,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    focusRequester: FocusRequester,
    placeholder: String = "Ask Dua AI...",
    sendEnabled: Boolean = state.canSend,
) {
    val currentUploads by rememberUpdatedState(state.uploads)
    val cancelUploads = remember(actions.onCancelUpload) {
        { currentUploads.filter { it.uploading }.forEach { actions.onCancelUpload(it.id) } }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SuppliedComposer(state.draft, state.typing, state.readingFiles, sendEnabled,
            state.attachments.isNotEmpty() || state.uploads.any { it.ref != null },
            state.uploads.any { it.uploading }, actions.onDraftChange, actions.onSend,
            actions.onStop, cancelUploads, onAttach, onVoice, focusRequester, placeholder)
    }
}

/** Primitive inputs let Compose skip this field while reply tokens update message history. */
@Composable
private fun SuppliedComposer(
    text: String, typing: Boolean, readingFiles: Boolean, canSend: Boolean,
    hasAttachments: Boolean, uploading: Boolean, onDraftChange: (String) -> Unit,
    onSend: () -> Unit, onStop: () -> Unit, onCancelUploads: () -> Unit,
    onAttach: () -> Unit, onVoice: () -> Unit, focusRequester: FocusRequester,
    placeholder: String = "Ask Dua AI...",
) {
    val surfaceColor = DuaTheme.colors.composer
    val textColor = MaterialTheme.colorScheme.onSurface
    var focused by remember { mutableStateOf(false) }
    val hasContent = text.isNotBlank() || hasAttachments
    val action = when {
        typing -> SuppliedComposerAction.Stop
        uploading -> SuppliedComposerAction.CancelUpload
        hasContent -> SuppliedComposerAction.Send
        else -> SuppliedComposerAction.Mic
    }
    val motion = rememberReplyMotionAllowed()
    Row(
        modifier = Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth().navigationBarsPadding().imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("supplied-composer")
            .composerFocusPop(focused, true)
            .shadow(6.dp, DuaShape.composer, clip = false)
            .clip(DuaShape.composer).background(surfaceColor).background(DuaTheme.colors.floatingSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (focused) .65f else .35f), DuaShape.composer)
            .animateContentSize(animationSpec = if (motion) DuaMotion.gentle() else snap())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        IconButton(onClick = onAttach, enabled = !typing && !readingFiles, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, "Add", tint = textColor)
        }
        Box(Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = text, onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = SolidColor(DuaTheme.semantic.primary), maxLines = 6,
                decorationBox = { inner -> Box(Modifier.heightIn(min = Sizes.touchTarget), contentAlignment = Alignment.CenterStart) { inner() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "Message" },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            )
        }
        val control: @Composable (SuppliedComposerAction) -> Unit = { target ->
            val enabled = target != SuppliedComposerAction.Send || canSend
            IconButton(
                enabled = enabled,
                onClick = {
                    when (target) {
                        SuppliedComposerAction.Send -> if (canSend) onSend()
                        SuppliedComposerAction.Stop -> onStop()
                        SuppliedComposerAction.CancelUpload -> onCancelUploads()
                        SuppliedComposerAction.Mic -> onVoice()
                    }
                },
                modifier = if (target == SuppliedComposerAction.Mic) Modifier.size(48.dp) else Modifier.size(48.dp)
                    .clip(CircleShape).background(DuaTheme.semantic.primary),
            ) {
                val (icon, description) = when (target) {
                    SuppliedComposerAction.Mic -> Icons.Default.Mic to "Voice"
                    SuppliedComposerAction.Send -> Icons.Default.Send to "Send"
                    SuppliedComposerAction.Stop -> Icons.Default.Stop to "Stop response"
                    SuppliedComposerAction.CancelUpload -> Icons.Default.Close to "Cancel upload"
                }
                Icon(icon, description, tint = if (target == SuppliedComposerAction.Mic) textColor else MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (motion) AnimatedContent(
            targetState = action,
            transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) },
            label = "MicToSendButton",
        ) { control(it) } else control(action)
    }
}

@Composable
internal fun GeminiLoadingShimmer() {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant
    )

    val motion = rememberReplyMotionAllowed()
    val transition = if (motion) rememberInfiniteTransition(label = "Shimmer") else null
    val translateAnim = transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Translate"
    )?.value ?: 600f

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 400f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth().testTag("supplied-shimmer")
            .semantics { contentDescription = "Dua is thinking" }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
    }
}
