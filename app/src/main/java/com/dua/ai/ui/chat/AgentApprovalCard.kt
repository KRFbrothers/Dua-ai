package com.dua.ai.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.model.AgentApproval
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** What the card's bottom area shows. */
internal enum class ApprovalStage { Ask, Working, Done, Failed, Declined, Expired }

internal fun approvalStage(approval: AgentApproval, now: Long): ApprovalStage = when (approval.status) {
    AgentApproval.Status.Working -> ApprovalStage.Working
    AgentApproval.Status.Done -> ApprovalStage.Done
    AgentApproval.Status.Failed -> ApprovalStage.Failed
    AgentApproval.Status.Rejected -> ApprovalStage.Declined
    AgentApproval.Status.Expired -> ApprovalStage.Expired
    AgentApproval.Status.Pending -> if (approval.expired(now)) ApprovalStage.Expired else ApprovalStage.Ask
}

/**
 * Dua Agent approval card: the agent wants to do something that changes things (open a pull request, create an issue,
 * comment, save or delete a note). Nothing has happened yet. Approve asks the server to run exactly the action it
 * stored; Reject drops it and the reply notes that the user declined.
 */
@Composable
fun AgentApprovalCard(
    approval: AgentApproval,
    onDecide: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val scheme = MaterialTheme.colorScheme
    val stage = approvalStage(approval, now)
    Surface(shape = DuaShape.card, color = scheme.surfaceContainerLow, border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.fillMaxWidth().testTag("agent-approval")) {
        Column(Modifier.padding(Spacing.lg).animateContentSize()) {
            Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(Sizes.iconTiny))
                Text("Approval needed", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm))
            }
            Text(approval.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
            approval.details.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xxs))
            }
            approval.diff?.let { AgentTextPreview("proposal diff", it, approval.diffTruncated) }
            if (approval.tool == "propose_changes" && approval.diff == null) {
                Text("Diff preview unavailable. Ask Agent to show workspace_diff before approving.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            val statusModifier = Modifier.padding(top = Spacing.md).semantics { liveRegion = LiveRegionMode.Polite }
            when (stage) {
                ApprovalStage.Ask -> {
                    Text("Nothing has been done yet. Dua runs this only if you approve.", style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md))
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                        OutlinedButton(onClick = { onDecide(false) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)
                            .semantics { contentDescription = "Reject: ${approval.title}" }) { Text("Reject") }
                        Button(onClick = { onDecide(true) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)
                            .semantics { contentDescription = "Approve: ${approval.title}" }) { Text("Approve") }
                    }
                }
                ApprovalStage.Working -> Row(statusModifier, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(Sizes.iconTiny), strokeWidth = 2.dp)
                    Text("Working\u2026", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
                }
                ApprovalStage.Done -> StatusLine(Icons.Outlined.CheckCircle, scheme.primary, approval.result ?: "Done.", statusModifier)
                ApprovalStage.Failed -> StatusLine(Icons.Outlined.ErrorOutline, scheme.error, approval.result ?: "The action failed.", statusModifier)
                ApprovalStage.Declined -> Text("You declined. Nothing was changed.", style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, modifier = statusModifier)
                ApprovalStage.Expired -> Text(approval.result ?: "This approval expired. Ask the agent again.", style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, modifier = statusModifier)
            }
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, text: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.iconSmall))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
    }
}
