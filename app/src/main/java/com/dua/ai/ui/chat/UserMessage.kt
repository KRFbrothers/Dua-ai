package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** The ViewModel appends "\nFiles: a.pdf, b.png" to a sent prompt; show those names as chips. */
internal fun splitFileNames(text: String): Pair<String, List<String>> {
    val at = text.lastIndexOf("\nFiles: ")
    if (at < 0) return text to emptyList()
    val names = text.substring(at + "\nFiles: ".length).split(", ").filter { it.isNotBlank() }
    return text.substring(0, at) to names
}

/** User turn: subtle right-aligned bubble. */
@Composable
fun UserMessage(text: String, modifier: Modifier = Modifier) {
    val (body, files) = remember(text) { splitFileNames(text) }
    Column(modifier.fillMaxWidth().padding(start = Spacing.huge), horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (files.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            files.take(3).forEach { name ->
                Surface(shape = DuaShape.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(Sizes.iconTiny), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Spacing.xs).widthIn(max = 140.dp))
                    }
                }
            }
        }
        Surface(color = DuaTheme.colors.userBubble, shape = DuaShape.userBubble) {
            SelectionContainer {
                Text(body, style = MaterialTheme.typography.bodyLarge, color = DuaTheme.colors.onUserBubble,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md))
            }
        }
    }
}
