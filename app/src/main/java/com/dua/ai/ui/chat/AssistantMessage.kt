package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ReplySources
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Assistant turn: full width, no bubble; markdown body, source chips and an action row. */
@Composable
fun AssistantMessage(
    message: ChatMessage,
    isLast: Boolean,
    typing: Boolean,
    speaking: Boolean,
    speakEnabled: Boolean,
    generatedVoice: Boolean,
    retryWebMode: String?,
    onRetry: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    thinkingExpanded: Boolean = false,
    codeExpanded: Boolean = false,
) {
    val parts = remember(message.text) { ReplySources.split(message.text) }
    val streaming = message.streaming
    Column(modifier.fillMaxWidth()) {
        if (message.thinking != null) ThinkingBlock(
            messageId = message.id,
            thinking = message.thinking,
            seconds = message.thinkingSeconds,
            live = streaming,
            answering = message.text.isNotEmpty(),
            startedAt = message.startedAt,
            initiallyExpanded = thinkingExpanded,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        // Run code: what the provider executed, collapsed above the answer (the answer explains the result).
        if (message.codeRuns.isNotEmpty()) CodeRuns(message.id, message.codeRuns, Modifier.padding(bottom = Spacing.sm), initiallyExpanded = codeExpanded)
        if (streaming && message.text.isEmpty() && message.thinking == null && message.codeRuns.isEmpty()) TypingIndicator()
        if (parts.body.isNotEmpty()) MarkdownMessage(parts.body)
        message.codeSnippet?.let { CodeBlock(code = it, language = null, modifier = Modifier.padding(top = Spacing.sm)) }
        if (parts.sources.isNotEmpty() && !streaming) SourceChips(parts.sources, Modifier.padding(top = Spacing.md))
        message.notice?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm))
        }
        if (message.state == "stopped") Text(if (message.text.isEmpty()) "Stopped" else "Stopped · partial reply",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        if (message.state == "complete" || (message.state == "stopped" && message.text.isNotEmpty())) MessageActions(
            copyText = message.text + (message.codeSnippet?.let { "\n" + it } ?: "") + com.dua.ai.model.CodeRuns.copyText(message.codeRuns),
            showRetry = isLast,
            retryEnabled = isLast && !typing,
            retryDescription = when (retryWebMode) {
                "search" -> "Retry response with web search"
                "page" -> "Retry response by reading its original link"
                else -> "Retry response with web off"
            },
            speaking = speaking,
            speakEnabled = speakEnabled,
            speakDescription = if (speaking) "Stop response voice" else if (generatedVoice) "Generate AI voice for this response" else "Speak response with device voice",
            onRetry = onRetry,
            onSpeak = onSpeak,
        )
    }
}

/** Copy · retry · read aloud, as quiet icons with 48-dp touch targets. */
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
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    // Pull the row left so the first icon lines up with the text above it.
    Row(Modifier.padding(top = Spacing.xs).offset(x = -Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { clipboard.setText(AnnotatedString(copyText)); copied = true }) {
            Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (copied) "Response copied" else "Copy response",
                tint = tint, modifier = Modifier.size(Sizes.iconSmall))
        }
        if (showRetry) IconButton(onClick = onRetry, enabled = retryEnabled) {
            Icon(Icons.Outlined.Refresh, retryDescription, tint = if (retryEnabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(Sizes.iconSmall))
        }
        IconButton(onClick = onSpeak, enabled = speakEnabled) {
            Icon(if (speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, speakDescription,
                tint = if (speakEnabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(Sizes.iconSmall))
        }
    }
}
