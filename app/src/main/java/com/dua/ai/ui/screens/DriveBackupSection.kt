package com.dua.ai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.backup.DriveBackup
import com.dua.ai.backup.DriveBackupApi
import com.dua.ai.viewmodel.ChatViewModel
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DriveBackupSection(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by viewModel.account.state.collectAsState()
    val dataBusy by viewModel.dataBusy.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val prefs = remember { DriveBackup.preferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var notice by remember { mutableStateOf(prefs.getString("status", "").orEmpty()) }
    var lastBackup by remember { mutableLongStateOf(prefs.getLong("lastBackup", 0)) }
    var busy by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var pendingEmail by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<CloudSnapshot?>(null) }
    var previewEmail by remember { mutableStateOf<String?>(null) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            enabled = prefs.getBoolean("enabled", false)
            notice = prefs.getString("status", "").orEmpty()
            lastBackup = prefs.getLong("lastBackup", 0)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(account.user?.email) {
        preview = null
        val connected = prefs.getString("email", null)
        if (connected != null && connected != account.user?.email) DriveBackup.disable(context)
    }
    fun complete(token: String?) {
        val action = pendingAction
        val email = pendingEmail
        pendingAction = null
        scope.launch {
            try {
                check(email != null && viewModel.account.state.value.user?.email == email) { "Account changed. Try again." }
                check(!token.isNullOrBlank()) { "Google Drive permission was not granted." }
                if (action == "enable") {
                    viewModel.prepareDriveBackup()
                    DriveBackup.enable(context, email)
                    enabled = true
                    notice = "Automatic backup enabled. First backup is scheduled."
                } else {
                    val downloaded = withContext(Dispatchers.IO) { DriveBackupApi().downloadLatest(token) }
                    check(viewModel.account.state.value.user?.email == email) { "Account changed. Try again." }
                    preview = downloaded
                    previewEmail = email
                    notice = "Backup loaded. Review before restoring."
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { notice = failure.message ?: "Drive operation failed. Try again." }
            finally { busy = false }
        }
    }
    val authorizationClient = remember { Identity.getAuthorizationClient(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try {
            if (result.resultCode != android.app.Activity.RESULT_OK) {
                pendingAction = null; busy = false; notice = "Google Drive permission was cancelled."
            } else complete(authorizationClient.getAuthorizationResultFromIntent(result.data).accessToken)
        } catch (_: Exception) { pendingAction = null; busy = false; notice = "Google Drive permission failed. Try again." }
    }
    fun authorize(action: String) {
        val email = account.user?.email ?: return
        busy = true; pendingAction = action; pendingEmail = email
        authorizationClient.authorize(DriveBackup.authorization(email))
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val intent = result.pendingIntent
                    if (intent != null) launcher.launch(IntentSenderRequest.Builder(intent).build())
                    else { busy = false; notice = "Google Drive permission is unavailable." }
                } else complete(result.accessToken)
            }.addOnFailureListener { busy = false; pendingAction = null; notice = "Google Drive could not connect. Check Google Play services and OAuth setup." }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Google Drive backup", style = MaterialTheme.typography.titleMedium)
            Text("Save chats, drafts and memory notes daily in Dua's hidden Drive folder. Google permission is separate from sign-in. Android may delay background work. Files, audio, settings and login tokens are not included.")
            Text("Backups are separate copies and use Drive storage. Older backups are kept; restore loads the newest backup for this Google account.")
            if (enabled) {
                Text("Automatic backup is on for ${prefs.getString("email", "")}")
                TextButton(enabled = !busy, onClick = {
                    try { DriveBackup.disable(context); enabled = false; notice = "Automatic backup stopped. Existing Drive backups are kept." }
                    catch (failure: Exception) { notice = failure.message.orEmpty() }
                }) { Text("Turn off automatic backup") }
            }
            FilledTonalButton(enabled = account.user != null && !account.busy && !busy && !dataBusy && !typing && preview == null,
                onClick = { authorize("enable") }) { Text(if (enabled) "Reconnect / Back up now" else "Enable automatic Drive backup") }
            FilledTonalButton(enabled = account.user != null && !account.busy && !busy && !dataBusy && !typing && preview == null,
                onClick = { authorize("restore") }) { Text("Review latest Drive backup") }
            if (lastBackup > 0) Text("Last backup: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastBackup))}")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (notice.isNotBlank()) Text(notice)
            preview?.let { snapshot ->
                Text("Restore from $previewEmail: ${snapshot.chats.size} chats, ${snapshot.memory.size} memory notes. Existing data stays; conflicting chats become copies.")
                snapshot.chats.forEach { Text("${it.title}: ${it.messages.size} messages") }
                snapshot.memory.forEach { Text("Memory: $it") }
                FilledTonalButton(enabled = !busy && !dataBusy && !typing, onClick = {
                    busy = true
                    scope.launch {
                        try { notice = viewModel.restoreDriveBackup(snapshot, previewEmail ?: error("Review again.")); preview = null }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: Exception) { notice = failure.message ?: "Restore failed." }
                        finally { busy = false }
                    }
                }) { Text("Confirm restore") }
                TextButton(enabled = !busy, onClick = { preview = null; notice = "Restore cancelled." }) { Text("Cancel") }
            }
        }
    }
}
