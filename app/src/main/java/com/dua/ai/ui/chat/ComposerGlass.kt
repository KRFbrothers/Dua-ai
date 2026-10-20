/*
 * Copyright 2020 The Android Open Source Project (Jetchat focus pop)
 * Copyright 2025 The Android Open Source Project (Androidify animating gradient, reused from AgentEdgeGlow.kt)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from Jetchat app/src/main/java/com/example/compose/jetchat/conversation/UserInput.kt
 * (https://github.com/android/compose-samples, fork commit 0bc770f): the focus "pop" (Animatable scale 1 -> 1.03
 * over 120 ms FastOutSlowIn, then a medium-bouncy spring back to 1) and the translucent rounded input card.
 * The background uses Androidify's AnimatingGradientBrush via Brush.infinitelyAnimatingLinearGradient
 * (ui/components/AgentEdgeGlow.kt, https://github.com/android/androidify, commit a4be10fe).
 * Changes (Dua, GPL-3.0-or-later as part of Dua): smaller pop (1.02), Dua colours, no mesh glow, the gradient is
 * faint and only animates while the screen is resumed, animations are on and battery saver is off.
 * See THIRD_PARTY_NOTICES.md.
 */
package com.dua.ai.ui.chat

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.dua.ai.ui.components.infinitelyAnimatingLinearGradient
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.rememberMotionAllowed

object ComposerGlass {
    /** Blue, purple, pink, orange. Kept faint behind the glass so text contrast stays with the bar colours. */
    val AuroraColors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF97316))
    const val AURORA_ALPHA = 0.22f
    const val AURORA_CYCLE_MILLIS = 9000
    /** Glass fill: the bar colour, partly see-through. */
    const val GLASS_ALPHA = 0.82f
    const val FOCUS_POP = 1.02f
}

/** Jetchat: brief scale pop when the field gains focus, spring back when it settles. Instant when motion is off. */
@Composable
internal fun Modifier.composerFocusPop(focused: Boolean, enabled: Boolean): Modifier {
    if (!enabled) return this
    val motion = rememberMotionAllowed()
    val focusScale = remember { Animatable(1f) }
    LaunchedEffect(focused, motion) {
        if (!motion) { focusScale.snapTo(1f); return@LaunchedEffect }
        if (focused) {
            focusScale.animateTo(ComposerGlass.FOCUS_POP, tween(durationMillis = 120, easing = FastOutSlowInEasing))
            focusScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        } else if (focusScale.value != 1f) {
            focusScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
        }
    }
    return graphicsLayer { scaleX = focusScale.value; scaleY = focusScale.value }
}

/** Dua glue: true while the gradient may animate (resumed, animations on, battery saver off). */
@Composable
internal fun rememberAuroraAnimating(): Boolean {
    val motion = rememberMotionAllowed()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val context = LocalContext.current
    val saver = remember(context, lifecycle) {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }
    return motion && !saver && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
}

/** Dua glue: faint blue-purple-pink-orange gradient behind the composer (Androidify brush); static when paused. */
@Composable
internal fun Modifier.composerAurora(enabled: Boolean): Modifier {
    if (!enabled) return this
    val brush = Brush.infinitelyAnimatingLinearGradient(ComposerGlass.AuroraColors,
        tween(ComposerGlass.AURORA_CYCLE_MILLIS, easing = LinearEasing), animate = rememberAuroraAnimating())
    return background(brush, alpha = ComposerGlass.AURORA_ALPHA)
}

/** Dua glue: one-tap prompt chips above the composer; tapping fills the draft (nothing is sent). */
@Composable
internal fun ComposerSuggestionChips(suggestions: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (suggestions.isEmpty()) return
    val c = HomeTheme.colors
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md).testTag("composer-suggestions"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        suggestions.forEach { text ->
            SuggestionChip(onClick = { onPick(text) }, label = { Text(text, maxLines = 1) },
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = c.bar.copy(alpha = ComposerGlass.GLASS_ALPHA), labelColor = c.input),
                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = c.barBorder))
        }
    }
}

/** Default follow-up chips for an open conversation. */
internal val ConversationSuggestions = listOf("Explain more simply", "Give an example", "Summarize this", "What should I do next?")
