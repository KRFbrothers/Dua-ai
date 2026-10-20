package com.dua.ai.ui.chat

// Swipe-to-reply, reply quote and new-bubble motion. Behaviour (thresholds, feel, curve) is modelled on
// Telegram for Android (https://github.com/DrKLO/Telegram). No Telegram source code is copied; this is a
// Compose reimplementation. See THIRD_PARTY_NOTICES.md.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Pure swipe-to-reply maths so thresholds can be unit tested without a device. Values in px. */
internal class SwipeReplyState(private val thresholdPx: Float, private val maxPx: Float = thresholdPx * 2f) {
    var offset = 0f; private set
    /** True once the drag has crossed [thresholdPx]; flips back if the finger returns below it. */
    var armed = false; private set

    /** Apply a horizontal drag delta. Returns true exactly when the threshold is first crossed (fire the haptic). */
    fun drag(delta: Float): Boolean {
        // Linear up to the threshold, then rubber-band resistance up to maxPx (Telegram-like feel).
        val raw = (rawOffset + delta).coerceAtLeast(0f)
        rawOffset = raw
        offset = if (raw <= thresholdPx) raw else (thresholdPx + (raw - thresholdPx) * RESISTANCE).coerceAtMost(maxPx)
        val wasArmed = armed
        armed = offset >= thresholdPx
        return armed && !wasArmed
    }

    /** Finger lifted: returns true if a reply should start. Resets the state. */
    fun release(): Boolean { val fire = armed; reset(); return fire }
    fun reset() { rawOffset = 0f; offset = 0f; armed = false }
    /** 0..1 progress for the reply arrow's scale/alpha. */
    val progress: Float get() = if (thresholdPx <= 0f) 0f else (offset / thresholdPx).coerceIn(0f, 1f)

    private var rawOffset = 0f
    companion object { const val RESISTANCE = 0.35f }
}

/** Quoted context for the next message. */
data class ReplyQuote(val messageId: String, val text: String, val fromUser: Boolean) {
    companion object {
        const val MAX_QUOTE = 600
        fun of(id: String, text: String, fromUser: Boolean) = ReplyQuote(id, splitFileNames(text).first.trim(), fromUser)

        /** Markdown blockquote prefix sent to the model, followed by the user's text. */
        fun compose(quote: ReplyQuote?, text: String): String {
            val q = quote?.text?.trim().orEmpty()
            if (q.isEmpty()) return text
            val clipped = if (q.length > MAX_QUOTE) q.take(MAX_QUOTE).trimEnd() + "…" else q
            return clipped.lines().joinToString("\n") { "> $it" } + "\n\n" + text
        }

        /** Inverse of [compose] for display: (quote, body). Only a leading blockquote counts. */
        fun split(text: String): Pair<String?, String> {
            if (!text.startsWith("> ")) return null to text
            val lines = text.lines()
            val quoteLines = lines.takeWhile { it.startsWith("> ") || it == ">" }
            val rest = lines.drop(quoteLines.size)
            if (rest.firstOrNull() != "") return null to text
            return quoteLines.joinToString("\n") { it.removePrefix(">").removePrefix(" ") } to rest.drop(1).joinToString("\n")
        }
    }
}

/** Telegram-style list item curve (fast start, soft landing). */
internal val BubbleAppearEasing = CubicBezierEasing(0.2f, 0.01f, 0.28f, 0.91f)
internal const val BubbleAppearMillis = 250

/**
 * Drag the bubble to the right to reply. Only rightward drags are claimed, so left swipes and
 * vertical scrolling keep working; children that consume the drag (code blocks) win.
 */
@Composable
internal fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, haptic: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    if (!enabled) { Box(modifier) { content() }; return }
    val density = LocalDensity.current
    val threshold = with(density) { SwipeReplyThreshold.toPx() }
    val logic = remember(threshold) { SwipeReplyState(threshold) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reply by rememberUpdatedState(onReply)
    val tick by rememberUpdatedState(haptic)
    Box(modifier
        .semantics { customActions = listOf(CustomAccessibilityAction("Reply") { reply(); true }) }
        .pointerInput(threshold) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var accepted = false
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over > 0f) { accepted = true; change.consume(); if (logic.drag(over)) tick() }
                }
                if (drag == null || !accepted) return@awaitEachGesture
                scope.launch { offset.snapTo(logic.offset) }
                horizontalDrag(drag.id) { change ->
                    if (logic.drag(change.positionChange().x)) tick()
                    change.consume()
                    scope.launch { offset.snapTo(logic.offset) }
                }
                val fire = logic.release()
                scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
                if (fire) reply()
            }
        }) {
        SwipeReplyArrow(offset.value / threshold, Modifier.align(Alignment.CenterStart))
        Box(Modifier.graphicsLayer { translationX = offset.value }) { content() }
    }
}

private val SwipeReplyThreshold = 56.dp

/** Reply arrow revealed on the left while swiping. */
@Composable
internal fun SwipeReplyArrow(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    Box(modifier.size(Sizes.touchTarget).graphicsLayer {
        val p = progress.coerceIn(0f, 1f); alpha = p; scaleX = 0.5f + 0.5f * p; scaleY = scaleX
    }.background(ChatTheme.colors.chipFill, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = ChatTheme.colors.chipIcon, modifier = Modifier.size(Sizes.iconTiny))
    }
}

/** Quoted message above the composer, with cancel. */
@Composable
fun ReplyPreview(quote: ReplyQuote, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = ChatTheme.colors
    Row(modifier.fillMaxWidth().padding(bottom = Spacing.xs).testTag("reply-preview"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = c.accent, modifier = Modifier.padding(horizontal = Spacing.sm).size(Sizes.iconTiny))
        Box(Modifier.width(3.dp).height(36.dp).background(c.accent, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(if (quote.fromUser) "Replying to you" else "Replying to Dua", style = MaterialTheme.typography.labelMedium, color = c.accent)
            Text(quote.text, style = MaterialTheme.typography.bodySmall, color = c.mutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancel, modifier = Modifier.testTag("reply-cancel")) { Icon(Icons.Outlined.Close, "Cancel reply", tint = c.mutedText) }
    }
}

/** Quote strip shown inside a sent user bubble. */
@Composable
internal fun BubbleQuote(text: String) {
    val c = ChatTheme.colors
    Row(Modifier.padding(bottom = Spacing.xs)) {
        Box(Modifier.width(3.dp).height(32.dp).background(c.accent, RoundedCornerShape(2.dp)))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.onUserBubble.copy(alpha = 0.7f), maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Spacing.sm))
    }
}

/** Slide up + fade for bubbles that arrive after the list is shown (not for history on first load). */
@Composable
internal fun Modifier.bubbleAppear(animate: Boolean): Modifier {
    if (!animate) return this
    val density = LocalDensity.current
    val shift = with(density) { 24.dp.toPx() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(BubbleAppearMillis, easing = BubbleAppearEasing)) }
    return graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * shift }
}
