package com.dua.ai.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRuns
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.delay

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
private fun rememberTextMessageActions(copyText: String, assistant: Boolean = true): TextMessageActions {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember(copyText) { mutableStateOf(false) }
    var shareError by remember(copyText) { mutableStateOf<String?>(null) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    return TextMessageActions(copied, shareError, copy = { clipboard.setText(AnnotatedString(copyText)); copied = true }, share = {
        try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, copyText)
            }, if (assistant) "Share reply" else "Share message"))
            shareError = null
            true
        } catch (_: ActivityNotFoundException) {
            shareError = if (assistant) "No app is available to share this reply." else "No app is available to share this message."
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
    imagineActions: ImagineActions, onDismiss: () -> Unit) {
    val assistant = !message.isUser
    val copyText = message.text + if (assistant) CodeRuns.copyText(message.codeRuns) else ""
    val textActions = rememberTextMessageActions(copyText, assistant)
    val retryEnabled = assistant && !message.isImagine && state.messages.lastOrNull()?.id == message.id && !state.typing &&
        message.state in listOf("complete", "stopped")
    val speaking = state.speakingId == message.id
    val speakEnabled = assistant && !message.streaming && (state.generatedVoice || state.speechAvailable) && (speaking || message.text.isNotBlank())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag("message-actions-sheet")) {
            Text("Message actions", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.sm).semantics { heading() })
            SheetMessageAction("copy", "Copy", if (assistant) "Copy response" else "Copy message", Icons.Outlined.ContentCopy, copyText.isNotBlank()) {
                textActions.copy(); onDismiss()
            }
            if (assistant) SheetMessageAction("retry", "Retry", replyRetryDescription(actions.retryWebMode(message.id)), Icons.Outlined.Refresh, retryEnabled) {
                onDismiss(); actions.onRetry(message.id)
            }
            SheetMessageAction("share", "Share", if (assistant) "Share response" else "Share message", Icons.Outlined.Share,
                copyText.isNotBlank() || message.isImagine && message.media.isNotEmpty()) {
                if (message.isImagine && message.media.isNotEmpty()) { onDismiss(); imagineActions.onShare(message.media) }
                else if (textActions.share()) onDismiss()
            }
            if (assistant) SheetMessageAction("speak", if (speaking) "Stop speaking" else "Speak", replySpeakDescription(speaking, state.generatedVoice),
                if (speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, speakEnabled) {
                onDismiss(); actions.onSpeak(message)
            }
            textActions.shareError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.heightIn(min = Spacing.lg))
        }
    }
}

@Composable
private fun SheetMessageAction(key: String, label: String, description: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).testTag("message-action-$key")
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button }) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.lg))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
        }
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
) {
    val actions = rememberTextMessageActions(copyText)
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .86f)
    val copyTint = if (copyText.isNotBlank()) tint else tint.copy(alpha = .38f)
    FlowRow(Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TextButton(onClick = actions.copy, enabled = copyText.isNotBlank(), modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
            Icon(if (actions.copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (actions.copied) "Response copied" else "Copy response",
                tint = copyTint, modifier = Modifier.size(Sizes.iconSmall))
            Text(if (actions.copied) "Copied" else "Copy", color = copyTint, modifier = Modifier.padding(start = Spacing.xs))
        }
        if (showRetry) TextButton(onClick = onRetry, enabled = retryEnabled, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
            Icon(Icons.Outlined.Refresh, retryDescription, tint = if (retryEnabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(Sizes.iconSmall))
            Text("Retry", modifier = Modifier.padding(start = Spacing.xs))
        }
        IconButton(onClick = { actions.share() }, enabled = copyText.isNotBlank(), modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(Icons.Outlined.Share, "Share response", tint = copyTint, modifier = Modifier.size(Sizes.iconSmall))
        }
        IconButton(onClick = onSpeak, enabled = speakEnabled, modifier = Modifier.size(Sizes.touchTarget)) {
            Icon(if (speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, speakDescription,
                tint = if (speakEnabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(Sizes.iconSmall))
        }
    }
    actions.shareError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}
