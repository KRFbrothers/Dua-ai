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
 * Changes (Dua, GPL-3.0-or-later as part of Dua): smaller pop (1.02), Dua colours, opaque elevated backing
 * to prevent shadow/layer bands, no mesh glow. The faint tint only animates while the screen is resumed,
 * animations are on and battery saver is off.
 * See THIRD_PARTY_NOTICES.md.
 */
package com.dua.ai.ui.chat

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Immutable
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
import com.dua.ai.ui.theme.DuaFixedColors
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.HomeTheme
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.rememberMotionAllowed

object ComposerGlass {
    /** Blue, purple, pink, orange. Kept faint behind the glass so text contrast stays with the bar colours. */
    val AuroraColors = listOf(DuaFixedColors.AuroraBlue, DuaFixedColors.AuroraViolet, DuaFixedColors.AuroraPink, DuaFixedColors.AuroraOrange)
    const val AURORA_ALPHA = 0.22f
    const val AURORA_CYCLE_MILLIS = 9000
    /** Translucent chip fill; also retains the original effective aurora tint strength. */
    const val GLASS_ALPHA = 0.82f
    const val FOCUS_POP = 1.02f
    /** Larger, raised glass input: more padding, deeper soft shadow, rounder corners. */
    val Elevation = 6.dp
    val PaddingV = 10.dp
    val MarginH = 20.dp
    val Shape = DuaShape.composer
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
            focusScale.animateTo(ComposerGlass.FOCUS_POP, DuaMotion.fast())
            focusScale.animateTo(1f, DuaMotion.gentle())
        } else if (focusScale.value != 1f) {
            focusScale.animateTo(1f, DuaMotion.fast())
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
    // Previously a 22% aurora was behind an 82% bar: only 3.96% of that tint showed.
    // Preserve that tint over an opaque backing so the shadow cannot show through the input.
    return background(brush, alpha = ComposerGlass.AURORA_ALPHA * (1f - ComposerGlass.GLASS_ALPHA))
}

/** One prompt chip: label plus its Material Symbols icon. */
@Immutable
data class ComposerSuggestion(val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Dua glue: soft tint per chip, derived from the current theme palette (never new brand colours). */
@Composable
private fun suggestionTint(index: Int): Color {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val base = HomeTheme.colors.bar
    val accent = when (index % 4) { 0 -> scheme.primary; 1 -> scheme.secondary; 2 -> scheme.tertiary; else -> HomeTheme.colors.voice }
    return androidx.compose.ui.graphics.lerp(base, accent, 0.10f).copy(alpha = ComposerGlass.GLASS_ALPHA)
}

/** Dua glue: one-tap prompt chips above the composer; tapping fills the draft (nothing is sent). */
@Composable
internal fun ComposerSuggestionChips(suggestions: List<ComposerSuggestion>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (suggestions.isEmpty()) return
    val c = HomeTheme.colors
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.sm, vertical = Spacing.xs).testTag("composer-suggestions"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        suggestions.forEachIndexed { index, item ->
            SuggestionChip(onClick = { onPick(item.text) }, label = { Text(item.text, maxLines = 1) },
                icon = { androidx.compose.material3.Icon(item.icon, null, tint = c.barIcon, modifier = Modifier.size(SuggestionChipDefaults.IconSize)) },
                modifier = Modifier.heightIn(min = Sizes.touchTarget),
                shape = DuaShape.card,
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = suggestionTint(index), labelColor = c.input),
                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = c.barBorder))
        }
    }
}

/** Default follow-up chips for an open conversation. */
internal val ConversationSuggestions = listOf(
    ComposerSuggestion("Explain more simply", androidx.compose.material.icons.Icons.Outlined.Lightbulb),
    ComposerSuggestion("Give an example", androidx.compose.material.icons.Icons.Outlined.FormatListBulleted),
    ComposerSuggestion("Summarize this", androidx.compose.material.icons.Icons.Outlined.ShortText),
    ComposerSuggestion("What should I do next?", androidx.compose.material.icons.Icons.Outlined.Explore),
)
