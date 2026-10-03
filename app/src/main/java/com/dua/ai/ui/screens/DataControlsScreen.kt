package com.dua.ai.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.viewmodel.ChatViewModel
import java.time.Instant

@Composable
fun DataControlsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val chats by viewModel.savedChats.collectAsState()
    val busy by viewModel.dataBusy.collectAsState()
    val status by viewModel.dataStatus.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) viewModel.dataControlNotice("Export cancelled. Your chats are unchanged.")
        else viewModel.exportChats(uri)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = !busy, onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to settings") }
        Text("Data controls", fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.width(48.dp))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${chats.size} saved chats · this device", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(enabled = !busy && chats.isNotEmpty(), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), onClick = {
            try { export.launch("dua-chats-${Instant.now().toString().take(10)}.json") }
            catch (_: ActivityNotFoundException) { viewModel.dataControlNotice("File picker unavailable on this device.") }
            catch (_: SecurityException) { viewModel.dataControlNotice("File access unavailable. Try another location.") }
        }) {
            Icon(Icons.Default.Download, null); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text("Export chats", fontSize = 16.sp); Text("Save messages and drafts as JSON", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        FilledTonalButton(enabled = !busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = androidx.compose.ui.graphics.Color(0xFFFFB1C0)), onClick = { confirm = true }) {
            Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text("Clear all chats", fontSize = 16.sp); Text("Remove local chats, drafts and pending files", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text("Only this device’s history is managed here. Export includes messages and unsent drafts, without attachment bytes. Cloud accounts, other devices and previously exported files are unchanged.",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Clear all chats?") },
        text = { Text("Every chat and draft saved on this device will be deleted. Pending attachments will also be cleared. This cannot be undone. Export first if you want a copy.") },
        confirmButton = { TextButton(onClick = { confirm = false; viewModel.clearAllChats() }) { Text("Clear all") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
