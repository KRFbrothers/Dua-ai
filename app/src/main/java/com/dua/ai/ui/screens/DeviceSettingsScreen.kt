package com.dua.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.core.content.ContextCompat
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.settings.LocalReplyNotifications
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.components.SettingsSection

@Composable
fun DeviceSettingsScreen(viewModel: ChatViewModel, page: String, onBack: () -> Unit, onPage: (String) -> Unit, onAttachments: () -> Unit) {
    val settings by viewModel.localSettings.collectAsState()
    val status by viewModel.settingsStatus.collectAsState()
    val chats by viewModel.savedChats.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val context = LocalContext.current
    var editing by rememberSaveable { mutableIntStateOf(-1) }
    var note by rememberSaveable { mutableStateOf("") }
    var resetting by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("") }
    var permissionVersion by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) { val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permissionVersion++ }; lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) } }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionVersion++; viewModel.settingsNotice(if (granted) "Microphone permission granted. Start recording from Voice." else "Microphone denied. Allow it in app Settings.") }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && LocalReplyNotifications.available(context)) viewModel.updateSettings(settings.copy(notifications = true))
        else viewModel.settingsNotice("Notifications not granted. Allow them in app Settings.")
    }
    DuaPageScaffold(title = when (page) { "ColorMode", "FontStyle", "Appearance" -> "Appearance"; else -> page }, onBack = onBack) {
        fun notice(text: String) = viewModel.settingsNotice(text)
        when (page) {
            "ColorMode", "FontStyle", "Appearance" -> {
                SettingsSection("Color mode") {
                    listOf("dark" to "Dark", "light" to "Light", "system" to "Match system").forEach { (choice, label) ->
                        ChoiceRow(label, selected = settings.theme == choice) { viewModel.updateSettings(settings.copy(theme = choice)) }
                    }
                }
                SettingsSection("Font") {
                    listOf("default" to "Inter (default)", "system" to "Device font", "serif" to "Serif", "monospace" to "Monospace").forEach { (choice, label) ->
                        ChoiceRow(label, selected = settings.font == choice) { viewModel.updateSettings(settings.copy(font = choice)) }
                    }
                }
                Text("Saved on this device and applied to every Dua screen. Text size follows your phone's font size setting.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Text("I am Dua · आज मैं आपकी कैसे मदद करूँ? · 0123456789", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                }
            }
            "Memory" -> {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { Text("Use memory in replies", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(settings.memoryEnabled, { viewModel.updateSettings(settings.copy(memoryEnabled = it)) }) }
                Text("Off by default. Enabled notes are sent to Gemini with new prompts. Chats do not automatically create memories. The server copy is the source of truth; this phone keeps a cache.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(note, { if (it.length <= 200) note = it }, label = { Text("New memory") }, modifier = Modifier.fillMaxWidth())
                FilledTonalButton(onClick = { if (note.isBlank()) notice("Write a memory first.") else { viewModel.updateSettings(settings.copy(memory = if (editing < 0) settings.memory + note.trim() else settings.memory.mapIndexed { index, text -> if (index == editing) note.trim() else text })); if (viewModel.localSettings.value.memory.contains(note.trim())) { note = ""; editing = -1 } } }) { Text(if (editing < 0) "Save note" else "Save edit") }
                if (editing >= 0) TextButton(onClick = { editing = -1; note = "" }) { Text("Cancel edit") }
                settings.memory.forEachIndexed { index, text -> Row(verticalAlignment = Alignment.CenterVertically) { Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(12.dp)); TextButton(onClick = { editing = index; note = text }) { Text("Edit") }; TextButton(onClick = { editing = -1; note = ""; viewModel.updateSettings(settings.copy(memory = settings.memory.filterIndexed { i, _ -> i != index })) }) { Text("Delete") } } }
                TextButton(onClick = { confirming = true }) { Text("Delete all memories") }
            }
            "Notifications" -> {
                val notificationsAllowed = remember(permissionVersion) { LocalReplyNotifications.available(context) }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { Text("Reply notifications", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(settings.notifications && notificationsAllowed, {
                    if (!it) viewModel.updateSettings(settings.copy(notifications = false))
                    else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (LocalReplyNotifications.available(context)) viewModel.updateSettings(settings.copy(notifications = true))
                    else notice("Allow notifications in app Settings first.")
                }) }
                Text("Notify when a reply completes in the background while Dua is still running. No chat text is shown. No push service is connected; Android can stop the background process.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { if (settings.notifications && LocalReplyNotifications.available(context)) { LocalReplyNotifications.show(context, chats.firstOrNull()?.id.orEmpty()); notice("Test notification requested.") } else notice("Enable notifications first.") }) { Text("Send test notification") }
            }
            "Permissions" -> {
                val granted = remember(permissionVersion) { ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
                Text("Microphone: ${if (granted) "Granted" else "Not granted"}", style = MaterialTheme.typography.bodyLarge)
                FilledTonalButton(onClick = { mic.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Request microphone access") }
                FilledTonalButton(onClick = onAttachments) { Text("Add photos or files in chat") }
                Text("Camera capture uses the external camera app; no direct camera permission is needed. Photos/files use the system picker and its selected-file grants.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onPage("Notifications") }) { Text("Notification settings") }
            }
            "Sharing" -> {
                Text("Review completed messages before sharing. Unsent drafts and attachment bytes are excluded. This creates plain text, not a public cloud link.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val current = chats.find { it.id == selected } ?: chats.firstOrNull()
                chats.forEach { chat -> TextButton(onClick = { selected = chat.id }) { Text((if (current?.id == chat.id) "✓ " else "") + chat.title) } }
                val text = current?.let(viewModel::shareChatText).orEmpty()
                Text(text.ifBlank { "No completed messages to share." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(enabled = current?.messages?.any { it.state == "complete" } == true, onClick = {
                    try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share conversation")) }
                    catch (_: Exception) { notice("Sharing unavailable on this device.") }
                }) { Text("Share conversation") }
                TextButton(enabled = current?.messages?.any { it.state == "complete" } == true, onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Dua conversation", text)); notice("Copied conversation.") }) { Text("Copy conversation") }
            }
            "Privacy" -> {
                Text("Chats and memory notes live on the server; this phone keeps a disposable cache. Chat history is excluded from Android backups. Sending forwards messages, selected file contents and enabled memory notes to the configured AI provider. Device/browser speech services may process speech remotely. Account sessions are encrypted on this device and excluded from backup.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Removing cached chats keeps server chats and their stored files. Discarding unsent attachments may delete temporary uploads. Provider records and exports remain unchanged. Your drafts, messages and saved notes are authoritative in your Supabase account. Logout does not erase cached or server data. Clear cloud separately in Settings → Cloud sync. " + if (account.deletionConfigured) "To delete your account and its cloud data, use Delete account in Settings → Profile." else "Account deletion is not configured on this server.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onPage("DataControls") }) { Text("Manage cached chats") }
                TextButton(onClick = { onPage("Memory") }) { Text("Manage cached memories") }
                TextButton(onClick = { resetting = true }) { Text("Reset local settings and memories") }
            }
        }
        if (page in listOf("Permissions", "Notifications")) TextButton(onClick = {
            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
            catch (_: Exception) { notice("App Settings unavailable.") }
        }) { Text("Open app Settings") }
        Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (resetting) AlertDialog(onDismissRequest = { resetting = false }, title = { Text("Reset settings and memories?") }, text = { Text("Appearance, notifications and memories reset. Cached chats and readable project settings stay saved.") }, confirmButton = { TextButton(onClick = { resetting = false; viewModel.resetSettings() }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { resetting = false }) { Text("Cancel") } })
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Delete all memories?") }, text = { Text("Remove every cached memory on this device. With sync on, the deleted notes are removed from the server on the next sync. Chats remain unchanged.") }, confirmButton = { TextButton(onClick = { confirming = false; viewModel.updateSettings(settings.copy(memory = emptyList())) }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } })
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
