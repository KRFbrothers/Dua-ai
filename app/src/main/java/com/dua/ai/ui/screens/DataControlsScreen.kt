package com.dua.ai.ui.screens

import com.dua.ai.ui.theme.SheetSubStyle
import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.chat.DuaDialog
import com.dua.ai.ui.components.DuaPageScaffold
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.dua.ai.ui.components.PageNote
import com.dua.ai.ui.components.PageStatus
import com.dua.ai.ui.components.SettingsGroup
import com.dua.ai.ui.components.SettingsNavRow
import com.dua.ai.ui.components.SettingsRowDivider
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SettingsRowStyle
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.SettingsTheme
import com.dua.ai.viewmodel.ChatViewModel
import java.time.Instant

@Composable
fun DataControlsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val chats by viewModel.savedChats.collectAsState()
    val busy by viewModel.dataBusy.collectAsState()
    val status by viewModel.dataStatus.collectAsState()
    val importPreview by viewModel.importPreview.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    var confirmFiles by rememberSaveable { mutableStateOf(false) }
    val usage by viewModel.storageUsage.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    LaunchedEffect(account.user?.id, integrations.filesEnabled) { if (integrations.filesEnabled) viewModel.loadStorageUsage() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) viewModel.dataControlNotice("Export cancelled. Your chats are unchanged.")
        else viewModel.exportChats(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) viewModel.dataControlNotice("Import cancelled. Your chats are unchanged.")
        else viewModel.reviewImport(uri)
    }
    DataControlsContent(chatCount = chats.size, busy = busy, status = status, signedIn = account.user != null, filesEnabled = integrations.filesEnabled,
        usage = usage, onBack = onBack,
        onExport = {
            try { export.launch("dua-chats-${Instant.now().toString().take(10)}.json") }
            catch (_: ActivityNotFoundException) { viewModel.dataControlNotice("File picker unavailable on this device.") }
            catch (_: SecurityException) { viewModel.dataControlNotice("File access unavailable. Try another location.") }
        },
        onImport = {
            try { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            catch (_: ActivityNotFoundException) { viewModel.dataControlNotice("File picker unavailable on this device.") }
            catch (_: SecurityException) { viewModel.dataControlNotice("File access unavailable. Try another location.") }
        },
        onClear = { confirm = true }, onRefreshFiles = viewModel::loadStorageUsage, onDeleteFiles = { confirmFiles = true })
    // Capture the preview once: the dialog content recomposes separately and must not re-read a cleared state.
    importPreview?.let { preview -> DuaDialog(onDismissRequest = { viewModel.cancelImport() }, title = "Import chats and projects?",
        text = { Column(verticalArrangement = Arrangement.spacedBy(PageSizes.cardGap)) {
            Text(style = SheetBodyStyle, text = "Add ${preview.size} chats as separate copies, with their project settings? Existing chats, drafts, instructions and tasks will be kept. Differing project settings or tasks are imported separately. Importing the same file again creates additional chat copies.")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PageTheme.colors.accent, trackColor = PageTheme.colors.divider)
            Text(status, style = SheetSubStyle, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } },
        confirmButton = { SheetTextButton("Import", onClick = { viewModel.confirmImport() }, enabled = !busy) },
        dismissButton = { SheetTextButton("Cancel", onClick = { viewModel.cancelImport() }, enabled = !busy) }) }
    if (confirmFiles) DuaDialog(onDismissRequest = { confirmFiles = false }, title = "Delete all uploaded files?",
        text = { Text(style = SheetBodyStyle, text = "Every file you uploaded to your private Dua storage (photos, PDFs, spreadsheets and audio) will be permanently deleted. Chats stay, but their files can no longer be opened or re-read in follow-ups.") },
        confirmButton = { SheetTextButton("Delete files", onClick = { confirmFiles = false; viewModel.deleteAllUploadedFiles() }, destructive = true) },
        dismissButton = { SheetTextButton("Cancel", onClick = { confirmFiles = false }) })
    if (confirm) DuaDialog(onDismissRequest = { confirm = false }, title = "Clear cached chats?",
        text = { Text(style = SheetBodyStyle, text = "Remove cached chats and drafts from this phone. Server chats and their stored files remain available to restore. Phone-only chats and unsynced edits need an export or sync first. Pending attachments are discarded; unsent uploads may be deleted from storage.") },
        confirmButton = { SheetTextButton("Clear all", onClick = { confirm = false; viewModel.clearAllChats() }, destructive = true) },
        dismissButton = { SheetTextButton("Cancel", onClick = { confirm = false }) })
}

/** Data controls page body (stateless): export / import / clear rows, uploaded files card, notes and status. */
@Composable
fun DataControlsContent(chatCount: Int, busy: Boolean, status: String, signedIn: Boolean, filesEnabled: Boolean, usage: com.dua.ai.files.StorageUsage?,
    onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onClear: () -> Unit, onRefreshFiles: () -> Unit, onDeleteFiles: () -> Unit) {
    DuaPageScaffold(title = "Data controls", onBack = onBack, backEnabled = !busy, backLabel = "Back to settings") {
        PageNote("$chatCount cached chats · server is the source of truth")
        SettingsGroup(title = "Chats on this phone") {
            SettingsNavRow(Icons.Default.Download, "Export chats", subtitle = "Save messages and drafts as JSON", showChevron = false,
                enabled = !busy && chatCount > 0, onClick = onExport)
            SettingsRowDivider()
            SettingsNavRow(Icons.Default.Upload, "Import chats", subtitle = "Restore a Dua JSON export as separate copies", showChevron = false,
                enabled = !busy, onClick = onImport)
            SettingsRowDivider()
            SettingsNavRow(Icons.Default.DeleteOutline, "Clear cached chats", subtitle = "Server chats and their stored files stay", showChevron = false,
                titleColor = PageTheme.colors.danger, enabled = !busy, onClick = onClear)
        }
        StoredFilesSection(signedIn = signedIn, enabled = filesEnabled, usage = usage, busy = busy, onRefresh = onRefreshFiles, onDeleteAll = onDeleteFiles)
        PageNote("Chat controls manage this phone's cache; the separate uploaded-files action manages private server storage. The server chat copy is restored on sync. Backups include messages, drafts, project settings/instructions/tasks and project memory notes/switches, extracted attachment text, file references/transcripts, code results, Build metadata, summaries and Imagine state. Raw file/audio bytes and global memory notes are not included. Stored files still need owner sign-in and must exist on the server. Import accepts Dua version 1 JSON up to 4 MiB, 100 chats, 500 messages per chat and 8000-character drafts; the combined cache must fit too. Existing chats, project instructions, tasks and memory stay intact; differing imported project settings get a separate project. Cache clearing keeps server chats, their stored files, project settings and previously exported files.")
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PageTheme.colors.accent, trackColor = PageTheme.colors.divider)
        if (status.isNotBlank()) PageStatus(status)
    }
}

/** Settings › History and data › Data controls: storage used in the private bucket and a delete-all action. */
@Composable
fun StoredFilesSection(signedIn: Boolean, enabled: Boolean, usage: com.dua.ai.files.StorageUsage?, busy: Boolean, onRefresh: () -> Unit, onDeleteAll: () -> Unit) {
    SettingsGroup(title = "Private storage") {
        Column(Modifier.padding(PageSizes.cardPadding), verticalArrangement = Arrangement.spacedBy(PageSizes.itemGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SettingsSizes.rowGap)) {
                Icon(Icons.Default.Cloud, null, tint = SettingsTheme.colors.rowIcon, modifier = Modifier.size(SettingsSizes.rowIcon))
                Column(Modifier.weight(1f)) {
                    Text("Uploaded files", style = SettingsRowStyle, color = SettingsTheme.colors.rowText)
                    PageStatus(when {
                        !enabled -> "Stored files are not set up on the Dua backend."
                        !signedIn -> "Sign in to see and manage uploaded files."
                        usage == null -> "Checking storage…"
                        else -> "${usage.files} files · ${com.dua.ai.files.StoredFileTypes.sizeLabel(usage.bytes)} used · up to ${com.dua.ai.files.StoredFileTypes.sizeLabel(usage.maxFileBytes)} per file"
                    })
                }
                if (enabled && signedIn) SheetTextButton("Refresh", onClick = onRefresh, enabled = !busy)
            }
            PageNote("Clearing cached chats keeps their files in your private Supabase bucket. Delete all uploaded files permanently removes those files, including files needed by server chats. Discarded unsent uploads are cleaned up separately.")
            if (enabled && signedIn) SheetTextButton("Delete all uploaded files", onClick = onDeleteAll, enabled = !busy && (usage?.files ?: 1) > 0,
                destructive = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
