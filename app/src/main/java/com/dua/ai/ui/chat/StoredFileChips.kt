package com.dua.ai.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
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

/** Stored-file uploads in the composer: progress while uploading, a per-file cancel/remove button. */
@Composable
fun UploadChips(uploads: List<PendingUpload>, removeEnabled: Boolean, onCancel: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        uploads.forEach { upload -> UploadChip(upload, removeEnabled, onCancel) }
    }
}

@Composable
private fun UploadChip(upload: PendingUpload, removeEnabled: Boolean, onCancel: (String) -> Unit) {
    val failed = upload.state == PendingUpload.FAILED
    val percent = (upload.progress * 100).toInt()
    val status = when {
        failed -> upload.error ?: "Upload failed"
        upload.uploading -> "Uploading $percent% · ${StoredFileTypes.sizeLabel(upload.size)}"
        else -> "${StoredFileTypes.label(upload.mimeType)} · ${StoredFileTypes.sizeLabel(upload.size)}"
    }
    Surface(shape = DuaShape.card, color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = status }) {
        Column(Modifier.width(220.dp)) {
            Row(Modifier.padding(start = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = DuaShape.codeBlock, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (failed) Icons.Outlined.ErrorOutline else kindIcon(upload.mimeType), null,
                            tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
                    }
                }
                Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                    Text(upload.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onCancel(upload.id) }, enabled = removeEnabled) {
                    Icon(Icons.Outlined.Close, contentDescription = if (upload.uploading) "Cancel upload of ${upload.name}" else "Remove ${upload.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (upload.uploading) LinearProgressIndicator(progress = { upload.progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** File chips on a sent message. Stored files open a short-lived signed link when tapped. */
@Composable
fun MessageFileChips(names: List<String>, files: List<FileRef>, onOpen: (FileRef) -> Unit) {
    val stored = files.associateBy { it.name }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        names.take(10).forEach { name ->
            val ref = stored[name]
            val tap = ref?.let { { onOpen(it) } }
            Surface(onClick = tap ?: {}, enabled = tap != null, shape = DuaShape.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.semantics {
                    if (ref != null) { role = Role.Button; contentDescription = "Open $name" + if (ref.transcript != null) ", transcribed" else "" }
                }) {
                Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ref?.let { kindIcon(it.mimeType) } ?: Icons.Outlined.Description, null, Modifier.size(Sizes.iconTiny),
                        tint = if (ref != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Spacing.xs).widthIn(max = 160.dp))
                    if (ref != null && ref.size > 0) Text(" · " + StoredFileTypes.sizeLabel(ref.size), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
