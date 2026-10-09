package com.dua.ai.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

@Composable
fun MessageList(state: ChatUiState, actions: ChatActions, listState: LazyListState, modifier: Modifier = Modifier,
    imagineActions: ImagineActions = actions.imagine) {
    val lastId = state.messages.lastOrNull()?.id
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(state.messages, key = { it.id }, contentType = { if (it.isUser) "user" else "assistant" }) { message ->
            Column(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth()) {
                if (message.isUser) UserMessage(message.text, files = message.files, onOpenFile = actions.onOpenFile)
                else if (message.isImagine) ImagineReply(message, imagineActions, videoAvailable = state.videoAvailable)
                else AssistantMessage(
                    message = message,
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
                )
                if (message.id == state.summarisedThroughId && message.id != lastId) SummaryDivider(Modifier.padding(top = Spacing.md))
            }
        }
        // Streaming replies render their own progress; the dots cover non-streaming waits.
        if (state.typing && state.messages.none { it.streaming }) item(key = "typing") {
            Box(Modifier.widthIn(max = Sizes.contentMaxWidth).fillMaxWidth()) { TypingIndicator() }
        }
    }
}

/** Marks where the model's view switches from a summary to the full messages (long context). */
@Composable
fun SummaryDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "Earlier messages summarised. The model sees a summary of the messages above and the full messages below."
    }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(color))
        Icon(Icons.Outlined.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.md).size(Sizes.iconTiny))
        Text("Earlier messages summarised", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.md))
        Box(Modifier.weight(1f).height(1.dp).background(color))
    }
}

/** Three softly pulsing dots while a reply is pending. */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase = transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "phase")
    Row(modifier.clearAndSetSemantics {
        contentDescription = "Dua is thinking"; liveRegion = LiveRegionMode.Polite
    }, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val distance = kotlin.math.abs(phase.value - index - 0.5f).coerceAtMost(1.5f)
            Box(Modifier.size(8.dp).alpha(1f - distance / 2f).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            if (index < 2) Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Text("Thinking", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
