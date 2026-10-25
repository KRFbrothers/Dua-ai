package com.dua.ai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dua.ai.R
import com.dua.ai.ui.theme.SheetButtonStyle
import com.dua.ai.ui.theme.SheetDisabledAlpha
import com.dua.ai.ui.theme.SheetNoteStyle
import com.dua.ai.ui.theme.SheetRowStyle
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.SheetSubStyle
import com.dua.ai.ui.theme.SheetTheme
import com.dua.ai.ui.theme.SheetTileStyle

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
    inlineChatFiles: Boolean = false,
    showModeControls: Boolean = true,
) {
    DuaSheet(onDismiss = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AttachmentSheetContent(webMode, pageReadingAvailable, enabled, onCamera, onPhotos, onFiles, onReadLink, onAudio, storedFiles, storedFilesHint, storedFilesMaxBytes,
            modes, onImagine, onBuild, onTurnOffModes, inlineChatFiles, showModeControls)
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
    inlineChatFiles: Boolean = false,
    showModeControls: Boolean = true,
) {
    val c = SheetTheme.colors
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = SheetSizes.paddingH).padding(bottom = SheetSizes.paddingBottom)
        .testTag("attachment-sheet"), verticalArrangement = Arrangement.spacedBy(SheetSizes.sectionGap)) {
        SheetTitle("Add to chat", Modifier.padding(bottom = 0.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("attachment-sources"),
            horizontalArrangement = Arrangement.spacedBy(SheetSizes.tileGap)) {
            SheetTile("Photos", ImageVector.vectorResource(R.drawable.og_photo_library), enabled, onPhotos, Modifier.width(SheetSizes.attachmentTileWidth))
            SheetTile("Camera", ImageVector.vectorResource(R.drawable.og_photo_camera), enabled, onCamera, Modifier.width(SheetSizes.attachmentTileWidth))
            SheetTile("Files", ImageVector.vectorResource(R.drawable.og_upload), enabled, onFiles, Modifier.width(SheetSizes.attachmentTileWidth))
            SheetTile("Audio", ImageVector.vectorResource(R.drawable.og_graphic_eq), enabled, onAudio, Modifier.width(SheetSizes.attachmentTileWidth))
        }
        if (inlineChatFiles) Text("Up to 3 files / 2 MB total — images, PDFs, CSV/text and audio — go directly with chat. Photos over the limit are resized. XLSX/ODS need conversion to CSV. Media stays available for follow-ups in this session; reselect it after restoring a chat.",
            style = SheetNoteStyle, color = c.subText)
        else if (storedFiles) Text("Photos and files up to ${com.dua.ai.files.StoredFileTypes.sizeLabel(storedFilesMaxBytes.takeIf { it > 0 } ?: 52428800L)} each — images, PDFs, spreadsheets (XLSX, XLS, ODS, CSV), text and audio — upload to your private storage and remain available for follow-ups. Deleting a cached chat keeps stored files; remove them explicitly in Data controls. Camera photos and code files go with the message directly.",
            style = SheetNoteStyle, color = c.subText)
        else {
            Text("Up to 3 files, 2 MB in total: JPEG, PNG, WebP, PDF or text and code files like TXT, CSV, MD, JSON (256 KB each). Photos and PDFs go with your next message only; extracted text is saved with the chat for follow-ups and cloud sync. Large photos are resized to JPEG; originals stay unchanged.",
                style = SheetNoteStyle, color = c.subText)
            if (storedFilesHint.isNotBlank()) Text(storedFilesHint, style = SheetNoteStyle, color = c.accent)
        }
        // Read a link, the mode switches (Run code, Think, Search, X), Imagine and Build share one gray group.
        if (showModeControls) SheetGroup {
            SwitchRow("Read a link", if (pageReadingAvailable) "Reads the first HTTPS link in your message" else "Unavailable until setup is refreshed",
                Icons.Outlined.Link, webMode == "page", enabled, onReadLink)
            modes?.toggles.orEmpty().forEach { toggle ->
                SheetDivider()
                SwitchRow(toggle.label, toggle.detail, toggle.icon, toggle.on, enabled, toggle.onChange)
            }
            modes?.imagine?.let { detail ->
                SheetDivider()
                SwitchRow("Imagine", detail, Icons.Outlined.Brush, modes.imagineOn, enabled, onImagine)
            }
            modes?.build?.let { detail ->
                SheetDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = SheetSizes.rowMin).clickable(enabled = enabled, role = Role.Button, onClick = onBuild)
                    .padding(horizontal = SheetSizes.rowPaddingH, vertical = SheetSizes.rowPaddingV), verticalAlignment = Alignment.CenterVertically) {
                    RowText(Icons.Outlined.Build, "Build", detail, false, enabled)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = c.subText, modifier = Modifier.size(SheetSizes.rowIcon))
                }
            }
        }
        // Shown only while a mode is on: one tap back to a plain reply (what the old "Auto" pill used to do).
        if (showModeControls && onTurnOffModes != null) TextButton(onClick = onTurnOffModes, enabled = enabled, shape = SheetShapes.button,
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = SheetSizes.touch),
            colors = ButtonDefaults.textButtonColors(contentColor = c.text)) {
            Icon(Icons.Outlined.ToggleOff, contentDescription = null, modifier = Modifier.size(SheetSizes.rowIcon))
            Text("Turn off all modes", style = SheetButtonStyle, modifier = Modifier.padding(start = SheetSizes.tileGap))
        }
    }
}

@Composable
private fun RowScope.RowText(icon: ImageVector, label: String, detail: String, on: Boolean, enabled: Boolean) {
    val c = SheetTheme.colors
    val alpha = if (enabled) 1f else SheetDisabledAlpha
    Icon(icon, contentDescription = null, tint = (if (on) c.accent else c.icon).copy(alpha = alpha), modifier = Modifier.size(SheetSizes.rowIcon))
    Column(Modifier.weight(1f).padding(horizontal = SheetSizes.rowGap)) {
        Text(label, style = SheetRowStyle, color = c.text.copy(alpha = alpha))
        Text(detail, style = SheetSubStyle, color = c.subText.copy(alpha = alpha))
    }
}

@Composable
private fun SwitchRow(label: String, detail: String, icon: ImageVector, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = SheetSizes.rowMin)
        .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = SheetSizes.rowPaddingH, vertical = SheetSizes.rowPaddingV), verticalAlignment = Alignment.CenterVertically) {
        RowText(icon, label, detail, on, enabled)
        Switch(checked = on, onCheckedChange = null, enabled = enabled, colors = sheetSwitchColors())
    }
}

/**
 * Mode rows in the + sheet: [toggles] (Run code, Think, Search, X), Imagine
 * (null hides it; text explains its state) and Build (null hides it).
 */
data class AttachModes(val imagine: String? = null, val imagineOn: Boolean = false, val build: String? = null, val toggles: List<ModeToggle> = emptyList())

/** One mode switch in the + sheet. */
data class ModeToggle(val label: String, val detail: String, val icon: ImageVector, val on: Boolean, val onChange: (Boolean) -> Unit)

@Composable
private fun SheetTile(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = SheetTheme.colors
    val alpha = if (enabled) 1f else SheetDisabledAlpha
    Surface(onClick = onClick, enabled = enabled, shape = SheetShapes.tile, color = c.fill, modifier = modifier.semantics { role = Role.Button }) {
        Column(Modifier.fillMaxWidth().heightIn(min = SheetSizes.tileMin).padding(vertical = SheetSizes.rowPaddingV), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = c.icon.copy(alpha = alpha), modifier = Modifier.size(SheetSizes.tileIcon))
            Text(label, style = SheetTileStyle, color = c.text.copy(alpha = alpha), modifier = Modifier.padding(top = SheetSizes.tileGap))
        }
    }
}
