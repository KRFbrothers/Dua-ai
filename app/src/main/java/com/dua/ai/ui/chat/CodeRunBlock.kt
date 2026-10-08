package com.dua.ai.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dua.ai.model.CodeRun
import com.dua.ai.ui.theme.CodeTextStyle
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.delay

/** Header text: "Running code…", "Ran code", "Code failed" or "Code timed out" (with "· 2/3" when a reply ran several). */
internal fun codeRunLabel(run: CodeRun, position: Int, total: Int): String {
    val base = when {
        run.running -> "Running code…"
        run.outcome == "timeout" -> "Code timed out"
        run.outcome == "error" -> "Code failed"
        else -> "Ran code"
    }
    return if (total > 1) "$base · ${position + 1}/$total" else base
}

/** Every run of a reply, stacked above the answer. */
@Composable
fun CodeRuns(messageId: String, runs: List<CodeRun>, modifier: Modifier = Modifier, initiallyExpanded: Boolean = false) {
    Column(modifier.fillMaxWidth()) {
        runs.forEachIndexed { index, run ->
            CodeRunBlock(messageId, index, runs.size, run, initiallyExpanded = initiallyExpanded)
        }
    }
}

/**
 * Collapsible "Ran code" block: monospace code with a copy button and, below it, what the code printed.
 * Collapsed by default so the answer stays in front; [initiallyExpanded] is for previews and screenshots.
 */
@Composable
fun CodeRunBlock(messageId: String, index: Int, total: Int, run: CodeRun, modifier: Modifier = Modifier, initiallyExpanded: Boolean = false) {
    val colors = DuaTheme.colors
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(messageId, index) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val label = codeRunLabel(run, index, total)
    val code = remember(run.code, run.language, colors.isDark) { highlightCode(run.code.trimEnd('\n'), run.language, colors.isDark) }
    Surface(shape = DuaShape.codeBlock, color = colors.codeBackground, border = BorderStroke(1.dp, colors.codeBorder),
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Column {
            Surface(onClick = { expanded = !expanded }, color = colors.codeHeader, contentColor = scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).semantics {
                    role = Role.Button
                    contentDescription = "$label, ${run.language}"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }) {
                Row(Modifier.padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        run.running -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(Sizes.iconTiny))
                        run.failed -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(Sizes.iconTiny))
                        else -> Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(Sizes.iconTiny))
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.sm).weight(1f))
                    Text(run.language, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = Spacing.xs).size(Sizes.iconSmall).rotate(rotation))
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    CopyableMono(code, run.code, "Copy code", "Code copied")
                    HorizontalDivider(color = colors.codeBorder)
                    val output = run.output
                    Row(Modifier.fillMaxWidth().background(colors.codeHeader.copy(alpha = .5f)).padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (output == null) "Output · running…" else "Output", style = MaterialTheme.typography.labelMedium,
                            color = if (run.failed) scheme.error else scheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(vertical = Spacing.sm))
                    }
                    when {
                        output == null -> Unit
                        output.isBlank() -> Text(if (run.outcome == "timeout") "The code ran out of time before printing anything." else "No output.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.md))
                        else -> CopyableMono(AnnotatedString(output.trimEnd('\n')), output, "Copy output", "Output copied")
                    }
                }
            }
        }
    }
}

/** Monospace text that scrolls sideways, with a copy button in its top-right corner. */
@Composable
private fun CopyableMono(text: AnnotatedString, copy: String, description: String, copiedDescription: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(text, style = CodeTextStyle, color = DuaTheme.colors.onCode, softWrap = false,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.md))
        IconButton(onClick = { clipboard.setText(AnnotatedString(copy)); copied = true }) {
            Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, if (copied) copiedDescription else description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconTiny + 2.dp))
        }
    }
}
