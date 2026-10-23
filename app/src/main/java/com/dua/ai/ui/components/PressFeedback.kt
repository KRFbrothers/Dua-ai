package com.dua.ai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.rememberMotionAllowed

/** Apply to artwork inside a control so its 48 dp layout/touch target never shrinks. */
@Composable
internal fun Modifier.pressFeedback(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = rememberMotionAllowed()
    val scale = animateFloatAsState(if (pressed && motion) .96f else 1f,
        animationSpec = if (motion) DuaMotion.fast() else snap(), label = "Control press")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
