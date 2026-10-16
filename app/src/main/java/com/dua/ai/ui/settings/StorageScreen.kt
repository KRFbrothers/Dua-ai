package com.dua.ai.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
        busy = busy || backupState.busy, hasBackupContent = chats.isNotEmpty() || settings.memory.isNotEmpty() || settings.projects != ProjectCodec.defaults || account.user != null,
        backupState = backupState, onBack = onBack, onOpen = onOpen, onRefresh = { refresh++ },
        onSave = { selectedUri = null; passwordAction = "save" }, onRestore = {
            try { restore.launch(arrayOf("*/*")) }
            catch (_: ActivityNotFoundException) { viewModel.fullBackup.notice("File picker unavailable on this device.") }
            catch (_: SecurityException) { viewModel.fullBackup.notice("File access unavailable. Try another location.") }
        }, onCancel = viewModel.fullBackup::cancel)
    passwordAction?.let { action ->
        BackupPasswordDialog(saving = action != "restore", onDismiss = { passwordAction = null; selectedUri = null }, onSubmit = { password ->
            passwordAction = null
            if (action == "save") {
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
        })
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
    onRefresh: () -> Unit, onSave: () -> Unit, onRestore: () -> Unit, onCancel: () -> Unit) {
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
