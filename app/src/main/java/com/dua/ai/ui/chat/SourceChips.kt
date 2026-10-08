package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ReplySource
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Spacing

/** Compact, tappable source chips: number and domain. Opens the source in the browser. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceChips(sources: List<ReplySource>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val opener = remember(context) { SafeUriHandler(context) }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        sources.forEach { source ->
            Surface(onClick = { opener.openUri(source.url) }, shape = DuaShape.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "Source ${source.number}: ${source.title}, ${source.domain}. Opens in browser"; role = Role.Button
                }) {
                Row(Modifier.padding(start = Spacing.xs, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            Text("${source.number}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(source.domain, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Spacing.sm).widthIn(max = 180.dp))
                }
            }
        }
    }
}
