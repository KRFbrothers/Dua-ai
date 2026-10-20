/*
 * Adapted from the PredictiveBackHandler snippet in android/snippets
 * (compose/snippets/.../predictiveback/PredictiveBackSnippets.kt, read through the fork
 * https://github.com/KRFbrothers/snippets), Copyright 2024 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md. Changes: a reusable
 * progress state plus a subtle scale/fade modifier, no motion when the system turns animations off.
 */
package com.dua.ai.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.dua.ai.ui.theme.rememberMotionAllowed
import kotlinx.coroutines.CancellationException

/** Gesture progress for the smallest visible shrink; 0.92 at a full swipe. */
internal fun predictiveBackScale(progress: Float): Float = 1f - 0.08f * progress.coerceIn(0f, 1f)

/**
 * Handles system back (button or predictive gesture) by calling [onBack] once the gesture commits,
 * and returns the live gesture progress (0..1). Progress stays 0 when animations are turned off.
 */
@Composable
fun rememberPredictiveBack(enabled: Boolean = true, onBack: () -> Unit): State<Float> {
    val progress = remember { mutableFloatStateOf(0f) }
    val motion = rememberMotionAllowed()
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { if (motion) progress.floatValue = it.progress }
            currentOnBack()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            progress.floatValue = 0f
        }
    }
    return progress
}

/** Slight shrink and fade that follows the back gesture (Material predictive back guidance). */
fun Modifier.predictiveBack(progress: State<Float>): Modifier = graphicsLayer {
    val p = progress.value
    val scale = predictiveBackScale(p)
    scaleX = scale; scaleY = scale
    alpha = 1f - 0.2f * p.coerceIn(0f, 1f)
}
