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
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwitchDefaults
import com.dua.ai.ui.theme.DuaTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.GraphicEq
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
    onAudio: () -> Unit = {},
    storedFiles: Boolean = false,
    storedFilesHint: String = "",
    storedFilesMaxBytes: Long = 0,
    modes: AttachModes? = null,
    onImagine: (Boolean) -> Unit = {},
    onBuild: () -> Unit = {},
    onTurnOffModes: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        AttachmentSheetContent(webMode, pageReadingAvailable, enabled, onCamera, onPhotos, onFiles, onReadLink, onAudio, storedFiles, storedFilesHint, storedFilesMaxBytes,
            modes, onImagine, onBuild, onTurnOffModes)
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
    onAudio: () -> Unit = {},
    storedFiles: Boolean = false,
    storedFilesHint: String = "",
    storedFilesMaxBytes: Long = 0,
    modes: AttachModes? = null,
    onImagine: (Boolean) -> Unit = {},
    onBuild: () -> Unit = {},
    onTurnOffModes: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text("Add to chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SheetTile("Camera", Icons.Outlined.PhotoCamera, enabled, onCamera, Modifier.weight(1f))
            SheetTile("Photos", Icons.Outlined.PhotoLibrary, enabled, onPhotos, Modifier.weight(1f))
            SheetTile("Files", Icons.Outlined.UploadFile, enabled, onFiles, Modifier.weight(1f))
            SheetTile("Audio", Icons.Outlined.GraphicEq, enabled, onAudio, Modifier.weight(1f))
        }
        if (storedFiles) Text("Photos and files up to ${com.dua.ai.files.StoredFileTypes.sizeLabel(storedFilesMaxBytes.takeIf { it > 0 } ?: 52428800L)} each — images, PDFs, spreadsheets (XLSX, XLS, ODS, CSV), text and audio — upload to your private storage and remain available for follow-ups. Deleting a cached chat keeps stored files; remove them explicitly in Data controls. Camera photos and code files go with the message directly.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            Text("Up to 3 files, 2 MB in total: JPEG, PNG, WebP, PDF or text and code files like TXT, CSV, MD, JSON (256 KB each). Photos and PDFs go with your next message only; extracted text is saved with the chat for follow-ups and cloud sync. Large photos are resized to JPEG; originals stay unchanged.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (storedFilesHint.isNotBlank()) Text(storedFilesHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
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
                Switch(checked = webMode == "page", onCheckedChange = null, enabled = enabled, colors = duaSwitchColors())
            }
        }
        // In a conversation the chip row only shows modes that are on, so every mode can be switched here.
        if (!modes?.toggles.isNullOrEmpty()) ModeToggleGroup(modes!!.toggles, enabled)
        // Imagine and Build live here (the Home chip row keeps Run code, Reasoning and Search).
        modes?.imagine?.let { detail ->
            Surface(shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .toggleable(value = modes.imagineOn, enabled = enabled, role = Role.Switch, onValueChange = onImagine)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.lg)) {
                        Text("Imagine", style = MaterialTheme.typography.bodyLarge)
                        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = modes.imagineOn, onCheckedChange = null, enabled = enabled, colors = duaSwitchColors())
                }
            }
        }
        modes?.build?.let { detail ->
            Surface(onClick = onBuild, enabled = enabled, shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Build, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.lg)) {
                        Text("Build", style = MaterialTheme.typography.bodyLarge)
                        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Shown only while a mode is on: one tap back to a plain reply (what the old "Auto" pill used to do).
        if (onTurnOffModes != null) TextButton(onClick = onTurnOffModes, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
            Icon(Icons.Outlined.ToggleOff, contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
            Text("Turn off all modes", modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

/** Switches in the + sheet use the selected-chip palette: silver (dark) or ink (light) track with a gold thumb. */
@Composable
private fun duaSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = DuaTheme.colors.chipSelected,
    checkedBorderColor = DuaTheme.colors.chipSelected,
    checkedThumbColor = DuaTheme.colors.chipSelectedIcon,
    checkedIconColor = DuaTheme.colors.chipSelected,
)

/**
 * Mode rows in the + sheet: [toggles] (Run code, Reasoning, Search, X; shown in conversations), Imagine
 * (null hides it; text explains its state) and Build (null hides it).
 */
data class AttachModes(val imagine: String? = null, val imagineOn: Boolean = false, val build: String? = null, val toggles: List<ModeToggle> = emptyList())

/** One mode switch in the + sheet. */
data class ModeToggle(val label: String, val detail: String, val icon: ImageVector, val on: Boolean, val onChange: (Boolean) -> Unit)

@Composable
private fun ModeToggleGroup(toggles: List<ModeToggle>, enabled: Boolean) {
    Surface(shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            toggles.forEachIndexed { index, toggle ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 56.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = toggle.on, enabled = enabled, role = Role.Switch, onValueChange = toggle.onChange)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Icon(toggle.icon, contentDescription = null, tint = if (toggle.on) DuaTheme.colors.sparkle else MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.lg)) {
                        Text(toggle.label, style = MaterialTheme.typography.bodyLarge)
                        Text(toggle.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = toggle.on, onCheckedChange = null, enabled = enabled, colors = duaSwitchColors())
                }
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
