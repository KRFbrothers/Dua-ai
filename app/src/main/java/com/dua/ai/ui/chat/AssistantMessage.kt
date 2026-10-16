package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRuns
import com.dua.ai.model.ReplySources
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

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
    buildExpanded: Boolean = false,
    buildPr: BuildPrUiState? = null,
    buildPrHint: String = "",
    buildPrDraft: Boolean = true,
    confirmPr: Boolean = false,
    onCreatePr: (String) -> Unit = {},
    showActions: Boolean = isLast,
    onOpenMedia: (Int) -> Unit = {},
    onOpenCanvas: ((List<com.dua.ai.model.ChatArtifact>) -> Unit)? = null,
) {
    val parts = remember(message.text) { ReplySources.split(message.text) }
    val streaming = message.streaming
    Column(modifier.fillMaxWidth()) {
        val showProgress = streaming && message.progress.isNotEmpty()
        if (showProgress) AgentProgressView(message.progress, Modifier.padding(bottom = Spacing.xs))
        if (message.thinking != null) ThinkingBlock(
            messageId = message.id,
            thinking = message.thinking,
            seconds = message.thinkingSeconds,
            live = streaming,
            answering = message.text.isNotEmpty(),
            startedAt = message.startedAt,
            initiallyExpanded = thinkingExpanded,
            // Agent steps replace the raw live text; the details stay one tap away.
            followStream = !showProgress,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        // Build mode: which repository files the server read for this reply (read-only).
        message.build?.let { BuildFiles(message.id, it, live = streaming, Modifier.padding(bottom = Spacing.sm), initiallyExpanded = buildExpanded) }
        // Run code: what the provider executed, collapsed above the answer (the answer explains the result).
        if (message.codeRuns.isNotEmpty()) CodeRuns(message.id, message.codeRuns, Modifier.padding(bottom = Spacing.sm), initiallyExpanded = codeExpanded, live = streaming)
        // Dua Agent generate_image: the created image sits above the answer (same grid and viewer as Imagine).
        val images = message.media.filter { !it.isVideo }
        if (images.isNotEmpty()) Box(Modifier.padding(bottom = Spacing.sm)) { ImageGrid(images, null, onOpen = onOpenMedia) }
        if (streaming && message.text.isEmpty() && message.thinking == null && !showProgress && message.codeRuns.isEmpty() && message.build == null && images.isEmpty()) ReplyPlaceholder()
        if (parts.body.isNotEmpty()) {
            // New replies fade in once; restored/finished replies render immediately.
            if (streaming) FadeInOnce { MarkdownMessage(parts.body) } else MarkdownMessage(parts.body)
        }
        if (streaming && parts.body.isNotEmpty()) StreamingCursor()
        MessageArtifactCanvas(message, onOpenCanvas)
        if (parts.sources.isNotEmpty() && !streaming) SourceChips(parts.sources, Modifier.padding(top = Spacing.md))
        // Build mode (Phase 5C): the proposed pull request, for review. Only its confirmed Create PR button writes to GitHub.
        if (!streaming) message.proposal?.let {
            BuildProposalCard(message.id, it, buildPr, buildPrHint, buildPrDraft, onCreatePr, Modifier.padding(top = Spacing.md), initiallyConfirming = confirmPr)
        }
        message.notice?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm))
        }
        if (message.state == "stopped") Text(if (message.text.isEmpty()) "Stopped" else "Stopped · partial reply",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
        if (showActions && !typing && (message.state == "complete" || message.state == "stopped")) MessageActions(
            copyText = message.text + CodeRuns.copyText(message.codeRuns),
            showRetry = isLast,
            retryEnabled = isLast && !typing,
            retryDescription = replyRetryDescription(retryWebMode),
            speaking = speaking,
            speakEnabled = speakEnabled && (speaking || message.text.isNotBlank()),
            speakDescription = replySpeakDescription(speaking, generatedVoice),
            onRetry = onRetry,
            onSpeak = onSpeak,
        )
    }
}
