package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.painterResource
import com.dua.ai.R
import com.dua.ai.ui.theme.ChatShapes
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

@Composable
fun MessageList(state: ChatUiState, actions: ChatActions, listState: LazyListState, modifier: Modifier = Modifier,
    imagineActions: ImagineActions = actions.imagine, searchMessageId: String? = null,
    onReply: ((com.dua.ai.model.ChatMessage) -> Unit)? = null,
    /** Extra bottom room so the last message can scroll clear of an overlay (floating Chat/Task toggle). */
    bottomOverlay: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f)) {
    // Ids present on first show (history) do not animate in; only bubbles that arrive later do.
    val initialIds = remember(state.chatId) { state.messages.map { it.id }.toHashSet() }
    val motion = rememberReplyMotionAllowed()
    val lastId = state.messages.lastOrNull()?.id
    val lastAssistantId = state.messages.lastOrNull { !it.isUser }?.id
    var actionsId by rememberSaveable(state.chatId) { mutableStateOf<String?>(null) }
    var canvas by remember(state.chatId) { mutableStateOf<ArtifactCanvasSession?>(null) }
    LaunchedEffect(canvas?.messageId, state.messages) {
        if (canvas?.messageId?.let { id -> state.messages.none { it.id == id } } == true) canvas = null
    }
    var popupId by remember(state.chatId) { mutableStateOf<String?>(null) }
    var selectTextId by remember(state.chatId) { mutableStateOf<String?>(null) }
    val selectedMessage = state.messages.firstOrNull { it.id == actionsId }
    LaunchedEffect(actionsId, selectedMessage?.id) { if (actionsId != null && selectedMessage == null) actionsId = null }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("chat-messages"),
        contentPadding = PaddingValues(start = ChatSizes.listPaddingH, end = ChatSizes.listPaddingH, top = ChatSizes.listPaddingTop, bottom = ChatSizes.listPaddingBottom + bottomOverlay),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(state.messages, key = { _, message -> message.id }, contentType = { _, message -> if (message.isUser) "user" else "assistant" }) { index, message ->
            val selected = message.id == searchMessageId
            Column(Modifier.padding(top = MessageGrouping.gapBefore(state.messages, index)).widthIn(max = Sizes.contentMaxWidth).fillMaxWidth()
                .then(if (motion) Modifier.animateItem(
                    fadeInSpec = DuaMotion.gentle(),
                    placementSpec = DuaMotion.bouncy(),
                ) else Modifier)
                .bubbleAppear(motion && message.id !in initialIds)
                .testTag("message-${message.id}")
                .then(if (selected) Modifier.border(ChatSizes.searchSelectionBorder, ChatTheme.colors.accent, ChatShapes.searchSelection) else Modifier)
                .semantics { if (selected) stateDescription = "Selected search result" }) {
                val openActions = { canvas = null; popupId = message.id }
                if (popupId == message.id) MessagePopup(message, onReply,
                    onSelectText = { selectTextId = message.id }, onMore = { actionsId = message.id }, onDismiss = { popupId = null })
                val longPressHaptic = { actions.haptic(ChatHaptic.LongPress) }
                val replyHere = onReply?.takeIf { !message.streaming && message.text.isNotBlank() }
                SwipeToReply(enabled = replyHere != null, onReply = { replyHere?.invoke(message) },
                    haptic = { actions.haptic(ChatHaptic.Tap) }) {
                if (message.isUser) UserMessage(message.text, files = message.files, onOpenFile = actions.onOpenFile,
                    onLongPress = openActions, longPressHaptic = longPressHaptic)
                else AssistantTurn(modifier = Modifier.messageLongPress(openActions, longPressHaptic)) {
                    // Imagine replies own their layout; Agent replies with a created image keep their text (image above it).
                    if (message.isImagine && (message.imagine != null || message.text.isBlank() && !message.streaming)) ImagineReply(message, imagineActions, videoAvailable = state.videoAvailable)
                    else AssistantMessage(
                        message = message,
                        onOpenMedia = { imagineActions.onOpen(message.id, it) },
                        onOpenCanvas = { artifacts -> actionsId = null; canvas = ArtifactCanvasSession(message.id, artifacts) },
                        isLast = message.id == lastId,
                        typing = state.typing,
                        speaking = state.speakingId == message.id,
                        speakEnabled = state.generatedVoice || state.speechAvailable,
                        generatedVoice = state.generatedVoice,
                        retryWebMode = actions.retryWebMode(message.id),
                        onRetry = { actions.onRetry(message.id) },
                        onSpeak = { actions.onSpeak(message) },
                        thinkingExpanded = message.id in state.expandedThinking,
                        codeExpanded = message.id in state.expandedCode,
                        buildExpanded = message.id in state.expandedBuild,
                        buildPr = state.buildPr[message.id],
                        buildPrHint = state.buildPrHint,
                        buildPrDraft = state.buildConfig?.pr?.draft ?: true,
                        confirmPr = state.confirmPr == message.id,
                        onCreatePr = { title -> actions.onCreatePr(message.id, title) },
                        onApproval = { approvalId, approve -> actions.onAgentApproval(message.id, approvalId, approve) },
                        showActions = message.id == lastAssistantId,
                    )
                }
                }
                if (message.id == state.summarisedThroughId && message.id != lastId) SummaryDivider(Modifier.padding(top = Spacing.md))
            }
        }
        // Streaming replies render their own progress; the orb covers non-streaming waits.
        if (state.typing && state.messages.none { it.streaming }) item(key = "typing") {
            AssistantTurn(modifier = Modifier.padding(top = if (state.messages.isEmpty()) 0.dp else ChatSizes.messageSpacing).widthIn(max = Sizes.contentMaxWidth)) { TypingIndicator(Modifier.heightIn(min = Sizes.touchTarget)) }
        }
    }
    if (selectedMessage != null) MessageActionsSheet(selectedMessage, state, actions, imagineActions, onDismiss = { actionsId = null }, onReply = onReply)
    state.messages.firstOrNull { it.id == selectTextId }?.let { m -> SelectTextSheet(copyTextOf(m), onDismiss = { selectTextId = null }) }
    canvas?.let { session -> ArtifactCanvasSheet(session.artifacts, onDismiss = { canvas = null }) }
}

/** Reference assistant turn: no avatar, transparent box with 16/10 dp padding, left aligned. */
@Composable
private fun AssistantTurn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).testTag("assistant-turn")
        .background(ChatTheme.colors.assistantBubble, ChatShapes.bubble)
        .padding(horizontal = ChatSizes.bubblePaddingH, vertical = ChatSizes.bubblePaddingV)) { content() }
}

/** Marks where the model's view switches from a summary to the full messages (long context). */
@Composable
fun SummaryDivider(modifier: Modifier = Modifier) {
    val c = ChatTheme.colors
    val color = c.divider
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "Earlier messages summarised. The model sees a summary of the messages above and the full messages below."
    }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(ChatSizes.divider).background(color))
        Icon(Icons.Outlined.Compress, contentDescription = null, tint = c.mutedText,
            modifier = Modifier.padding(start = Spacing.md).size(Sizes.iconTiny))
        Text("Earlier messages summarised", style = MaterialTheme.typography.labelMedium, color = c.mutedText,
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.md))
        Box(Modifier.weight(1f).height(ChatSizes.divider).background(color))
    }
}

/** Approved ambient orb while a reply is pending; retains the existing status announcement. */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val c = ChatTheme.colors
    Row(modifier.clearAndSetSemantics {
        contentDescription = "Dua is thinking"; liveRegion = LiveRegionMode.Polite
    }, verticalAlignment = Alignment.CenterVertically) {
        com.dua.ai.ui.components.RiveOrFallback(com.dua.ai.R.raw.rive_thinking_dots, Modifier.size(com.dua.ai.ui.theme.AmbientTokens.orbSize)) { ThinkingOrb() }
        Spacer(Modifier.width(Spacing.md))
        Text("Thinking", style = MaterialTheme.typography.bodyMedium, color = c.mutedText)
    }
}

/** Telegram-style long-press popup; "More" keeps every action of the full sheet reachable. */
@Composable
private fun MessagePopup(message: com.dua.ai.model.ChatMessage, onReply: ((com.dua.ai.model.ChatMessage) -> Unit)?,
    onSelectText: () -> Unit, onMore: () -> Unit, onDismiss: () -> Unit) {
    val text = copyTextOf(message)
    val textActions = rememberTextMessageActions(text, !message.isUser)
    val hasText = text.isNotBlank()
    val items = buildList {
        if (onReply != null) add(PopupItem("reply", "Reply", Icons.AutoMirrored.Outlined.Reply, hasText && !message.streaming) { onReply(message) })
        add(PopupItem("copy", "Copy", Icons.Outlined.ContentCopy, hasText) { textActions.copy() })
        add(PopupItem("select", "Select text", Icons.Outlined.TextFields, message.text.isNotBlank(), onSelectText))
        add(PopupItem("share", "Share", Icons.Outlined.Share, hasText) { textActions.share() })
        add(PopupItem("more", "More", Icons.Outlined.MoreHoriz, true, onMore))
    }
    TelegramMessagePopup(alignEnd = message.isUser, items = items, onDismiss = onDismiss)
}
