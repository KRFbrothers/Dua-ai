package com.dua.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.model.ReplySource
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Rich source cards; preserves the source-chip API and its single accessible browser action. */
@Composable
fun SourceChips(sources: List<ReplySource>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val opener = remember(context) { SafeUriHandler(context) }
    val c = ChatTheme.colors
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        sources.forEach { source ->
            val title = source.pageTitle
            Surface(onClick = { opener.openUri(source.url) }, shape = DuaShape.card, color = c.chipFill, border = BorderStroke(ChatSizes.border, c.chipBorder),
                modifier = Modifier.width(ChatSizes.sourceCardWidth).heightIn(min = Sizes.touchTarget).clearAndSetSemantics {
                    contentDescription = "Source ${source.number}: ${title ?: source.domain}, ${source.domain}. Opens in browser"; role = Role.Button
                    onClick(label = "Open source") { opener.openUri(source.url); true }
                }) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(source.domain)
                        Text(source.domain, style = MaterialTheme.typography.labelMedium, color = c.mutedText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Surface(shape = DuaShape.chip, color = c.shimmer) {
                            Text("[${source.number}]", style = MaterialTheme.typography.labelSmall, color = c.text,
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs))
                        }
                    }
                    Text(title ?: source.domain, style = MaterialTheme.typography.titleSmall, color = c.text,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    Box(Modifier.size(ChatSizes.siteIcon).clip(CircleShape), contentAlignment = Alignment.Center) {
        val current = icon
        if (current != null) Image(current, contentDescription = null, modifier = Modifier.size(ChatSizes.favicon))
        else Surface(shape = CircleShape, color = ChatTheme.colors.shimmer) {
            Box(Modifier.size(ChatSizes.siteIcon), contentAlignment = Alignment.Center) {
                Text(domain.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•", style = MaterialTheme.typography.labelSmall,
                    color = ChatTheme.colors.mutedText)
            }
        }
    }
}
