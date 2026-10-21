package com.dua.ai.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import com.dua.ai.model.AgentCoding
import com.dua.ai.ui.theme.AgentSizes
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Plain text only: terminal output and patches are never interpreted as markdown or links. */
@Composable
internal fun AgentTextPreview(title: String, text: String, truncated: Boolean = false,
    truncationMessage: String = "Preview shortened. Review the full files before publishing.") {
    var expanded by remember(title) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = Sizes.touchTarget)
        .semantics { contentDescription = "${if (expanded) "Hide" else "Show"} $title" }) {
        Text("${if (expanded) "Hide" else "Show"} $title")
    }
    if (expanded) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            SelectionContainer {
                Box(Modifier.fillMaxWidth().heightIn(max = AgentSizes.codeMaxHeight)
                    .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(Spacing.sm)) {
                    Text(text.ifEmpty { "No text output." }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
        if (truncated) Text(truncationMessage,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AgentCodingCard(coding: AgentCoding, onOpenWorkspaces: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(if (coding.workspace == null && coding.ci.isNotEmpty()) "Pull request checks" else "Coding workspace", style = MaterialTheme.typography.titleSmall)
            coding.workspace?.let { w ->
                Text("${w.repository} · ${w.base.take(8)}", style = MaterialTheme.typography.bodySmall)
                Text(if (w.paths.isEmpty()) "No draft changes" else "${w.paths.size} changed files", style = MaterialTheme.typography.bodyMedium)
                w.paths.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(if (w.saved) "Draft saved · revision ${w.revision}" else "Draft kept in this task · not saved",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            coding.terminals.forEach { run ->
                key(run.id) {
                    val status = when (run.state) {
                        "preparing" -> "Preparing command"
                        "succeeded" -> "Completed · exit ${run.exitCode ?: "unknown"}"
                        "failed" -> "Failed · exit ${run.exitCode ?: "unknown"}"
                        "timed_out" -> "Timed out"
                        "cancelled" -> "Stopped"
                        "interrupted" -> "Reply ended · command outcome unavailable"
                        else -> "Command unavailable"
                    }
                    Text(status, style = MaterialTheme.typography.labelLarge)
                    Text(run.command, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    if (run.output.isNotEmpty()) AgentTextPreview("command output", run.output, run.truncated)
                }
            }
            coding.ci.forEach { ci -> key(ci.repository, ci.number) { AgentCiCard(ci) } }
            coding.diff?.let { AgentTextPreview("draft diff", it, coding.diffTruncated) }
            TextButton(onClick = onOpenWorkspaces, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Saved drafts") }
        }
    }
}
