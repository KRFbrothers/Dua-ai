package com.dua.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dua.ai.backup.drive.DriveBackupFile
import com.dua.ai.backup.drive.DriveBackupListState
import com.dua.ai.backup.drive.DriveBackupRecord
import com.dua.ai.files.StoredFileTypes
import com.dua.ai.ui.chat.DuaDialog
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.components.*
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.ui.theme.SettingsRowStyle
import com.dua.ai.ui.theme.SettingsValueStyle
import com.dua.ai.ui.theme.SettingsSizes
import com.dua.ai.ui.theme.SettingsTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun AutomaticDriveBackupSection(record: DriveBackupRecord, signedIn: Boolean, busy: Boolean,
    onConnect: () -> Unit, onEnable: () -> Unit, onDisable: () -> Unit, onBackup: () -> Unit, onBrowse: () -> Unit) {
    SettingsGroup("Automatic Google Drive backup") {
        SettingsNavRow(Icons.Outlined.Cloud, if (record.connected) "Reconnect Google Drive" else "Connect Google Drive",
            if (record.connected) record.email else "Grant access only to Dua AI's private backup folder", enabled = !busy, onClick = onConnect)
        SettingsRowDivider()
        SettingsToggleRow(Icons.Outlined.Schedule, "Daily automatic backup", checked = record.enabled,
            subtitle = "Wi-Fi or another unmetered network · latest 3 completed backups",
            enabled = !busy && signedIn && record.connected, onCheckedChange = { if (it) onEnable() else onDisable() })
        SettingsRowDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = SettingsSizes.rowPadding, vertical = SettingsSizes.rowGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Last successful backup", style = SettingsRowStyle, color = SettingsTheme.colors.rowText)
            Text(if (record.lastSuccess > 0) backupDate(record.lastSuccess) else "Not recorded on this phone",
                style = SettingsValueStyle, color = SettingsTheme.colors.value)
        }
        SettingsRowDivider()
        SettingsNavRow(Icons.Outlined.Backup, if (record.phase == "error" || record.phase == "setup") "Retry backup" else "Back up now",
            "Uses the same unmetered-network constraints", enabled = !busy && record.enabled && record.phase != "running", onClick = onBackup)
        SettingsRowDivider()
        SettingsNavRow(Icons.Outlined.Restore, "Browse Drive backups", "Choose a backup, enter its password, then review the restore",
            enabled = !busy && record.connected && signedIn, onClick = onBrowse)
    }
    if (record.phase == "running") {
        if (record.progress != null) LinearProgressIndicator(progress = { record.progress / 100f }, modifier = Modifier.fillMaxWidth(),
            color = PageTheme.colors.accent, trackColor = PageTheme.colors.divider)
        else LinearProgressIndicator(Modifier.fillMaxWidth(), color = PageTheme.colors.accent, trackColor = PageTheme.colors.divider)
    }
    PageStatus(record.message)
    PageNote("Automatic mode stores your backup password encrypted with Android Keystore on this phone. Keep a separate copy in your password manager. Turning it off removes the saved password and stops scheduled backups.")
    PageNote("Automatic backups use Drive's hidden app folder; browse them here. The latest 3 verified backups are kept, and older completed backups are deleted after a successful upload. Android may delay daily work and show a backup notification. Manual file-picker backups stay separate.")
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
