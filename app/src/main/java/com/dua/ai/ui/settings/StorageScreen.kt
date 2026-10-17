package com.dua.ai.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.result.IntentSenderRequest
import android.app.Activity
import com.dua.ai.backup.drive.DriveAccess
import com.dua.ai.backup.drive.DriveFailure
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ActivityNotFoundException
import android.net.Uri
import com.dua.ai.backup.FullBackupState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.files.CacheUsage
import com.dua.ai.files.StorageOverview
import com.dua.ai.files.StorageUsage
import com.dua.ai.files.StorageUsageStatus
import com.dua.ai.files.StoredFileTypes
import com.dua.ai.ui.components.*
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun StorageScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    val context = LocalContext.current
    val chats by viewModel.savedChats.collectAsState()
    val settings by viewModel.localSettings.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val integrations by viewModel.integrations.collectAsState()
    val usage by viewModel.storageUsage.collectAsState()
    val usageStatus by viewModel.storageUsageStatus.collectAsState()
    val busy by viewModel.dataBusy.collectAsState()
    val backupState by viewModel.fullBackup.state.collectAsState()
    val driveRestoreBusy by viewModel.driveRestoreBusy.collectAsState()
    val drive = viewModel.driveBackups
    val driveRecord by drive.store.state.collectAsState()
    val driveList by drive.listState.collectAsState()
    val scope = rememberCoroutineScope()
    var showDriveBackups by rememberSaveable { mutableStateOf(false) }
    var selectedDriveId by rememberSaveable { mutableStateOf<String?>(null) }
    var authOwner by rememberSaveable { mutableStateOf("") }
    var authEmail by rememberSaveable { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var driveConnectionStatus by rememberSaveable(account.user?.id) { mutableStateOf("") }
    val authorization = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        authBusy = false
        if (result.resultCode != Activity.RESULT_OK) driveConnectionStatus = "Drive permission was cancelled. Existing backups were kept."
        else scope.launch {
            authBusy = true
            try {
                val access = drive.authorization.result(result.data)
                check(access is DriveAccess.Token) { "Drive permission was not completed. Reconnect and try again." }
                drive.connect(access.value, authOwner, authEmail)
                driveConnectionStatus = ""
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { driveConnectionStatus = if (failure is DriveFailure) failure.message.orEmpty() else "Drive could not be connected. Check account and Google Cloud setup, then retry." }
            finally { authBusy = false }
        }
    }
    var cache by remember { mutableStateOf<CacheUsage?>(null) }
    var cacheError by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var passwordAction by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var heldPassword by remember { mutableStateOf<CharArray?>(null) }
    DisposableEffect(Unit) { onDispose { heldPassword?.fill('\u0000') } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = heldPassword; heldPassword = null
        if (uri == null) { password?.fill('\u0000'); viewModel.fullBackup.notice("Backup cancelled. Your data is unchanged.") }
        else if (password != null) viewModel.fullBackup.save(uri, password)
        else { selectedUri = uri.toString(); passwordAction = "save-selected" }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) viewModel.fullBackup.notice("Restore cancelled. Your data is unchanged.")
        else { selectedUri = uri.toString(); passwordAction = "restore" }
    }
    LaunchedEffect(account.user?.id, integrations.filesEnabled, refresh) {
        if (integrations.filesEnabled) viewModel.loadStorageUsage()
    }
    LaunchedEffect(refresh, busy) {
        if (busy) return@LaunchedEffect
        cache = null; cacheError = ""
        try { cache = StorageOverview.cacheUsage(context.cacheDir) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { cacheError = "App cache size could not be measured. Refresh to retry." }
    }
    val owner = account.user?.id
    StorageContent(chats.size, ProjectCodec.userProjects(settings.projects, chats).size, cache, cacheError,
        signedIn = owner != null, filesEnabled = integrations.filesEnabled,
        usage = usage.takeIf { owner != null && usageStatus.ownerId == owner },
        usageStatus = usageStatus.takeIf { it.ownerId == owner } ?: StorageUsageStatus(),
        busy = busy || backupState.busy || authBusy, hasBackupContent = chats.isNotEmpty() || settings.memory.isNotEmpty() || settings.projects != ProjectCodec.defaults || account.user != null,
        backupState = if (driveRestoreBusy) backupState.copy(busy = true, status = "Downloading encrypted Drive backup…") else backupState,
        onBack = onBack, onOpen = onOpen, onRefresh = { refresh++ },
        onSave = { selectedUri = null; passwordAction = "save" }, onRestore = {
            try { restore.launch(arrayOf("*/*")) }
            catch (_: ActivityNotFoundException) { viewModel.fullBackup.notice("File picker unavailable on this device.") }
            catch (_: SecurityException) { viewModel.fullBackup.notice("File access unavailable. Try another location.") }
        }, onCancel = viewModel::cancelBackupOperation, automaticBackup = {
            AutomaticDriveBackupSection(driveRecord, account.user != null, busy || backupState.busy || authBusy || driveList.busy,
                onConnect = {
                    val user = account.user
                    if (user == null) onOpen(DuaDestination.Account)
                    else scope.launch {
                        authBusy = true; driveConnectionStatus = ""; authOwner = user.id; authEmail = user.email
                        try {
                            when (val access = drive.authorization.request(user.email)) {
                                is DriveAccess.Token -> { drive.connect(access.value, user.id, user.email); driveConnectionStatus = ""; authBusy = false }
                                is DriveAccess.Consent -> authorization.launch(IntentSenderRequest.Builder(access.intent).build())
                            }
                        } catch (cancelled: CancellationException) { authBusy = false; throw cancelled }
                        catch (failure: Exception) { authBusy = false; driveConnectionStatus = if (failure is DriveFailure) failure.message.orEmpty() else "Drive permission could not open. Check Google Play services and retry." }
                    }
                }, onEnable = { passwordAction = "enable-automatic" },
                onDisable = { scope.launch {
                    try { withContext(Dispatchers.IO) { drive.disable() } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { viewModel.fullBackup.notice("Could not save the automatic-backup Off setting. Scheduled work was stopped; retry turning it off.") }
                } },
                onBackup = { runCatching { drive.backupNow() }.onFailure { viewModel.fullBackup.notice("Backup could not be scheduled. Reconnect Drive and retry.") } },
                onBrowse = { showDriveBackups = true; scope.launch { drive.loadBackups() } },
                connectionStatus = driveConnectionStatus, connecting = authBusy)
        })
    passwordAction?.let { action ->
        BackupPasswordDialog(saving = action != "restore" && action != "restore-drive", onDismiss = { passwordAction = null; selectedUri = null; selectedDriveId = null }, onSubmit = { password ->
            passwordAction = null
            if (action == "enable-automatic") scope.launch {
                try { drive.enable(password) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { viewModel.fullBackup.notice("Automatic backup could not be enabled. Connect Drive and choose the password again.") }
            }
            else if (action == "restore-drive") {
                val id = selectedDriveId; selectedDriveId = null
                if (id == null) { password.fill('\u0000'); viewModel.fullBackup.notice("Select the Drive backup again.") }
                else viewModel.reviewDriveBackup(id, password)
            }
            else if (action == "save") {
                heldPassword = password
                try { save.launch("dua-full-${java.time.LocalDate.now()}.duabackup") }
                catch (_: ActivityNotFoundException) { heldPassword?.fill('\u0000'); heldPassword = null; viewModel.fullBackup.notice("File picker unavailable on this device.") }
                catch (_: SecurityException) { heldPassword?.fill('\u0000'); heldPassword = null; viewModel.fullBackup.notice("File access unavailable. Try another location.") }
            } else {
                val uri = selectedUri?.let(Uri::parse)
                selectedUri = null
                if (uri == null) { password.fill('\u0000'); viewModel.fullBackup.notice("Choose the backup location again.") }
                else if (action == "restore") viewModel.fullBackup.review(uri, password)
                else viewModel.fullBackup.save(uri, password)
            }
        }, title = if (action == "enable-automatic") "Enable daily Drive backup?" else null,
            submitLabel = if (action == "enable-automatic") "Enable daily backup" else null,
            description = if (action == "enable-automatic") "Encrypt all backup data with this password and store it protected by Android Keystore on this phone. Upload daily on an unmetered network; keep 3 completed backups and remove older ones after success. Save a separate copy of your password." else null)
    }
    if (showDriveBackups) DriveBackupListDialog(driveList, { showDriveBackups = false }, { scope.launch { drive.loadBackups() } }) { file ->
        selectedDriveId = file.id; showDriveBackups = false; passwordAction = "restore-drive"
    }
    backupState.preview?.takeIf { !backupState.busy }?.let { preview ->
        FullBackupReviewDialog(preview, busy, account.user != null && integrations.filesEnabled && integrations.filesRestoreEnabled,
            viewModel.fullBackup::cancelReview, viewModel.fullBackup::confirmRestore, status = backupState.status)
    }
}

@Composable
internal fun StorageContent(chatCount: Int, projectCount: Int, cache: CacheUsage?, cacheError: String,
    signedIn: Boolean, filesEnabled: Boolean, usage: StorageUsage?, usageStatus: StorageUsageStatus,
    busy: Boolean, hasBackupContent: Boolean, backupState: FullBackupState, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit,
    onRefresh: () -> Unit, onSave: () -> Unit, onRestore: () -> Unit, onCancel: () -> Unit, automaticBackup: @Composable () -> Unit = {}) {
    DuaPageScaffold("Storage", onBack, backEnabled = !busy) {
        SettingsGroup("On this phone") {
            SettingsValueRow("Cached chats", chatCount.toString())
            SettingsRowDivider()
            SettingsValueRow("Saved projects", projectCount.toString())
            SettingsRowDivider()
            SettingsValueRow("App cache", cache?.let { StoredFileTypes.sizeLabel(it.bytes) } ?: "—")
            SettingsRowDivider()
            SettingsValueRow("Downloaded media", cache?.let { StoredFileTypes.sizeLabel(it.mediaBytes) } ?: "—")
        }
        PageNote("Downloaded media is included in app cache. These are temporary copies; this page does not delete them.")
        if (cacheError.isNotBlank()) PageStatus(cacheError)
        SettingsGroup("Private cloud files") {
            if (signedIn && filesEnabled && usage != null) {
                SettingsValueRow("Files", usage.files.toString())
                SettingsRowDivider()
                SettingsValueRow("Storage used", StoredFileTypes.sizeLabel(usage.bytes))
                SettingsRowDivider()
                SettingsValueRow("Per-file limit", StoredFileTypes.sizeLabel(usage.maxFileBytes))
            } else {
                SettingsNavRow(Icons.Outlined.Cloud, when {
                    !filesEnabled -> "Set up cloud files"
                    !signedIn -> "Sign in to view cloud files"
                    usageStatus.loading -> "Checking storage…"
                    else -> "Cloud usage unavailable"
                }, subtitle = when {
                    !filesEnabled -> "Open Feature setup to check private file storage."
                    !signedIn -> "Usage is available only to the signed-in owner."
                    usageStatus.error.isNotBlank() -> usageStatus.error
                    else -> "Refresh to load the current usage."
                }, enabled = !busy && (!signedIn || !filesEnabled), showChevron = !signedIn || !filesEnabled) {
                    onOpen(if (!filesEnabled) DuaDestination.FeatureSetup else DuaDestination.Account)
                }
            }
        }
        SettingsGroup("Google Drive backup") {
            SettingsNavRow(Icons.Outlined.SaveAlt, "Save full backup", "Protect with a password, then choose Google Drive in the file picker.",
                enabled = !busy && hasBackupContent, onClick = onSave)
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.Restore, "Restore full backup", "Unlock a .duabackup file and review before restoring.",
                enabled = !busy, onClick = onRestore)
        }
        PageNote("Drive must be available in the phone's file picker. Saving a copy is manual; Google sign-in alone does not connect automatic Drive backup.")
        automaticBackup()
        PageNote("One encrypted file includes cached chats, drafts, projects, Personal memory, available in-chat attachments and private cloud file bytes. Login tokens and API keys are excluded. Up to 1 GB per archive and 256 MB per raw file. Temporary phone space is needed to prepare and verify the backup.")
        PageNote("Use account sync first to bring server-only chats and notes onto this phone. Raw cloud files restore to the original signed-in account. Traditional JSON exports are still available in Data controls.")
        SettingsGroup(null) {
            SettingsNavRow(Icons.Outlined.Refresh, "Refresh storage", enabled = !busy, showChevron = false, onClick = onRefresh)
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.Storage, "Manage chats and files", "Export, import or review deletion controls", enabled = !busy) { onOpen(DuaDestination.DataControls) }
            SettingsRowDivider()
            SettingsNavRow(Icons.Outlined.CloudSync, "Account cloud sync", "Sync chats and Personal memory with your account", enabled = !busy) { onOpen(DuaDestination.CloudSync) }
        }
        if (backupState.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = PageTheme.colors.accent, trackColor = PageTheme.colors.divider)
            SheetTextButton("Cancel backup operation", onClick = onCancel)
        }
        if (backupState.status.isNotBlank()) PageStatus(backupState.status)
    }
}
