package com.dua.ai.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.model.BuildProposal
import com.dua.ai.model.BuildProposals
import com.dua.ai.model.ProposalFile
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** In-memory state of one proposal's Create PR request (the created PR itself is saved on the message). */
data class BuildPrUiState(val creating: Boolean = false, val error: String? = null)

/** What the review card's bottom area shows. */
internal enum class ProposalStage { Ready, Unavailable, Incomplete, Creating, Failed, Created }

internal fun proposalStage(proposal: BuildProposal, state: BuildPrUiState?, unavailable: String): ProposalStage = when {
    proposal.pullRequest != null -> ProposalStage.Created
    state?.creating == true -> ProposalStage.Creating
    state?.error != null -> ProposalStage.Failed
    unavailable.isNotBlank() -> ProposalStage.Unavailable
    !proposal.complete -> ProposalStage.Incomplete
    else -> ProposalStage.Ready
}

/** "Dua will create branch … and open a draft pull request …" for the confirmation dialog. */
internal fun confirmationText(proposal: BuildProposal, draft: Boolean): String =
    "Dua will create the new branch ${proposal.branch} in ${proposal.repository} from ${proposal.baseBranch}, commit ${proposal.filesLabel} " +
        "(+${proposal.additions} −${proposal.deletions}) and open a ${if (draft) "draft " else ""}pull request. " +
        "Nothing is merged: you review and merge it on GitHub."

/**
 * Build mode review card (Phase 5C): the model's proposed pull request with title, summary, files and coloured diffs.
 * Nothing has been written to GitHub. Only Create PR, confirmed in a dialog, asks the server to create the branch,
 * commit and pull request; afterwards the card links to the pull request.
 */
@Composable
fun BuildProposalCard(
    messageId: String,
    proposal: BuildProposal,
    state: BuildPrUiState?,
    unavailable: String,
    draft: Boolean,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyConfirming: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val opener = remember(context) { SafeUriHandler(context) }
    var confirming by rememberSaveable(messageId) { mutableStateOf(initiallyConfirming) }
    // The first diff starts open for review; proposals that already have a pull request (e.g. from history) start collapsed.
    var expanded by remember(messageId) { mutableStateOf(if (proposal.pullRequest != null) emptySet() else proposal.files.take(1).map { it.path }.toSet()) }
    val stage = proposalStage(proposal, state, unavailable)
    val added = DuaTheme.colors.diff.added
    val removed = DuaTheme.colors.diff.removed
    Surface(shape = DuaShape.card, color = scheme.surfaceContainerLow, border = BorderStroke(1.dp, scheme.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg).animateContentSize()) {
            Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.RateReview, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(Sizes.iconTiny))
                Text("Proposed pull request · ${proposal.repository.substringAfter('/')}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm))
            }
            Text(proposal.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
            Text("${proposal.baseBranch} ← ${proposal.pullRequest?.branch?.ifBlank { null } ?: proposal.branch}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Spacing.xxs))
            if (proposal.summary.isNotBlank()) Text(proposal.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.sm))
            Row(Modifier.padding(top = Spacing.md).semantics(mergeDescendants = true) {
                contentDescription = "${proposal.filesLabel}, ${proposal.additions} lines added, ${proposal.deletions} lines removed"
            }, verticalAlignment = Alignment.CenterVertically) {
                Text(proposal.filesLabel, style = MaterialTheme.typography.labelLarge)
                Text("+${proposal.additions}", style = MaterialTheme.typography.labelLarge, color = added, modifier = Modifier.padding(start = Spacing.sm))
                Text("−${proposal.deletions}", style = MaterialTheme.typography.labelLarge, color = removed, modifier = Modifier.padding(start = Spacing.xs))
            }
            Column(Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                proposal.files.forEach { file ->
                    val open = file.path in expanded
                    ProposalFileRow(file, open, added, removed) { expanded = if (open) expanded - file.path else expanded + file.path }
                    if (open) {
                        if (file.diff.isNotBlank()) CodeBlock(code = file.diff, language = "diff")
                        if (file.diffTruncated) Text("Diff shortened here; the pull request contains the full change.", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.heightIn(min = Spacing.md))
            when (stage) {
                ProposalStage.Created -> {
                    val pr = proposal.pullRequest!!
                    Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = added, modifier = Modifier.size(Sizes.iconSmall))
                        Text("${pr.label} ${if (pr.existing) "is open" else "opened"} on GitHub", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Spacing.sm))
                    }
                    Text("Review and merge it on GitHub. Dua never merges.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xxs))
                    if (pr.notice.isNotBlank()) Text(pr.notice, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xxs))
                    FilledTonalButton(onClick = { opener.openUri(pr.url) }, modifier = Modifier.padding(top = Spacing.sm).heightIn(min = Sizes.touchTarget)
                        .semantics { contentDescription = "Open ${pr.label} on GitHub" }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(Sizes.iconTiny))
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Open ${pr.label}")
                    }
                }
                else -> {
                    if (stage == ProposalStage.Failed) Row(Modifier.padding(bottom = Spacing.sm).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }, verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(Sizes.iconSmall))
                        Text(state?.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = scheme.error, modifier = Modifier.padding(start = Spacing.sm))
                    }
                    val enabled = stage == ProposalStage.Ready || stage == ProposalStage.Failed && unavailable.isBlank() && proposal.complete
                    Button(onClick = { confirming = true }, enabled = enabled, modifier = Modifier.heightIn(min = Sizes.touchTarget).semantics {
                        role = Role.Button
                        stateDescription = when (stage) { ProposalStage.Creating -> "Creating pull request"; ProposalStage.Failed -> "Last attempt failed"; else -> "" }
                    }) {
                        if (stage == ProposalStage.Creating) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = scheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconTiny))
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Creating PR…")
                        } else Text(if (stage == ProposalStage.Failed) "Try again" else "Create PR")
                    }
                    val note = when (stage) {
                        ProposalStage.Unavailable -> unavailable
                        ProposalStage.Incomplete -> "This proposal is too large to keep on this device after a restart. Ask Dua to propose it again to create a PR."
                        else -> "Nothing is written to GitHub until you tap Create PR and confirm."
                    }
                    Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
    if (confirming && proposal.pullRequest == null) {
        var title by rememberSaveable(messageId + ":title") { mutableStateOf(proposal.title) }
        var editing by rememberSaveable(messageId + ":editing") { mutableStateOf(false) }
        val valid = title.isNotBlank() && title.trim().length <= BuildProposals.MAX_TITLE
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Create pull request?") },
            text = {
                Column {
                    Text(confirmationText(proposal, draft), style = MaterialTheme.typography.bodyMedium)
                    // The title is optional to edit: shown as text until the owner taps Edit title.
                    if (editing) OutlinedTextField(title, { title = it.replace('\n', ' ').take(BuildProposals.MAX_TITLE + 20) }, label = { Text("Pull request title") },
                        singleLine = true, isError = !valid, supportingText = { Text("${title.trim().length}/${BuildProposals.MAX_TITLE}") },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
                    else {
                        Text("Title", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
                        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.xxs))
                        TextButton(onClick = { editing = true }, modifier = Modifier.padding(top = Spacing.xxs)) { Text("Edit title") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirming = false; onCreate(title.trim()) }, enabled = valid) { Text("Create PR") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProposalFileRow(file: ProposalFile, open: Boolean, added: Color, removed: Color, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val badge = when (file.action) { "create" -> added; "delete" -> removed; else -> scheme.primary }
    Surface(onClick = onToggle, shape = DuaShape.codeBlock, color = Color.Transparent, modifier = Modifier.fillMaxWidth().semantics {
        role = Role.Button
        contentDescription = "${file.label} ${file.path}, ${file.additions} added, ${file.deletions} removed. ${if (open) "Hide" else "Show"} diff"
    }) {
        Row(Modifier.heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = DuaShape.chip, color = badge.copy(alpha = .14f)) {
                Text(file.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = badge,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs))
            }
            Text(file.path, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm))
            Text("+${file.additions}", style = MaterialTheme.typography.labelMedium, color = added)
            Text("−${file.deletions}", style = MaterialTheme.typography.labelMedium, color = removed, modifier = Modifier.padding(start = Spacing.xs))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs).size(Sizes.iconSmall))
        }
    }
}
