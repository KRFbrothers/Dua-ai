package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dua.ai.model.AgentCi
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AgentCiCard(ci: AgentCi) {
    val context = LocalContext.current
    val opener = remember(context) { SafeUriHandler(context) }
    val scheme = MaterialTheme.colorScheme
    val label = when (ci.state) {
        "success" -> "Reported checks passed"
        "failure" -> "Failure reported"
        "pending" -> "Checks still running"
        "not_run" -> "Some checks skipped or neutral"
        "none" -> "No checks reported"
        "stale" -> "PR changed · ask Agent to recheck"
        else -> "CI status could not be verified"
    }
    HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
    Text("${ci.repository} #${ci.number}", style = MaterialTheme.typography.titleSmall)
    Text(label, style = MaterialTheme.typography.labelLarge,
        color = if (ci.state == "failure") scheme.error else scheme.onSurface)
    Text("Commit ${ci.head.take(8)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ci.checkedAt))}",
        style = MaterialTheme.typography.bodySmall)
    if (!ci.complete || !ci.headCurrent) Text("Coverage or current PR head is unverified.",
        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    Text("Snapshot of this commit. Required checks and merge eligibility are not evaluated.",
        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    AgentTextPreview("CI evidence", ci.details, ci.truncated,
        truncationMessage = "Evidence shortened. Open GitHub checks for full details.")
    TextButton(onClick = { opener.openUri(ci.url) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)
        .semantics { contentDescription = "Open GitHub checks for pull request ${ci.number}" }) { Text("Open GitHub checks") }
}
