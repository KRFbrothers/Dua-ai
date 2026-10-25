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
import androidx.compose.runtime.getValue
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
import com.dua.ai.ui.theme.rememberMotionAllowed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Decorative reply feedback follows both the system preference and the visible activity. */
@Composable
internal fun rememberReplyForeground(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

@Composable
internal fun rememberReplyMotionAllowed(): Boolean {
    val motion = rememberMotionAllowed()
    val foreground = rememberReplyForeground()
    return motion && foreground
}

/**
 * Blinking dot shown after a reply while it streams (ChatGPT-style). The blink runs in the draw phase
 * (graphicsLayer alpha), so it never recomposes or re-parses the markdown above it. Hidden from TalkBack.
 */
@Composable
fun StreamingCursor(modifier: Modifier = Modifier) {
    val alpha = if (rememberReplyMotionAllowed()) rememberInfiniteTransition(label = "streaming-cursor").animateFloat(1f, 0.15f,
        infiniteRepeatable(tween(ChatSizes.streamingCursorBlinkMs, easing = LinearEasing), RepeatMode.Reverse), label = "cursor-alpha") else null
    Box(modifier.padding(top = Spacing.xs).size(ChatSizes.streamingCursor).graphicsLayer { this.alpha = alpha?.value ?: 1f }
        .background(MaterialTheme.colorScheme.onSurface, CircleShape).clearAndSetSemantics { })
}

/** Fades content in once when it first appears (the first streamed words); later deltas are not re-animated. */
@Composable
fun FadeInOnce(content: @Composable () -> Unit) {
    val motion = rememberReplyMotionAllowed()
    val alpha = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) alpha.animateTo(1f, tween(ChatSizes.streamingFadeInMs)) else alpha.snapTo(1f)
    }
    Box(Modifier.fillMaxWidth().graphicsLayer { this.alpha = if (motion) alpha.value else 1f }) { content() }
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
 * Waiting for the first token: the approved orb and existing Thinking status.
 * Replaced by the streaming text as soon as it arrives.
 */
@Composable
fun ReplyPlaceholder(modifier: Modifier = Modifier) {
    TypingIndicator(modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).testTag("reply-placeholder"))
}
