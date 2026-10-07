package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing

/** Attach sheet: camera, photos, files (existing types only) and the Read-link web mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    webMode: String?,
    pageReadingAvailable: Boolean,
    enabled: Boolean,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onReadLink: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        AttachmentSheetContent(webMode, pageReadingAvailable, enabled, onCamera, onPhotos, onFiles, onReadLink)
    }
}

@Composable
fun AttachmentSheetContent(
    webMode: String?,
    pageReadingAvailable: Boolean,
    enabled: Boolean,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onReadLink: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text("Add to chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SheetTile("Camera", Icons.Outlined.PhotoCamera, enabled, onCamera, Modifier.weight(1f))
            SheetTile("Photos", Icons.Outlined.PhotoLibrary, enabled, onPhotos, Modifier.weight(1f))
            SheetTile("Files", Icons.Outlined.UploadFile, enabled, onFiles, Modifier.weight(1f))
        }
        Text("Up to 3 files, 2 MB in total: JPEG, PNG, WebP, PDF or TXT (64 KB). Files go with your next message only. Large photos are resized to JPEG; originals stay unchanged.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .toggleable(value = webMode == "page", enabled = enabled, role = Role.Switch, onValueChange = onReadLink)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(horizontal = Spacing.lg)) {
                    Text("Read a link", style = MaterialTheme.typography.bodyLarge)
                    Text(if (pageReadingAvailable) "Reads the first HTTPS link in your message" else "Unavailable until setup is refreshed",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = webMode == "page", onCheckedChange = null, enabled = enabled)
            }
        }
    }
}

@Composable
private fun SheetTile(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, enabled = enabled, shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.icon + 4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}
