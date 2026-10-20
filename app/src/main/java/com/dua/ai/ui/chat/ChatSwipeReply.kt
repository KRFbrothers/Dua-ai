/*
 * Dua AI - Copyright (C) 2026 KRFbrothers
 * Adapted from Telegram TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java (chat list swipe-to-reply tracking and drawReplyButton) and ui/recyclerview/ChatListItemAnimator.java (animateAddImpl) (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756).
 * Original: Telegram for Android, Copyright Nikolai Kudashov, 2013-2018, https://github.com/DrKLO/Telegram
 * (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756), licensed under GNU GPL v. 2 or later.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details
 * (LICENSE in the repository root, or <https://www.gnu.org/licenses/>).
 */
package com.dua.ai.ui.chat

// Swipe-to-reply tracking, reply button progress and the new-bubble add animation are ported from Telegram
// (see header). Telegram swipes left; Dua mirrors it to a right swipe as the owner asked. ReplyQuote and the
// composer preview are Dua glue.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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

/**
 * Telegram ChatActivity swipe-to-reply tracking (mirrored to the right). Values in px:
 * offset = clamp(dx, 0, dp(80)); vibrate once when |dx| >= dp(50) (re-armed when it drops below); on release
 * reply if the offset is still >= dp(50).
 */
internal class SwipeReplyState(private val thresholdPx: Float, private val maxPx: Float = thresholdPx * 80f / 50f) {
    var offset = 0f; private set
    /** Telegram's wasTrackingVibrate. */
    var armed = false; private set

    /** Apply a horizontal drag delta. Returns true exactly when the threshold is first crossed (fire the haptic). */
    fun drag(delta: Float): Boolean {
        offset = (offset + delta).coerceIn(0f, maxPx)
        if (offset >= thresholdPx) {
            if (!armed) { armed = true; return true }
        } else armed = false
        return false
    }

    /** Finger lifted: returns true if a reply should start. Resets the state. */
    fun release(): Boolean { val fire = offset >= thresholdPx; reset(); return fire }
    fun reset() { offset = 0f; armed = false }
    /** drawReplyButton: clamp((|translationX| - dp(20)) / dp(30), 0, 1). */
    val progress: Float get() = if (thresholdPx <= 0f) 0f else ((offset - thresholdPx * 20f / 50f) / (thresholdPx * 30f / 50f)).coerceIn(0f, 1f)

    companion object {
        /** Telegram starts tracking after 0.4 cm of horizontal travel with |dx| / 3 > |dy|. */
        fun shouldStart(dx: Float, dy: Float, startPx: Float): Boolean = dx >= startPx && dx / 3f > kotlin.math.abs(dy)
    }
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
    val startPx = with(density) { 0.4f * 160f / 2.54f * this.density } // AndroidUtilities.getPixelsInCM(0.4f, true)
    val returnAnim = tween<Float>(180, easing = TelegramDecelerate) // trackAnimationProgress += dt / 180f, decelerate
    Box(modifier
        .semantics { customActions = listOf(CustomAccessibilityAction("Reply") { reply(); true }) }
        .pointerInput(threshold) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val origin = down.position
                var tracking = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - origin.x
                    val dy = change.position.y - origin.y
                    if (!tracking) {
                        if (change.isConsumed) break // a child (code block scroll, selection) owns it
                        if (SwipeReplyState.shouldStart(dx, dy, startPx)) { tracking = true; logic.reset() }
                        else if (kotlin.math.abs(dy) > viewConfiguration.touchSlop || dx < -viewConfiguration.touchSlop) break
                        else continue
                    }
                    // Telegram re-bases startedTrackingX when tracking begins, then follows the finger.
                    if (logic.drag(change.positionChange().x)) tick()
                    change.consume()
                    scope.launch { offset.snapTo(logic.offset) }
                }
                if (!tracking) return@awaitEachGesture
                val fire = logic.release()
                scope.launch { offset.animateTo(0f, returnAnim) }
                if (fire) reply()
            }
        }) {
        SwipeReplyArrow(((offset.value - threshold * 0.4f) / (threshold * 0.6f)).coerceIn(0f, 1f), Modifier.align(Alignment.CenterStart))
        Box(Modifier.graphicsLayer { translationX = offset.value }) { content() }
    }
}

private val SwipeReplyThreshold = 50.dp // AndroidUtilities.dp(50)

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

/**
 * ChatListItemAnimator.animateAddImpl: a newly added bubble starts translated down by the added height and
 * animates translationY to 0 with DEFAULT_INTERPOLATOR over DEFAULT_DURATION (alpha stays 1, as in Telegram).
 */
@Composable
internal fun Modifier.bubbleAppear(animate: Boolean): Modifier {
    if (!animate) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(TelegramChatAnimator.DEFAULT_DURATION, easing = TelegramChatAnimator.DEFAULT_INTERPOLATOR))
    }
    return graphicsLayer { translationY = (1f - progress.value) * size.height }
}
