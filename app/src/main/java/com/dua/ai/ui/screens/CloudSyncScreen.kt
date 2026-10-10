package com.dua.ai.ui.screens

import com.dua.ai.ui.components.DuaPageScaffold
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import com.dua.ai.viewmodel.ChatViewModel

/** Cloud sync: automatic foreground sync and reviewed manual snapshot transfers. Sign-in and Log out live in Profile. */
@Composable
fun CloudSyncScreen(viewModel: ChatViewModel, onBack: () -> Unit, onProfile: () -> Unit) {
    val state by viewModel.account.state.collectAsState()
    val preview by viewModel.cloudPreview.collectAsState()
    val sync by viewModel.automaticSyncState.collectAsState()
    val dataBusy by viewModel.dataBusy.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadAccount() }
    DuaPageScaffold(title = "Cloud sync", onBack = onBack) {
        Text(state.user?.email ?: "Not signed in", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.user == null) TextButton(onClick = onProfile) { Text("Sign in from Profile") }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Automatic sync on this device", Modifier.weight(1f))
                    Switch(checked = sync.enabled, onCheckedChange = { enabled ->
                        if (enabled) viewModel.enableAutomaticSync() else viewModel.disableAutomaticSync()
                    }, enabled = sync.phase != "checking" && !state.busy && !dataBusy && (sync.enabled || (state.configured && state.user != null && preview == null)),
                        modifier = Modifier.semantics { contentDescription = "Automatic cloud sync on this device" })
                }
                Text("When enabled, saved chats, drafts, memory and chat metadata sync while this app is open and online. Raw file/audio bytes stay in private Storage. Sync pauses during replies and manual reviews.", style = MaterialTheme.typography.bodyMedium)
                Text(sync.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (sync.phase == "checking") {
                    TextButton(onClick = { viewModel.disableAutomaticSync() }) { Text("Cancel sync setup") }
                } else if (sync.phase == "storage-error") {
                    TextButton(enabled = !state.busy && !dataBusy,
                        onClick = { viewModel.resetAutomaticSync() }) { Text("Reset automatic sync settings") }
                    Text("Reset clears sync consent and guards on this device. Cached chats and memories stay until the next server sync. Review both copies before enabling again.", style = MaterialTheme.typography.bodyMedium)
                } else if (sync.reviewRequired) {
                    Text("Use the manual actions below to inspect the server snapshot and this phone’s cache. Resume only after checking the changes; remaining local data can be uploaded again.", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(enabled = state.user != null && !state.busy && !dataBusy && preview == null,
                        onClick = { viewModel.resumeAutomaticSyncAfterReview() }) { Text("Resume after review") }
                } else if (sync.enabled) {
                    TextButton(enabled = state.user != null && !state.busy && !dataBusy && preview == null,
                        onClick = { viewModel.retryAutomaticSync() }) { Text("Check sync / Retry") }
                }
            }
        }
        Text("Manually upload a snapshot or download the server copy; the server copy always wins over this phone's cache. Includes saved drafts and memory notes; no file/audio bytes. Chats and memory notes on the server replace the phone's cached copies.", style = MaterialTheme.typography.bodyMedium)
        listOf("upload" to "Review upload", "download" to "Review download", "delete" to "Clear cloud snapshot").forEach { (kind, title) ->
            FilledTonalButton(enabled = state.configured && state.user != null && !state.busy, onClick = { viewModel.reviewCloud(kind) }) { Text(title) }
        }
        preview?.let { reviewed ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Confirm ${reviewed.kind} · cloud revision ${reviewed.review.revision}")
                    if (reviewed.kind != "delete") {
                        reviewed.review.snapshot.chats.forEach { chat -> Text("${chat.title}: ${chat.messages.size} messages\nDraft: ${chat.draft}", style = MaterialTheme.typography.bodyMedium) }
                        Text("Memories:\n" + reviewed.review.snapshot.memory.joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
                    } else Text("Cloud snapshot becomes empty. Local data stays saved.")
                    Text("Check the account shown above. Automatic uploads require consent on this device. Imported memories keep their current on/off setting.", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(enabled = !state.busy, onClick = { viewModel.confirmCloud() }) { Text("Confirm") }
                    TextButton(enabled = !state.busy, onClick = { viewModel.cancelCloudReview() }) { Text("Cancel") }
                }
            }
        }
        if (!state.configured) Text("Supabase account setup is not configured yet. Local data is unchanged.", style = MaterialTheme.typography.bodyMedium)
        if (state.notice.isNotBlank()) Text(state.notice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
