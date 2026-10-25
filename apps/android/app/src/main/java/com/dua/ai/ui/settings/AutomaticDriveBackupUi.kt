package com.dua.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dua.ai.backup.drive.DriveBackupFile
import com.dua.ai.backup.drive.DriveBackupListState
import com.dua.ai.backup.drive.DriveBackupRecord
import com.dua.ai.files.StoredFileTypes
import com.dua.ai.ui.chat.DuaDialog
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.chat.SheetFilledButton
import com.dua.ai.ui.chat.sheetSwitchColors
import com.dua.ai.ui.components.*
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.BackupBodyStyle
import com.dua.ai.ui.theme.BackupRowTitleStyle
import com.dua.ai.ui.theme.Sizes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun AutomaticDriveBackupSection(record: DriveBackupRecord, signedIn: Boolean, busy: Boolean,
    onConnect: () -> Unit, onEnable: () -> Unit, onDisable: () -> Unit, onBackup: () -> Unit, onBrowse: () -> Unit,
    connectionStatus: String = "", connecting: Boolean = false, onManageStorage: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Backup settings", style = BackupBodyStyle, color = PageTheme.colors.subText)
        Text("Back up your chats and files to Google Drive so you can restore them on a new Android phone.",
            style = BackupBodyStyle, color = PageTheme.colors.subText)
        Column(Modifier.padding(top = Spacing.xl, bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Last backup: " + if (record.lastSuccess > 0) backupDate(record.lastSuccess) else "Not recorded on this phone",
                style = BackupBodyStyle, color = PageTheme.colors.text)
            Text("Size: " + (record.lastSize?.takeIf { record.lastSuccess > 0 }?.let(StoredFileTypes::sizeLabel) ?: "Not recorded"),
                style = BackupBodyStyle, color = PageTheme.colors.text)
        }
        SheetFilledButton(if (record.phase == "error" || record.phase == "setup") "Retry backup" else "Back up", onClick = onBackup,
            enabled = !busy && signedIn && record.enabled && record.phase != "running")
        val status = if (connecting) "Connecting Google Drive…" else connectionStatus.ifBlank { record.message }
        if (status.isNotBlank()) PageStatus(status, Modifier.padding(top = Spacing.sm, bottom = Spacing.md))
        if (record.phase == "running") {
            if (record.progress != null) LinearProgressIndicator(progress = { record.progress / 100f }, modifier = Modifier.fillMaxWidth(),
                color = PageTheme.colors.accent, trackColor = PageTheme.colors.divider)
            else LinearProgressIndicator(Modifier.fillMaxWidth(), color = PageTheme.colors.accent, trackColor = PageTheme.colors.divider)
        }
        BackupRow("Google Account", if (record.connected && signedIn) record.email else if (signedIn) "Connect Google Drive" else "Sign in to connect Google Drive",
            enabled = !busy, onClick = onConnect)
        if (onManageStorage != null) BackupRow("Manage Google storage", "Open Google Drive storage settings", enabled = !busy, onClick = onManageStorage)
        BackupAutomaticToggle(record.enabled, !busy && signedIn && record.connected) { if (it) onEnable() else onDisable() }
        BackupRow("Backup network", "Wi-Fi or another unmetered network")
        HorizontalDivider(color = PageTheme.colors.divider, modifier = Modifier.padding(vertical = Spacing.lg))
        Text("Backup protection", style = BackupBodyStyle, color = PageTheme.colors.subText)
        BackupRow("Password-protected backup", "Always encrypted. Your password is required to unlock a backup.",
            trailing = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = PageTheme.colors.subText, modifier = Modifier.size(Sizes.icon)) })
        PageNote("Automatic mode stores your backup password encrypted with Android Keystore on this phone. Keep a separate copy in your password manager. Turning it off removes the saved password and stops scheduled backups.")
        PageNote("The latest 3 completed backups are kept. Android may delay daily backups until network, battery and storage conditions allow.")
        PageNote("Automatic backups use Drive's hidden app folder; browse them here. Older completed backups are deleted after a successful upload. Manual file-picker backups stay separate.")
        BackupRow("Browse Drive backups", "Choose a backup, enter its password, then review the restore",
            enabled = !busy && record.connected && signedIn, onClick = onBrowse)
    }
}

@Composable
private fun BackupAutomaticToggle(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Automatic backups", style = BackupRowTitleStyle, color = PageTheme.colors.text)
            Text(if (checked) "Daily" else "Off", style = BackupBodyStyle, color = PageTheme.colors.subText)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = sheetSwitchColors())
    }
}

@Composable
internal fun DriveBackupListDialog(state: DriveBackupListState, onDismiss: () -> Unit, onRefresh: () -> Unit, onRestore: (DriveBackupFile) -> Unit) {
    DuaDialog(onDismissRequest = onDismiss, title = "Drive backups", text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = PageTheme.colors.accent, trackColor = PageTheme.colors.divider)
                PageBody("Loading completed backups…")
            } else if (state.error.isNotBlank()) PageStatus(state.error)
            else if (state.files.isEmpty()) PageBody("No completed automatic backups were found for this account. Manual backups can still be restored with the file picker.")
            state.files.forEach { file ->
                SettingsNavRow(Icons.Outlined.Restore, backupDate(file.createdAt), StoredFileTypes.sizeLabel(file.size), enabled = !state.busy) { onRestore(file) }
            }
            Text("Selecting a backup only downloads the encrypted file. Your password and restore confirmation are still required.", style = SheetBodyStyle)
        }
    }, confirmButton = { SheetTextButton("Refresh", onClick = onRefresh, enabled = !state.busy) },
        dismissButton = { SheetTextButton("Close", onClick = onDismiss) })
}

private fun backupDate(time: Long): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
