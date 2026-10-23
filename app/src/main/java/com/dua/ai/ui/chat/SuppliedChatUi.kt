package com.dua.ai.ui.chat

import android.app.Activity
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.dua.ai.model.ChatMessage
import com.dua.ai.ui.theme.DuaFixedColors
import com.dua.ai.ui.theme.DuaTheme

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
) {
    val parentDark = DuaTheme.colors.isDark
    val view = LocalView.current
    DisposableEffect(view, parentDark) {
        onDispose {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !parentDark
                    isAppearanceLightNavigationBars = !parentDark
                }
            }
        }
    }
    DuaTheme(darkTheme = true) {
      MaterialTheme(colorScheme = darkColorScheme(), typography = Typography()) {
        Scaffold(
            modifier = Modifier.testTag("supplied-chat"),
            containerColor = DuaFixedColors.SuppliedBg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Dua AI ✨",
                            color = DuaFixedColors.SuppliedText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.heightIn(min = 48.dp)
                                .combinedClickable(role = Role.Button, onClick = onMenu,
                                    onLongClickLabel = "Choose model", onLongClick = onModel)
                                .semantics { contentDescription = "Dua AI, chats and settings" }.wrapContentHeight(),
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DuaFixedColors.SuppliedBg),
                )
            },
            bottomBar = {
                Column {
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
                    GeminiBottomBar(state, actions, onAttach, { dictation?.start?.invoke() ?: actions.onOpenVoice() }, focusRequester)
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
              CompositionLocalProvider(LocalCodeExecutionUi provides CodeExecutionUi(state.codeSnippetUnavailableReason, actions.onRunCodeSnippet)) {
                MessageList(state, actions, listState, imagineActions = imagineActions, suppliedStyle = true,
                    emptyContent = {
                        val name = state.profileName.trim().substringBefore(" ").takeIf { it.isNotBlank() }
                        SuppliedAssistantTurn(Modifier.padding(vertical = 10.dp)) {
                            Text(
                                text = (if (name != null) "Hello $name! " else "Hello! ") +
                                    "Main Dua AI hoon. Aaj hum kis project par kaam kar rahe hain?",
                                color = DuaFixedColors.SuppliedText, fontSize = 16.sp, lineHeight = 24.sp,
                            )
                        }
                    })
              }
                JumpToLatest(showLatest, newReplies = newReplies, onClick = onLatest,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
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
        else if (names.isNotEmpty()) Text(names.joinToString(", "), color = DuaFixedColors.SuppliedText, fontSize = 12.sp)
        Box(Modifier.widthIn(max = 280.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(DuaFixedColors.SuppliedBubble).padding(horizontal = 16.dp, vertical = 12.dp)) {
            SelectionContainer {
                Column {
                    quote?.let { BubbleQuote(it) }
                    Text(body, color = DuaFixedColors.SuppliedText, fontSize = 16.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
internal fun SuppliedAssistantTurn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text("✨", fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
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
) {
    val surfaceColor = DuaFixedColors.SuppliedSurface
    val textColor = DuaFixedColors.SuppliedText
    val text = state.draft
    val hasContent = text.isNotBlank() || state.attachments.isNotEmpty() || state.uploads.any { it.ref != null }
    val action = when {
        state.typing -> SuppliedComposerAction.Stop
        state.uploads.any { it.uploading } -> SuppliedComposerAction.CancelUpload
        hasContent -> SuppliedComposerAction.Send
        else -> SuppliedComposerAction.Mic
    }
    val motion = rememberReplyMotionAllowed()
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("supplied-composer")
            .clip(RoundedCornerShape(28.dp)).background(surfaceColor)
            .animateContentSize(animationSpec = if (motion) spring(
                dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow) else snap())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        IconButton(onClick = onAttach, enabled = !state.typing && !state.readingFiles, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, "Add", tint = textColor)
        }
        Box(Modifier.weight(1f).padding(vertical = 12.dp, horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text("Ask Dua AI...", color = Color.Gray, fontSize = 16.sp)
            BasicTextField(
                value = text, onValueChange = actions.onDraftChange,
                textStyle = TextStyle(color = textColor, fontSize = 16.sp),
                cursorBrush = SolidColor(DuaFixedColors.SuppliedPrimary), maxLines = 6,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    .semantics { contentDescription = "Message" },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (state.canSend) actions.onSend() }),
            )
        }
        val control: @Composable (SuppliedComposerAction) -> Unit = { target ->
            val enabled = target != SuppliedComposerAction.Send || state.canSend
            IconButton(
                enabled = enabled,
                onClick = {
                    when (target) {
                        SuppliedComposerAction.Send -> if (state.canSend) actions.onSend()
                        SuppliedComposerAction.Stop -> actions.onStop()
                        SuppliedComposerAction.CancelUpload -> state.uploads.filter { it.uploading }.forEach { actions.onCancelUpload(it.id) }
                        SuppliedComposerAction.Mic -> onVoice()
                    }
                },
                modifier = if (target == SuppliedComposerAction.Mic) Modifier.size(48.dp) else Modifier.size(48.dp)
                    .clip(CircleShape).background(DuaFixedColors.SuppliedPrimary),
            ) {
                val (icon, description) = when (target) {
                    SuppliedComposerAction.Mic -> Icons.Default.Mic to "Voice"
                    SuppliedComposerAction.Send -> Icons.Default.Send to "Send"
                    SuppliedComposerAction.Stop -> Icons.Default.Stop to "Stop response"
                    SuppliedComposerAction.CancelUpload -> Icons.Default.Close to "Cancel upload"
                }
                Icon(icon, description, tint = if (target == SuppliedComposerAction.Mic) textColor else DuaFixedColors.SuppliedOnPrimary)
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
        DuaFixedColors.SuppliedSurface,
        DuaFixedColors.SuppliedGlowBlue.copy(alpha = 0.6f),
        DuaFixedColors.SuppliedGlowPurple.copy(alpha = 0.6f),
        DuaFixedColors.SuppliedGlowRose.copy(alpha = 0.6f),
        DuaFixedColors.SuppliedSurface
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
