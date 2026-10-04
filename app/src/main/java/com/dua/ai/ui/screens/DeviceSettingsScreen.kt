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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*
import androidx.core.content.ContextCompat
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.settings.LocalReplyNotifications

@Composable
fun DeviceSettingsScreen(viewModel: ChatViewModel, page: String, onBack: () -> Unit, onPage: (String) -> Unit, onAttachments: () -> Unit) {
    val settings by viewModel.localSettings.collectAsState()
    val status by viewModel.settingsStatus.collectAsState()
    val chats by viewModel.savedChats.collectAsState()
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
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(when(page) { "ColorMode" -> "Color mode"; "FontStyle" -> "Font style"; else -> page }, fontSize = 18.sp, modifier = Modifier.padding(12.dp))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fun notice(text: String) = viewModel.settingsNotice(text)
        when (page) {
            "ColorMode", "FontStyle" -> {
                val choices = if (page == "ColorMode") listOf("dark", "light", "system") else listOf("default", "system", "serif", "monospace")
                val selectedChoice = if (page == "ColorMode") settings.theme else settings.font
                choices.forEach { choice -> FilledTonalButton(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = {
                    viewModel.updateSettings(if (page == "ColorMode") settings.copy(theme = choice) else settings.copy(font = choice))
                }) { RadioButton(selectedChoice == choice, onClick = null); Text(choice.replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(start = 12.dp)) } }
                Text("Saved across all Dua screens on this device. Default and System fonts may look similar.", fontSize = 14.sp)
                Text("I am Dua · आज मैं आपकी कैसे मदद करूँ? · 0123456789")
            }
            "Memory" -> {
                Row { Text("Use local memory in replies", modifier = Modifier.weight(1f)); Switch(settings.memoryEnabled, { viewModel.updateSettings(settings.copy(memoryEnabled = it)) }) }
                Text("Off by default. Enabled notes are sent to Gemini with new prompts. Chats do not automatically create memories. Stored only on this device.", fontSize = 14.sp)
                OutlinedTextField(note, { if (it.length <= 200) note = it }, label = { Text("New memory") }, modifier = Modifier.fillMaxWidth())
                FilledTonalButton(onClick = { if (note.isBlank()) notice("Write a memory first.") else { viewModel.updateSettings(settings.copy(memory = if (editing < 0) settings.memory + note.trim() else settings.memory.mapIndexed { index, text -> if (index == editing) note.trim() else text })); if (viewModel.localSettings.value.memory.contains(note.trim())) { note = ""; editing = -1 } } }) { Text(if (editing < 0) "Save note" else "Save edit") }
                if (editing >= 0) TextButton(onClick = { editing = -1; note = "" }) { Text("Cancel edit") }
                settings.memory.forEachIndexed { index, text -> Row { Text(text, modifier = Modifier.weight(1f).padding(12.dp)); TextButton(onClick = { editing = index; note = text }) { Text("Edit") }; TextButton(onClick = { editing = -1; note = ""; viewModel.updateSettings(settings.copy(memory = settings.memory.filterIndexed { i, _ -> i != index })) }) { Text("Delete") } } }
                TextButton(onClick = { confirming = true }) { Text("Delete all memories") }
            }
            "Notifications" -> {
                val notificationsAllowed = remember(permissionVersion) { LocalReplyNotifications.available(context) }
                Row { Text("Reply notifications", modifier = Modifier.weight(1f)); Switch(settings.notifications && notificationsAllowed, {
                    if (!it) viewModel.updateSettings(settings.copy(notifications = false))
                    else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (LocalReplyNotifications.available(context)) viewModel.updateSettings(settings.copy(notifications = true))
                    else notice("Allow notifications in app Settings first.")
                }) }
                Text("Notify when a reply completes in the background while Dua is still running. No chat text is shown. No push service is connected; Android can stop the background process.", fontSize = 14.sp)
                FilledTonalButton(onClick = { if (settings.notifications && LocalReplyNotifications.available(context)) { LocalReplyNotifications.show(context, chats.firstOrNull()?.id.orEmpty()); notice("Test notification requested.") } else notice("Enable notifications first.") }) { Text("Send test notification") }
            }
            "Permissions" -> {
                val granted = remember(permissionVersion) { ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
                Text("Microphone: ${if (granted) "Granted" else "Not granted"}")
                FilledTonalButton(onClick = { mic.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Request microphone access") }
                FilledTonalButton(onClick = onAttachments) { Text("Open camera / photos / files") }
                Text("Camera capture uses the external camera app; no direct camera permission is needed. Photos/files use the system picker and its selected-file grants.", fontSize = 14.sp)
                TextButton(onClick = { onPage("Notifications") }) { Text("Notification settings") }
            }
            "Sharing" -> {
                Text("Review completed messages before sharing. Unsent drafts and attachment bytes are excluded. This creates plain text, not a public cloud link.", fontSize = 14.sp)
                val current = chats.find { it.id == selected } ?: chats.firstOrNull()
                chats.forEach { chat -> TextButton(onClick = { selected = chat.id }) { Text((if (current?.id == chat.id) "✓ " else "") + chat.title) } }
                val text = current?.let(viewModel::shareChatText).orEmpty()
                Text(text.ifBlank { "No completed messages to share." }, fontSize = 14.sp)
                FilledTonalButton(enabled = current?.messages?.any { it.state == "complete" } == true, onClick = {
                    try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share conversation")) }
                    catch (_: Exception) { notice("Sharing unavailable on this device.") }
                }) { Text("Share conversation") }
                TextButton(enabled = current?.messages?.any { it.state == "complete" } == true, onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Dua conversation", text)); notice("Copied conversation.") }) { Text("Copy conversation") }
            }
            "Privacy" -> {
                Text("Chats, settings and memory notes are local. Sending forwards messages, selected file contents and enabled memory notes to the configured AI provider. Device/browser speech services may process speech remotely. Account sessions are encrypted on this device and excluded from backup.", fontSize = 14.sp)
                Text("Local deletion does not delete provider records, exports or other devices. Reviewed Cloud sync uploads store drafts, messages and saved notes in your Supabase account. Logout does not erase local or cloud data. Clear cloud separately in Profile → Cloud sync. Account deletion is not available.", fontSize = 14.sp)
                TextButton(onClick = { onPage("DataControls") }) { Text("Manage local chats") }
                TextButton(onClick = { onPage("Memory") }) { Text("Manage local memories") }
                TextButton(onClick = { resetting = true }) { Text("Reset local settings and memories") }
            }
        }
        if (page in listOf("Permissions", "Notifications")) TextButton(onClick = {
            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
            catch (_: Exception) { notice("App Settings unavailable.") }
        }) { Text("Open app Settings") }
        Text(status, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (resetting) AlertDialog(onDismissRequest = { resetting = false }, title = { Text("Reset settings and memories?") }, text = { Text("Appearance, notifications and memories reset. Chats stay saved.") }, confirmButton = { TextButton(onClick = { resetting = false; viewModel.resetSettings() }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { resetting = false }) { Text("Cancel") } })
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Delete all memories?") }, text = { Text("Remove every memory stored on this device. Chats remain unchanged.") }, confirmButton = { TextButton(onClick = { confirming = false; viewModel.updateSettings(settings.copy(memory = emptyList())) }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } })
}
