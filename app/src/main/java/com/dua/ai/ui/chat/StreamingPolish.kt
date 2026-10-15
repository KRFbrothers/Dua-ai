package com.dua.ai.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/**
 * Blinking dot shown after a reply while it streams (ChatGPT-style). The blink runs in the draw phase
 * (graphicsLayer alpha), so it never recomposes or re-parses the markdown above it. Hidden from TalkBack.
 */
@Composable
fun StreamingCursor(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "streaming-cursor")
    val alpha = transition.animateFloat(1f, 0.15f,
        infiniteRepeatable(tween(ChatSizes.streamingCursorBlinkMs, easing = LinearEasing), RepeatMode.Reverse), label = "cursor-alpha")
    Box(modifier.padding(top = Spacing.xs).size(ChatSizes.streamingCursor).graphicsLayer { this.alpha = alpha.value }
        .background(MaterialTheme.colorScheme.onSurface, CircleShape).clearAndSetSemantics { })
}

/** Fades content in once when it first appears (the first streamed words); later deltas are not re-animated. */
@Composable
fun FadeInOnce(content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(ChatSizes.streamingFadeInMs)) }
    Box(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha.value }) { content() }
}

/** Shown above the composer while a sent message is being edited. */
@Composable
fun EditingBanner(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.md)
        .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = ChatTheme.colors.mutedText, modifier = Modifier.size(Sizes.iconSmall))
        Text("Editing message. Send replaces it and the replies after it.", style = MaterialTheme.typography.bodySmall,
            color = ChatTheme.colors.mutedText, modifier = Modifier.weight(1f).padding(start = Spacing.sm))
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Cancel") }
    }
}


/**
 * Waiting for the first token: the existing "Thinking" dots plus two softly pulsing placeholder lines
 * (theme colors, draw-phase alpha only). Replaced by the streaming text as soon as it arrives.
 */
@Composable
fun ReplyPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "reply-placeholder")
    val alpha = transition.animateFloat(0.35f, 0.9f,
        infiniteRepeatable(tween(ChatSizes.placeholderPulseMs, easing = LinearEasing), RepeatMode.Reverse), label = "placeholder-alpha")
    val fill = ChatTheme.colors.chipFill
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().testTag("reply-placeholder")) {
        TypingIndicator(Modifier.heightIn(min = Sizes.touchTarget))
        listOf(0.92f, 0.6f).forEach { width ->
            Box(Modifier.padding(top = ChatSizes.placeholderLineGap).fillMaxWidth(width).height(ChatSizes.placeholderLineHeight)
                .graphicsLayer { this.alpha = alpha.value }.background(fill, com.dua.ai.ui.theme.DuaShape.chip)
                .clearAndSetSemantics { })
        }
    }
}
