package com.dua.ai.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dua.ai.model.AgentStep
import com.dua.ai.ui.theme.AgentSizes
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.DuaMotion

/**
 * Agent live progress: finished steps as small check chips, the current step as a status line with a spinner and a
 * gentle pulse. Fed by structured "progress" events; the raw details stay in the Thinking block.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentProgressView(steps: List<AgentStep>, modifier: Modifier = Modifier) {
    val c = ChatTheme.colors
    val motion = rememberReplyMotionAllowed()
    val active = steps.lastOrNull { !it.done }
    val done = steps.filter { it.done }
    Column(modifier.fillMaxWidth().animateContentSize(animationSpec = if (motion) DuaMotion.gentle() else snap()).testTag("agent-progress")) {
        if (steps.size > 1) AgentStepTracker(steps, Modifier.padding(bottom = Spacing.xs))
        if (done.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(bottom = Spacing.xs)) {
            done.forEach { step ->
                Surface(shape = DuaShape.chip, color = c.chipFill, border = BorderStroke(ChatSizes.border, c.chipBorder),
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Done: ${step.label.trimEnd('…', '.')}" }) {
                    Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = c.chipIcon, modifier = Modifier.size(AgentSizes.stepIcon))
                        Text(step.label.trimEnd('…', '.'), style = MaterialTheme.typography.labelMedium, color = c.chipText,
                            modifier = Modifier.padding(start = Spacing.xs), maxLines = 1)
                    }
                }
            }
        }
        AnimatedContent(active, transitionSpec = {
            fadeIn(tween(if (motion) DuaMotion.DurationNormal else 0)) togetherWith
                fadeOut(tween(if (motion) DuaMotion.DurationNormal else 0))
        }, label = "agent-step") { step ->
            if (step != null) {
                val pulse = com.dua.ai.ui.components.rememberShimmerAlpha(enabled = motion)
                Row(Modifier.heightIn(min = Sizes.touchTarget).semantics { contentDescription = step.label; liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically) {
                    if (motion) TelegramRadialProgress(c.chipIcon, Modifier.size(AgentSizes.stepIcon), strokeWidth = AgentSizes.stepStroke)
                    else Icon(Icons.Outlined.Schedule, null, tint = c.chipIcon, modifier = Modifier.size(AgentSizes.stepIcon))
                    Text(step.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm).graphicsLayer { alpha = pulse() }.clearAndSetSemantics { })
                }
            }
        }
    }
}

/** Step n of m, counting the active step; null when there is nothing to track. */
internal fun agentStepPosition(steps: List<AgentStep>): Pair<Int, Int>? {
    if (steps.isEmpty()) return null
    val done = steps.count { it.done }
    return (if (done < steps.size) done + 1 else steps.size) to steps.size
}

/** Dua glue: segmented step tracker (done = filled, active = half, pending = track). */
@Composable
internal fun AgentStepTracker(steps: List<AgentStep>, modifier: Modifier = Modifier) {
    val c = ChatTheme.colors
    val (n, m) = agentStepPosition(steps) ?: return
    val firstActive = steps.indexOfFirst { !it.done }
    Row(modifier.fillMaxWidth().testTag("agent-step-tracker").semantics(mergeDescendants = true) { contentDescription = "Step $n of $m" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, step ->
            val a = when { step.done -> 1f; i == firstActive -> 0.5f; else -> 0.15f }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).height(3.dp)
                .background(c.chipIcon.copy(alpha = a), DuaShape.chip).testTag("agent-step-seg-$i"))
        }
    }
}
