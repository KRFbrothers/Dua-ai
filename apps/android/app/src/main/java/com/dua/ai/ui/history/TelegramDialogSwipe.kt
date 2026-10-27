/*
 * Dua AI - Copyright (C) 2026 KRFbrothers
 * Adapted from Telegram TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java (SwipeController), ui/Cells/DialogCell.java (setTranslationX, swipe background, reveal circle, BounceInterpolator) and ui/Components/UndoView.java (5 s undo countdown) (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756).
 * Original: Telegram for Android, Copyright Nikolai Kudashov, 2013-2018, https://github.com/DrKLO/Telegram
 * (commit 68c6775ac373f4bdb30ae9986282dd5ec0407756), licensed under GNU GPL v. 2 or later.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. This program is distributed WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details
 * (LICENSE in the repository root, or <https://www.gnu.org/licenses/>).
 */
package com.dua.ai.ui.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/** DialogsActivity.SwipeController / DialogCell numbers. */
internal object TelegramDialogSwipe {
    /** getSwipeThreshold(): 0.45f of the row width; DialogCell draws the reveal past the same point. */
    const val SWIPE_THRESHOLD = 0.45f
    /** getAnimationDuration(ANIMATION_TYPE_SWIPE_CANCEL) */
    const val CANCEL_MILLIS = 200
    /** DialogCell: currentRevealProgress += 16f / 300f per frame; currentRevealBounceProgress += 16f / 170f. */
    const val REVEAL_MILLIS = 300
    const val BOUNCE_MILLIS = 170
    /** UndoView: timeLeft = 5000 */
    const val UNDO_MILLIS = 5000L

    /** DialogCell: drawRevealBackground = Math.abs(translationX) >= getMeasuredWidth() * 0.45f */
    fun revealed(translationX: Float, width: Float) = width > 0f && abs(translationX) >= width * SWIPE_THRESHOLD

    /** getSwipeVelocityThreshold() is Float.MAX_VALUE: only the distance decides, flings never complete a swipe. */
    fun completes(translationX: Float, width: Float) = revealed(translationX, width)

    /** DialogCell.BounceInterpolator (icon scale = 1 + value). */
    val Bounce = Easing { t0 ->
        var t = t0
        if (t < 0.33f) 0.1f * (t / 0.33f) else {
            t -= 0.33f
            if (t < 0.33f) 0.1f - 0.15f * (t / 0.34f) else { t -= 0.34f; -0.05f + 0.05f * (t / 0.33f) }
        }
    }
    /** android.view.animation.AccelerateInterpolator (factor 1), used for the reveal circle radius. */
    val Accelerate = Easing { t -> t * t }
}

/** A swipe action shown behind a row. */
internal class DialogSwipeAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val background: Color, val reveal: Color, val content: Color, val slideOut: Boolean, val onSwiped: () -> Unit)

/**
 * Telegram chat-row swipe: drag the row; past 45 % of the width the reveal circle grows from the icon and the
 * phone ticks (again if you go back); on release past the threshold the action runs, otherwise the row returns
 * in 200 ms. Telegram only swipes left; Dua adds the mirrored right swipe for Pin.
 */
@Composable
internal fun DialogSwipeRow(start: DialogSwipeAction?, end: DialogSwipeAction?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val offset = remember { Animatable(0f) }
        val reveal = remember { Animatable(0f) }
        val bounce = remember { Animatable(0f) }
        var revealed by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val currentStart by rememberUpdatedState(start)
        val currentEnd by rememberUpdatedState(end)
        LaunchedEffect(revealed) {
            if (revealed) {
                launch { bounce.snapTo(0f); bounce.animateTo(1f, tween(TelegramDialogSwipe.BOUNCE_MILLIS, easing = LinearEasing)); bounce.snapTo(0f) }
                reveal.animateTo(1f, tween(((1f - reveal.value) * TelegramDialogSwipe.REVEAL_MILLIS).toInt(), easing = LinearEasing))
            } else reveal.animateTo(0f, tween((reveal.value * TelegramDialogSwipe.REVEAL_MILLIS).toInt(), easing = LinearEasing))
        }
        val x = offset.value
        val action = if (x < 0) end else if (x > 0) start else null
        if (action != null) SwipeBackground(action, x, width, reveal.value, bounce.value, Modifier.matchParentSize())
        Box(Modifier.graphicsLayer { translationX = offset.value }
            .semantics {
                customActions = listOfNotNull(start, end).map { a -> CustomAccessibilityAction(a.label) { a.onSwiped(); true } }
            }
            .draggable(rememberDraggableState { delta ->
                val min = if (currentEnd != null) -width else 0f
                val max = if (currentStart != null) width else 0f
                val next = (offset.value + delta).coerceIn(min, max)
                scope.launch { offset.snapTo(next) }
                val now = TelegramDialogSwipe.revealed(next, width)
                if (now != revealed) { revealed = now; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }, Orientation.Horizontal, onDragStopped = {
                val value = offset.value
                val a = if (value < 0) currentEnd else currentStart
                revealed = false
                if (a != null && TelegramDialogSwipe.completes(value, width)) {
                    if (a.slideOut) { offset.animateTo(if (value < 0) -width else width, tween(TelegramDialogSwipe.CANCEL_MILLIS)); a.onSwiped(); offset.snapTo(0f) }
                    else { a.onSwiped(); offset.animateTo(0f, tween(TelegramDialogSwipe.CANCEL_MILLIS)) }
                } else offset.animateTo(0f, tween(TelegramDialogSwipe.CANCEL_MILLIS))
            })) { content() }
    }
}

/** DialogCell.onDraw swipe part: background rect, reveal circle from the icon centre, bouncing icon, label. */
@Composable
private fun SwipeBackground(action: DialogSwipeAction, translationX: Float, width: Float, reveal: Float, bounce: Float, modifier: Modifier) {
    val density = LocalDensity.current
    val iconInset = with(density) { 43.dp.toPx() } // drawableX = getMeasuredWidth() - dp(43) - w / 2
    val left = translationX < 0
    Box(modifier.background(action.background).testTag("swipe-bg-${action.label.lowercase()}")) {
        Canvas(Modifier.matchParentSize()) {
            if (reveal > 0f) {
                val cx = if (left) size.width - iconInset else iconInset
                val cy = size.height / 2f
                val far = if (left) cx else size.width - cx
                val rad = sqrt(far * far + cy * cy) // radius to the far corner, as in DialogCell
                drawCircle(action.reveal, rad * TelegramDialogSwipe.Accelerate.transform(reveal), Offset(cx, cy))
            }
        }
        Column(Modifier.align(if (left) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 19.dp)
            .graphicsLayer { val s = 1f + if (bounce != 0f && bounce != 1f) TelegramDialogSwipe.Bounce.transform(bounce) else 0f; scaleX = s; scaleY = s },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(action.icon, null, tint = action.content, modifier = Modifier.size(20.dp))
            Text(action.label, style = MaterialTheme.typography.labelSmall, color = action.content)
        }
    }
}

/** UndoView (ACTION_DELETE): "Chat deleted" with Undo and the 5 s countdown ring; [onTimeout] commits. */
@Composable
internal fun ChatUndoBar(message: String, key: Any, onUndo: () -> Unit, onTimeout: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember(key) { Animatable(1f) }
    val timeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(key) { progress.animateTo(0f, tween(TelegramDialogSwipe.UNDO_MILLIS.toInt(), easing = LinearEasing)); timeout() }
    Surface(modifier.fillMaxWidth().testTag("chat-undo"), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { progress.value }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.inverseOnSurface)
                Text("${kotlin.math.ceil(progress.value * TelegramDialogSwipe.UNDO_MILLIS / 1000f).toInt().coerceAtLeast(1)}",
                    style = MaterialTheme.typography.labelSmall)
            }
            Text(message, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onUndo, modifier = Modifier.testTag("chat-undo-button")) {
                Text("Undo", color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

internal val SwipePinIcon = Icons.Outlined.PushPin
internal val SwipeDeleteIcon = Icons.Outlined.Delete
