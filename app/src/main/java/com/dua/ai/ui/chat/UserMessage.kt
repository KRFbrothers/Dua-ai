package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.dua.ai.ui.theme.ChatMessageStyle
import com.dua.ai.ui.theme.ChatShapes
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
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

/** User turn: reference shell's right-aligned grey 20 dp bubble (16/10 dp padding, 16 sp text). */
@Composable
fun UserMessage(text: String, modifier: Modifier = Modifier, files: List<com.dua.ai.files.FileRef> = emptyList(), onOpenFile: (com.dua.ai.files.FileRef) -> Unit = {},
    onLongPress: (() -> Unit)? = null, longPressHaptic: () -> Unit = {}) {
    val (body, names) = remember(text) { splitFileNames(text) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (files.isNotEmpty()) MessageFileChips(names, files, onOpenFile)
        else if (names.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            names.take(3).forEach { name ->
                Surface(shape = DuaShape.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(Sizes.iconTiny), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Spacing.xs).widthIn(max = 140.dp))
                    }
                }
            }
        }
        Surface(color = ChatTheme.colors.userBubble, shape = ChatShapes.bubble,
            modifier = Modifier.widthIn(min = Sizes.touchTarget).heightIn(min = Sizes.touchTarget)
                .then(if (onLongPress != null) Modifier.messageLongPress(onLongPress, longPressHaptic) else Modifier)) {
            SelectionContainer {
                Text(body, style = ChatMessageStyle, color = ChatTheme.colors.onUserBubble,
                    modifier = Modifier.padding(horizontal = ChatSizes.bubblePaddingH, vertical = ChatSizes.bubblePaddingV))
            }
        }
    }
}
