package com.dua.ai.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.delay

/** "Thinking… 7s" while streaming, "Thought for 12s" afterwards. */
internal fun thinkingLabel(live: Boolean, seconds: Int?): String = when {
    live -> if (seconds != null && seconds > 0) "Thinking… ${seconds}s" else "Thinking…"
    seconds != null && seconds > 0 -> "Thought for ${seconds}s"
    else -> "Thoughts"
}

/** Reasoning summaries are markdown-ish; show them as calm plain text. */
internal fun plainThinking(text: String): String = text.replace("**", "").replace(Regex("(?m)^#{1,6}\\s*"), "").trim()

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
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(initiallyExpanded || (live && !answering)) }
    // Follow the stream: open while thinking, fold away when the answer begins or the reply ends.
    LaunchedEffect(live, answering) { if (!live || answering) { if (!initiallyExpanded) expanded = false } else expanded = true }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = live && !answering
    LaunchedEffect(ticking) { while (ticking) { now = System.currentTimeMillis(); delay(1000) } }
    val elapsed = if (live && seconds == null && startedAt > 0) ((now - startedAt) / 1000).toInt() else seconds
    val label = thinkingLabel(live && !answering, elapsed)
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val body = remember(thinking) { thinking?.let(::plainThinking).orEmpty() }
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
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(Sizes.iconTiny))
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.sm))
                if (body.isNotEmpty()) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.xs).size(Sizes.iconSmall).rotate(rotation))
            }
        }
        AnimatedVisibility(visible = expanded && body.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = Spacing.xs, bottom = Spacing.sm)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(scheme.outlineVariant))
                Text(
                    // While streaming, show the latest lines; the full summary is there once it settles.
                    if (live && !answering) body.takeLast(LIVE_TAIL_CHARS) else body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = if (live && !answering) 6 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.md),
                )
            }
        }
    }
}

private const val LIVE_TAIL_CHARS = 700
