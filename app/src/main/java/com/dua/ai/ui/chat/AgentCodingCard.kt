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
            Text(when {
                coding.workspace != null -> "Coding workspace"
                coding.plan != null -> "Coding plan"
                coding.ci.isNotEmpty() -> "Pull request checks"
                else -> "Coding workspace"
            }, style = MaterialTheme.typography.titleSmall)
            coding.plan?.let { plan ->
                Text("Agent-reported plan", style = MaterialTheme.typography.labelLarge)
                if (plan.explanation.isNotBlank()) Text(plan.explanation, style = MaterialTheme.typography.bodySmall)
                plan.steps.forEachIndexed { index, step ->
                    val label = when (step.status) {
                        "completed" -> "Done"
                        "in_progress" -> if (plan.ended) "Not completed" else "In progress"
                        else -> "Pending"
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                        Text("${index + 1}. ${step.text}", style = MaterialTheme.typography.bodyMedium)
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = if (step.status == "in_progress" && !plan.ended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (plan.ended && plan.steps.any { it.status != "completed" }) Text("Reply ended · plan incomplete",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            coding.workspace?.let { w ->
                Text("${w.repository} · ${w.base.take(8)}", style = MaterialTheme.typography.bodySmall)
                w.prTarget?.let { target ->
                    Text("PR #${target.number} repair draft", style = MaterialTheme.typography.labelLarge)
                    Text(target.branch, style = MaterialTheme.typography.bodySmall)
                    Text("A separate approval is needed to commit this fix to the existing PR.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (w.paths.isEmpty()) "No draft changes" else "${w.paths.size} changed files", style = MaterialTheme.typography.bodyMedium)
                w.paths.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(if (w.saved) "Draft saved · revision ${w.revision}" else "Draft kept in this task · not saved",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            coding.terminals.forEach { run ->
                key(run.id) {
                    val status = when (run.state) {
                        "preparing" -> "Preparing command"
                        "queued" -> "Command queued"
                        "running" -> "Command running"
                        "succeeded" -> "Completed · exit ${run.exitCode ?: "unknown"}"
                        "failed" -> "Failed · exit ${run.exitCode ?: "unknown"}"
                        "timed_out" -> "Timed out"
                        "cancelled" -> "Stopped"
                        "interrupted" -> "Reply ended · command outcome unavailable"
                        else -> "Command unavailable"
                    }
                    Text(status, style = MaterialTheme.typography.labelLarge)
                    if (run.active) LinearProgressIndicator(Modifier.fillMaxWidth())
                    run.profile?.let { profile ->
                        Text("Toolchain: $profile", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(run.command, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    if (run.output.isNotEmpty()) AgentTextPreview("command output", run.output, run.truncated,
                        truncationMessage = "Output shortened at the command output limit.")
                }
            }
            coding.ci.forEach { ci -> key(ci.repository, ci.number) { AgentCiCard(ci) } }
            coding.diff?.let { AgentTextPreview("draft diff", it, coding.diffTruncated) }
            TextButton(onClick = onOpenWorkspaces, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Saved drafts") }
        }
    }
}
