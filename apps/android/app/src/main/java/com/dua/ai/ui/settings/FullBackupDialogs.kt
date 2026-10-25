package com.dua.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dua.ai.backup.FullBackupPreview
import com.dua.ai.files.StoredFileTypes
import com.dua.ai.ui.chat.DuaDialog
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.components.PageBody
import com.dua.ai.ui.components.PageNote
import com.dua.ai.ui.components.PageStatus
import com.dua.ai.ui.components.pageTextFieldColors
import com.dua.ai.ui.theme.Spacing

/** Passwords deliberately use remember, never rememberSaveable, preferences or SavedStateHandle. */
@Composable
internal fun BackupPasswordDialog(saving: Boolean, onDismiss: () -> Unit, onSubmit: (CharArray) -> Unit,
    title: String? = null, submitLabel: String? = null, description: String? = null) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length in 12..256 && (!saving || password == confirmation)
    DuaDialog(onDismissRequest = onDismiss, title = title ?: if (saving) "Protect your full backup" else "Unlock full backup",
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PageBody(description ?: if (saving) "Chats, projects, Personal notes and raw Dua AI files will be encrypted together." else "Enter the password used when this backup was created.")
                OutlinedTextField(password, { if (it.length <= 256) password = it }, label = { Text("Backup password") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, colors = pageTextFieldColors(), modifier = Modifier.fillMaxWidth())
                if (saving) OutlinedTextField(confirmation, { if (it.length <= 256) confirmation = it }, label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true, colors = pageTextFieldColors(), modifier = Modifier.fillMaxWidth())
                PageNote("Use 12–256 characters. Keep this password in your password manager; Dua AI cannot recover it.")
            }
        },
        confirmButton = { SheetTextButton(submitLabel ?: if (saving) "Choose destination" else "Verify backup", enabled = valid, onClick = {
            val key = password.toCharArray(); password = ""; confirmation = ""; onSubmit(key)
        }) },
        dismissButton = { SheetTextButton("Cancel", onClick = { password = ""; confirmation = ""; onDismiss() }) })
}

@Composable
internal fun FullBackupReviewDialog(preview: FullBackupPreview, busy: Boolean, canRestoreFiles: Boolean,
    onCancel: () -> Unit, onConfirm: () -> Unit, status: String = "") {
    DuaDialog(onDismissRequest = { if (!busy) onCancel() }, title = "Restore full backup?",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PageBody("${preview.chats} chats · ${preview.projects} projects · ${preview.notes} Personal notes · ${preview.files} raw files (${StoredFileTypes.sizeLabel(preview.bytes)})")
                PageNote("Chats and project settings are added as copies. Personal notes are merged, up to 10 in total. Memory's On/Off setting stays as it is on this phone.")
                PageNote("Missing cloud files are recovered to the original account. Existing cloud files are verified and kept. Sync restored chats and notes afterwards.")
                if (preview.files > 0 && !canRestoreFiles) PageNote("Sign in and update Feature setup with file restore support before restoring raw files.")
                if (status.isNotBlank()) PageStatus(status)
            }
        },
        confirmButton = { SheetTextButton("Restore backup", enabled = !busy && (preview.files == 0 || canRestoreFiles), onClick = onConfirm) },
        dismissButton = { SheetTextButton("Cancel", enabled = !busy, onClick = onCancel) })
}
