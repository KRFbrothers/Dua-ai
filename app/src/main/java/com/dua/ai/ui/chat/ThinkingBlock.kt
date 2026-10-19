package com.dua.ai.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.rememberMotionAllowed
import com.dua.ai.ui.theme.DuaMotion
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** A timer for the server-provided summary, not a claim about hidden model reasoning. */
internal fun thinkingLabel(live: Boolean, seconds: Int?): String = when {
    live -> "Thinking for ${(seconds ?: 0).coerceAtLeast(0)}s"
    seconds != null && seconds >= 0 -> "Thought for ${seconds}s"
    else -> "Thoughts"
}
/** Reasoning summaries are markdown-ish; show them as calm plain text. */
internal fun plainThinking(text: String): String = text.replace("**", "").replace(Regex("(?m)^#{1,6}\\s*"), "").trim()

internal data class ReasoningStep(val title: String, val lines: String)
private val numberedStep = Regex("^(?:\\d+[.)]\\s+|Step\\s+\\d+\\s*[-:.–—]\\s*)(.+)$", RegexOption.IGNORE_CASE)

/** Group only explicit headings/numbered steps; unstructured summaries remain one real node. */
internal fun reasoningSteps(text: String): List<ReasoningStep> {
    val steps = mutableListOf<ReasoningStep>()
    var title: String? = null
    val lines = mutableListOf<String>()
    fun finish() { title?.let { steps += ReasoningStep(it, lines.joinToString("\n").trim()) }; lines.clear() }
    text.trim().lines().forEach { raw ->
        val clean = plainThinking(raw)
        val numbered = numberedStep.matchEntire(clean)?.groupValues?.get(1)
        val heading = raw.trimStart().startsWith("#") || (raw.trim().startsWith("**") && raw.trim().endsWith("**"))
        when {
            clean.isBlank() && title == null -> Unit
            numbered != null || heading -> { finish(); title = numbered ?: clean }
            title == null -> title = clean
            else -> lines += raw
        }
    }
    finish()
    return steps
}

/**
 * Collapsible reasoning summary shown above an answer. Expanded and live while the model is
 * thinking; collapses once the answer starts (tap the header to reopen). [initiallyExpanded] is
 * for previews and screenshots.
 */
@Composable
fun ThinkingBlock(
    messageId: String,
    thinking: String?,
    seconds: Int?,
    live: Boolean,
    answering: Boolean,
    startedAt: Long,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    /** False when another view (Agent progress) shows the live state: stay collapsed unless the user opens it. */
    followStream: Boolean = true,
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(initiallyExpanded || (followStream && live && !answering)) }
    // Follow the stream: open while thinking, fold away when the answer begins or the reply ends.
    LaunchedEffect(live, answering, followStream) {
        if (!live || answering) { if (!initiallyExpanded) expanded = false } else if (followStream) expanded = true
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val foreground = rememberReplyForeground()
    val motion = rememberMotionAllowed() && foreground
    val ticking = live && !answering
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(ticking, lifecycle) {
        if (ticking) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { now = System.currentTimeMillis(); delay(1000) }
        }
    }
    val elapsed = if (live && seconds == null && startedAt > 0) ((now - startedAt) / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() else seconds
    val label = thinkingLabel(live && !answering, elapsed)
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (expanded) 180f else 0f,
        animationSpec = if (motion) DuaMotion.gentle() else snap(), label = "chevron")
    val body = remember(thinking) { thinking?.let(::plainThinking).orEmpty() }
    val steps = remember(thinking) { reasoningSteps(thinking.orEmpty()) }
    val animate = ticking && motion
    val pulse = if (animate) rememberInfiniteTransition(label = "thinking-badge").animateFloat(
        .55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "thinking-shimmer") else null
    Column(modifier.fillMaxWidth()) {
        Surface(
            onClick = { if (body.isNotEmpty()) expanded = !expanded },
            enabled = body.isNotEmpty(),
            shape = DuaShape.chip,
            color = scheme.surface.copy(alpha = 0f),
            contentColor = scheme.onSurfaceVariant,
            modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics {
                role = Role.Button
                contentDescription = label
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                if (live) liveRegion = LiveRegionMode.Polite
            },
        ) {
            Row(Modifier.padding(end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                // Animate only the decorative glyph so the timer text retains its theme contrast.
                Icon(Icons.Outlined.Psychology, contentDescription = null,
                    modifier = Modifier.size(Sizes.iconTiny).graphicsLayer { alpha = pulse?.value ?: 1f })
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.sm))
                if (body.isNotEmpty()) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.xs).size(Sizes.iconSmall).rotate(rotation))
            }
        }
        AnimatedVisibility(visible = expanded && body.isNotEmpty(),
            enter = if (motion) fadeIn() + expandVertically(expandFrom = Alignment.Top) else EnterTransition.None,
            exit = if (motion) fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top) else ExitTransition.None) {
            Column(Modifier.fillMaxWidth().padding(bottom = Spacing.sm).testTag("reasoning-step-tree")) {
                steps.forEachIndexed { index, step ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Column(Modifier.width(Sizes.touchTarget).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = CircleShape, color = scheme.secondaryContainer) {
                                Box(Modifier.size(Sizes.icon), contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer)
                                }
                            }
                            if (index < steps.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(scheme.outlineVariant))
                        }
                        Column(Modifier.weight(1f).padding(bottom = Spacing.md)) {
                            Text(step.title, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                            if (step.lines.isNotBlank()) Text(step.lines, style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                        }
                    }
                }
            }
        }
    }
}
