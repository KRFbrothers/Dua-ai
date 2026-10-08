package com.dua.ai.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
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

/** Tappable source chips: site icon, page title and "number · site". Opens the source in the browser. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceChips(sources: List<ReplySource>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val opener = remember(context) { SafeUriHandler(context) }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        sources.forEach { source ->
            val title = source.pageTitle
            Surface(onClick = { opener.openUri(source.url) }, shape = DuaShape.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "Source ${source.number}: ${title ?: source.domain}, ${source.domain}. Opens in browser"; role = Role.Button
                }) {
                Row(Modifier.padding(start = Spacing.sm, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(source.domain)
                    Column(Modifier.padding(start = Spacing.sm).widthIn(max = 200.dp)) {
                        if (title != null) Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${source.number} · ${source.domain}", style = if (title != null) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            color = if (title != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Favicon from the source site, or the site's first letter while loading / when unavailable. */
@Composable
private fun SiteIcon(domain: String) {
    val loader = LocalFaviconLoader.current
    var icon by remember(domain) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(domain, loader) { if (loader != null) icon = loader(domain) }
    Box(Modifier.size(20.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        val current = icon
        if (current != null) Image(current, contentDescription = null, modifier = Modifier.size(16.dp))
        else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Text(domain.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
