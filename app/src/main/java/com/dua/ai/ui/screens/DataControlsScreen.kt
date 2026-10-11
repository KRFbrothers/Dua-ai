package com.dua.ai.ui.screens

import com.dua.ai.ui.components.DuaPageScaffold
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
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
    DuaPageScaffold(title = "Data controls", onBack = onBack, backEnabled = !busy, backLabel = "Back to settings") {
        Text("${chats.size} cached chats · server is the source of truth", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(enabled = !busy && chats.isNotEmpty(), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), onClick = {
            try { export.launch("dua-chats-${Instant.now().toString().take(10)}.json") }
            catch (_: ActivityNotFoundException) { viewModel.dataControlNotice("File picker unavailable on this device.") }
            catch (_: SecurityException) { viewModel.dataControlNotice("File access unavailable. Try another location.") }
        }) {
            Icon(Icons.Default.Download, null); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text("Export chats", style = MaterialTheme.typography.bodyLarge); Text("Save messages and drafts as JSON", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        FilledTonalButton(enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface), onClick = {
            try { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            catch (_: ActivityNotFoundException) { viewModel.dataControlNotice("File picker unavailable on this device.") }
            catch (_: SecurityException) { viewModel.dataControlNotice("File access unavailable. Try another location.") }
        }) {
            Icon(Icons.Default.Upload, null); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text("Import chats", style = MaterialTheme.typography.bodyLarge); Text("Restore a Dua JSON export as separate copies", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        FilledTonalButton(enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.error), onClick = { confirm = true }) {
            Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text("Clear cached chats", style = MaterialTheme.typography.bodyLarge); Text("Remove cached chats, drafts and pending files from this phone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        StoredFilesSection(signedIn = account.user != null, enabled = integrations.filesEnabled, usage = usage, busy = busy,
            onRefresh = viewModel::loadStorageUsage, onDeleteAll = { confirmFiles = true })
        Text("Chat controls manage this phone's cache; the separate uploaded-files action manages private server storage. The server chat copy is restored on sync. Backups include messages, drafts, project settings/instructions/tasks and project memory notes/switches, extracted attachment text, file references/transcripts, code results, Build metadata, summaries and Imagine state. Raw file/audio bytes and global memory notes are not included. Stored files still need owner sign-in and must exist on the server. Import accepts Dua version 1 JSON up to 4 MiB, 100 chats, 500 messages per chat and 8000-character drafts; the combined cache must fit too. Existing chats, project instructions, tasks and memory stay intact; differing imported project settings get a separate project. Cache clearing keeps server chats, their stored files, project settings and previously exported files.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    // Capture the preview once: the dialog content recomposes separately and must not re-read a cleared state.
    importPreview?.let { preview -> AlertDialog(onDismissRequest = { viewModel.cancelImport() }, title = { Text("Import chats and projects?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add ${preview.size} chats as separate copies, with their project settings? Existing chats, drafts, instructions and tasks will be kept. Differing project settings or tasks are imported separately. Importing the same file again creates additional chat copies.")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.confirmImport() }) { Text("Import") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { viewModel.cancelImport() }) { Text("Cancel") } }) }
    if (confirmFiles) AlertDialog(onDismissRequest = { confirmFiles = false }, title = { Text("Delete all uploaded files?") },
        text = { Text("Every file you uploaded to your private Dua storage (photos, PDFs, spreadsheets and audio) will be permanently deleted. Chats stay, but their files can no longer be opened or re-read in follow-ups.") },
        confirmButton = { TextButton(onClick = { confirmFiles = false; viewModel.deleteAllUploadedFiles() }) { Text("Delete files") } },
        dismissButton = { TextButton(onClick = { confirmFiles = false }) { Text("Cancel") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear cached chats?") },
        text = { Text("Remove cached chats and drafts from this phone. Server chats and their stored files remain available to restore. Phone-only chats and unsynced edits need an export or sync first. Pending attachments are discarded; unsent uploads may be deleted from storage.") },
        confirmButton = { TextButton(onClick = { confirm = false; viewModel.clearAllChats() }) { Text("Clear all") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

/** Settings › Data & privacy › Data controls: storage used in the private bucket and a delete-all action. */
@Composable
fun StoredFilesSection(signedIn: Boolean, enabled: Boolean, usage: com.dua.ai.files.StorageUsage?, busy: Boolean, onRefresh: () -> Unit, onDeleteAll: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, null)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Uploaded files", style = MaterialTheme.typography.bodyLarge)
                    Text(when {
                        !enabled -> "Stored files are not set up on the Dua backend."
                        !signedIn -> "Sign in to see and manage uploaded files."
                        usage == null -> "Checking storage…"
                        else -> "${usage.files} files · ${com.dua.ai.files.StoredFileTypes.sizeLabel(usage.bytes)} used · up to ${com.dua.ai.files.StoredFileTypes.sizeLabel(usage.maxFileBytes)} per file"
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (enabled && signedIn) TextButton(enabled = !busy, onClick = onRefresh) { Text("Refresh") }
            }
            Text("Clearing cached chats keeps their files in your private Supabase bucket. Delete all uploaded files permanently removes those files, including files needed by server chats. Discarded unsent uploads are cleaned up separately.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (enabled && signedIn) OutlinedButton(enabled = !busy && (usage?.files ?: 1) > 0, onClick = onDeleteAll, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Delete all uploaded files")
            }
        }
    }
}
