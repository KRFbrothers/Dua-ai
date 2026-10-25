package com.dua.ai.ui.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * An upward drag of at least [threshold] that starts in this area calls [onSwipeUp] (used to open the + sheet
 * from the input card and the chips just above it). It steps aside for everything else: drags that start where
 * [isExcluded] says (the text field while typing), anything a child already consumed (text selection, scrolling
 * a long draft), sideways drags (the chip row scrolls), downward drags and multi-touch.
 */
internal fun Modifier.swipeUpToOpen(
    enabled: Boolean,
    isExcluded: (Offset) -> Boolean,
    onSwipeUp: () -> Unit,
    threshold: Dp = 40.dp,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    val thresholdPx = threshold.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (isExcluded(down.position)) return@awaitEachGesture
        var dx = 0f
        var dy = 0f
        while (true) {
            // Final pass: children (the text field, scrollers) have had their turn and may have consumed the move.
            val event = awaitPointerEvent(PointerEventPass.Final)
            if (event.changes.size > 1) return@awaitEachGesture
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) return@awaitEachGesture
            // The down itself may be consumed by a button or chip (tap detection); only a consumed move means a child took the drag.
            if (!change.previousPressed) continue
            if (change.isConsumed) return@awaitEachGesture
            val delta = change.positionChange()
            dx += delta.x
            dy += delta.y
            if (abs(dx) > slop && abs(dx) > abs(dy)) return@awaitEachGesture
            if (dy > slop) return@awaitEachGesture
            if (-dy >= thresholdPx) {
                change.consume()
                onSwipeUp()
                return@awaitEachGesture
            }
        }
    }
}
