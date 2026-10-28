package com.dua.ai.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRuns
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.SheetDisabledAlpha
import com.dua.ai.ui.theme.SheetNoteStyle
import com.dua.ai.ui.theme.SheetRowStyle
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetTheme
import androidx.compose.foundation.clickable
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.delay

internal fun copyTextOf(message: ChatMessage): String = message.text + if (!message.isUser) CodeRuns.copyText(message.codeRuns) else ""

/** Full message text in a selectable view (Telegram's "Select text"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectTextSheet(text: String, onDismiss: () -> Unit) {
    DuaSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = SheetSizes.paddingH).padding(bottom = SheetSizes.paddingBottom)
            .testTag("select-text-sheet")) {
            SheetTitle("Select text")
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = SheetTheme.colors.text)
            }
        }
    }
}

internal fun replyRetryDescription(mode: String?): String = when (mode) {
    "search" -> "Retry response with web search"
    "page" -> "Retry response by reading its original link"
    else -> "Retry response with web off"
}

internal fun replySpeakDescription(speaking: Boolean, generatedVoice: Boolean): String =
    if (speaking) "Stop response voice" else if (generatedVoice) "Generate AI voice for this response" else "Speak response with device voice"

/** Same full-message clipboard/Android chooser handlers for the toolbar and long-press sheet. */
internal class TextMessageActions(val copied: Boolean, val shareError: String?, val copy: () -> Unit, val share: () -> Boolean)

@Composable
internal fun rememberTextMessageActions(copyText: String, assistant: Boolean = true, shareTitle: String? = null): TextMessageActions {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalChatHaptic.current
    var copied by remember(copyText) { mutableStateOf(false) }
    var shareError by remember(copyText) { mutableStateOf<String?>(null) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    return TextMessageActions(copied, shareError, copy = { clipboard.setText(AnnotatedString(copyText)); haptic(ChatHaptic.Tap); copied = true }, share = {
        try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, copyText)
            }, shareTitle ?: if (assistant) "Share reply" else "Share message"))
            shareError = null
            true
        } catch (_: ActivityNotFoundException) {
            shareError = if (shareTitle != null) "No app is available to share this code." else if (assistant) "No app is available to share this reply." else "No app is available to share this message."
            false
        }
    })
}

/** Observe only long presses; child links/copy controls own taps and messages expose no click action. */
@Composable
internal fun Modifier.messageLongPress(onLongPress: () -> Unit, haptic: () -> Unit = {}): Modifier {
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentHaptic by rememberUpdatedState(haptic)
    return pointerInput(Unit) {
        detectTapGestures(onLongPress = { currentHaptic(); currentLongPress() })
    }.semantics {
        onLongClick(label = "Message actions") { currentLongPress(); true }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionsSheet(message: ChatMessage, state: ChatUiState, actions: ChatActions,
    imagineActions: ImagineActions, onDismiss: () -> Unit, onReply: ((ChatMessage) -> Unit)? = null) {
    var selecting by remember(message.id) { mutableStateOf(false) }
    if (selecting) { SelectTextSheet(copyTextOf(message), onDismiss = { selecting = false; onDismiss() }); return }
    val assistant = !message.isUser
    val copyText = copyTextOf(message)
    val textActions = rememberTextMessageActions(copyText, assistant)
    val haptic = LocalChatHaptic.current
    val retryEnabled = assistant && !message.isImagine && state.messages.lastOrNull()?.id == message.id && !state.typing &&
        message.state in listOf("complete", "stopped")
    val speaking = state.speakingId == message.id
    val speakEnabled = assistant && !message.streaming && (state.generatedVoice || state.speechAvailable) && (speaking || message.text.isNotBlank())
    DuaSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = SheetSizes.paddingH).padding(bottom = SheetSizes.paddingBottom)
            .testTag("message-actions-sheet")) {
            SheetTitle("Message actions")
            SheetGroup {
            SheetMessageAction("copy", "Copy", if (assistant) "Copy response" else "Copy message", Icons.Outlined.ContentCopy, copyText.isNotBlank()) {
                textActions.copy(); onDismiss()
            }
            if (!assistant) SheetDivider()
            if (!assistant) SheetMessageAction("edit", "Edit", "Edit message and resend", Icons.Outlined.Edit,
                !state.typing && com.dua.ai.model.MessageEdits.editable(message)) {
                onDismiss(); actions.onEditMessage(message.id)
            }
            if (assistant) SheetDivider()
            if (assistant) SheetMessageAction("retry", "Retry", replyRetryDescription(actions.retryWebMode(message.id)), Icons.Outlined.Refresh, retryEnabled) {
                haptic(ChatHaptic.Tap); onDismiss(); actions.onRetry(message.id)
            }
            SheetDivider()
            SheetMessageAction("share", "Share", if (assistant) "Share response" else "Share message", Icons.Outlined.Share,
                copyText.isNotBlank() || message.isImagine && message.media.isNotEmpty()) {
                if (message.isImagine && message.media.isNotEmpty()) { onDismiss(); imagineActions.onShare(message.media) }
                else if (textActions.share()) onDismiss()
            }
            if (assistant) SheetDivider()
            if (assistant) SheetMessageAction("speak", if (speaking) "Stop speaking" else "Speak", replySpeakDescription(speaking, state.generatedVoice),
                if (speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, speakEnabled) {
                onDismiss(); actions.onSpeak(message)
            }
            }
            Spacer(Modifier.height(SheetSizes.sectionGap))
            SheetGroup {
                if (onReply != null) {
                    SheetMessageAction("reply", "Reply", "Reply to this message", Icons.AutoMirrored.Outlined.Reply, !message.streaming && message.text.isNotBlank()) {
                        onDismiss(); onReply(message)
                    }
                    SheetDivider()
                }
                SheetMessageAction("select", "Select text", "Select part of this message", Icons.Outlined.TextFields, message.text.isNotBlank()) { selecting = true }
            }
            if (assistant && !message.isImagine && !message.streaming && message.text.isNotBlank()) {
                Spacer(Modifier.height(SheetSizes.sectionGap))
                SheetGroup {
                    SheetMessageAction("summarize", "Summarize", "Summarize this reply, on this phone when possible", Icons.AutoMirrored.Outlined.ShortText, true) {
                        onDismiss(); actions.onWriting(message.text, false, com.dua.ai.ondevice.WritingTask.Summarize)
                    }
                    SheetDivider()
                    SheetMessageAction("rewrite", "Rewrite", "Rewrite this reply, on this phone when possible", Icons.Outlined.EditNote, true) {
                        onDismiss(); actions.onWriting(message.text, false, null)
                    }
                }
                Text("On-device when supported", style = SheetNoteStyle, color = SheetTheme.colors.subText, modifier = Modifier.padding(top = Spacing.xs))
            }
            textActions.shareError?.let { Text(it, style = SheetNoteStyle, color = SheetTheme.colors.destructive, modifier = Modifier.padding(top = SheetSizes.sectionGap)) }
        }
    }
}

@Composable
private fun SheetMessageAction(key: String, label: String, description: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val c = SheetTheme.colors
    val alpha = if (enabled) 1f else SheetDisabledAlpha
    Row(Modifier.fillMaxWidth().heightIn(min = SheetSizes.rowMin).testTag("message-action-$key")
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }
        .padding(horizontal = SheetSizes.rowPaddingH, vertical = SheetSizes.rowPaddingV), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = c.icon.copy(alpha = alpha), modifier = Modifier.size(SheetSizes.rowIcon))
        Spacer(Modifier.width(SheetSizes.rowGap))
        Text(label, style = SheetRowStyle, color = c.text.copy(alpha = alpha))
    }
}

/** Copy · retry · read aloud. Actions wrap when a large font needs more room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageActions(
    copyText: String,
    showRetry: Boolean,
    retryEnabled: Boolean,
    retryDescription: String,
    speaking: Boolean,
    speakEnabled: Boolean,
    speakDescription: String,
    onRetry: () -> Unit,
    onSpeak: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    val actions = rememberTextMessageActions(copyText)
    val haptic = LocalChatHaptic.current
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .86f)
    val copyTint = if (copyText.isNotBlank()) tint else tint.copy(alpha = .38f)
    Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (showRetry) IconButton(onClick = { haptic(ChatHaptic.Tap); onRetry() }, enabled = retryEnabled,
                modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Outlined.Refresh, retryDescription, tint = if (retryEnabled) tint else tint.copy(alpha = .38f),
                    modifier = Modifier.size(Sizes.iconSmall))
            }
            IconButton(onClick = { actions.share() }, enabled = copyText.isNotBlank(), modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Outlined.Share, "Share response", tint = copyTint, modifier = Modifier.size(Sizes.iconSmall))
            }
            IconButton(onClick = actions.copy, enabled = copyText.isNotBlank(), modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(if (actions.copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    if (actions.copied) "Response copied" else "Copy response", tint = copyTint, modifier = Modifier.size(Sizes.iconSmall))
            }
            if (onMore != null) IconButton(onClick = onMore, modifier = Modifier.size(Sizes.touchTarget)) {
                Icon(Icons.Outlined.MoreHoriz, "More response actions", tint = tint, modifier = Modifier.size(Sizes.iconSmall))
            }
        }
        IconButton(onClick = onSpeak, enabled = speakEnabled, modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(if (speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, speakDescription,
                tint = if (speakEnabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(Sizes.iconSmall))
        }
    }
    actions.shareError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}
