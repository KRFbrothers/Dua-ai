package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.files.FileRef
import com.dua.ai.files.StoredFileTypes
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

internal fun kindIcon(mimeType: String): ImageVector = when (StoredFileTypes.kind(mimeType)) {
    "image" -> Icons.Outlined.Image
    "pdf" -> Icons.Outlined.PictureAsPdf
    "sheet" -> Icons.Outlined.TableChart
    "audio" -> Icons.Outlined.GraphicEq
    else -> Icons.Outlined.Description
}

@OptIn(ExperimentalLayoutApi::class)
/** File chips on a sent message. Stored files open a short-lived signed link when tapped. */
@Composable
fun MessageFileChips(names: List<String>, files: List<FileRef>, onOpen: (FileRef) -> Unit) {
    val stored = files.associateBy { it.name }
    val c = ChatTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        names.take(10).forEach { name ->
            val ref = stored[name]
            val tap = ref?.let { { onOpen(it) } }
            Surface(onClick = tap ?: {}, enabled = tap != null, shape = DuaShape.chip, color = c.chipFill, border = BorderStroke(ChatSizes.border, c.chipBorder),
                modifier = Modifier.then(if (ref != null) Modifier.heightIn(min = Sizes.touchTarget) else Modifier).semantics {
                    if (ref != null) { role = Role.Button; contentDescription = "Open $name" + if (ref.transcript != null) ", transcribed" else "" }
                }) {
                Row(Modifier.then(if (ref != null) Modifier.heightIn(min = Sizes.touchTarget) else Modifier).padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ref?.let { kindIcon(it.mimeType) } ?: Icons.Outlined.Description, null, Modifier.size(Sizes.iconTiny),
                        tint = if (ref != null) c.accent else c.chipIcon)
                    Text(name, style = MaterialTheme.typography.labelMedium, color = c.chipText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Spacing.xs).widthIn(max = ChatSizes.fileChipNameMax))
                    if (ref != null && ref.size > 0) Text(" · " + StoredFileTypes.sizeLabel(ref.size), style = MaterialTheme.typography.labelSmall,
                        color = c.mutedText)
                }
            }
        }
    }
}
