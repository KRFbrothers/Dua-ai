/*
 * Adapted from Androidify's ScaleIndication.kt (https://github.com/android/androidify),
 * Copyright 2025 The Android Open Source Project, licensed under the Apache License, Version 2.0.
 * See THIRD_PARTY_NOTICES.md. Changes: shrinks slightly instead of growing, keeps the Material
 * ripple (it is added next to it), and does nothing when the system turns animations off.
 */
package com.dua.ai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.rememberMotionAllowed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** How far a pressed control shrinks (4%). */
const val PressedScale = 0.96f

/** Scales content in the draw phase while pressed; layout, touch area and semantics stay the same. */
private class PressScaleNode(
    private val interactions: InteractionSource,
    private val pressedScale: Float,
    private val spec: AnimationSpec<Float>,
) : Modifier.Node(), DrawModifierNode {
    private val scale = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            interactions.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> scale.animateTo(pressedScale, spec)
                    is PressInteraction.Release, is PressInteraction.Cancel -> scale.animateTo(1f, spec)
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val value = scale.value
        if (value == 1f) drawContent() else scale(value) { this@draw.drawContent() }
    }
}

/** No-op node used when animations are off: pressing gives the ripple only. */
private class StillNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() = drawContent()
}

/** Indication that shrinks a pressed control a little. Use with [pressScale] next to the normal ripple. */
@Stable
class PressScaleIndication(val pressedScale: Float = PressedScale, val motion: Boolean = true) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        if (motion) PressScaleNode(interactionSource, pressedScale, DuaMotion.fast()) else StillNode()

    override fun equals(other: Any?) = other is PressScaleIndication && other.pressedScale == pressedScale && other.motion == motion
    override fun hashCode() = 31 * pressedScale.hashCode() + motion.hashCode()
}

/**
 * Shrinks this control slightly while [interactionSource] is pressed. Pass the same source to the
 * control's clickable/toggleable. Follows the system "Remove animations" setting.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = PressedScale): Modifier {
    val motion = rememberMotionAllowed()
    val indication = remember(pressedScale, motion) { PressScaleIndication(pressedScale, motion) }
    return indication(interactionSource, indication)
}
