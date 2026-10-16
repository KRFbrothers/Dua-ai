package com.dua.ai.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PersonalMemory
import com.dua.ai.ui.chat.DuaDialog
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.components.*
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.theme.*
import com.dua.ai.viewmodel.ChatViewModel

@Composable
fun MemoryScreen(viewModel: ChatViewModel, onBack: () -> Unit, onOpen: (DuaDestination) -> Unit) {
    val settings by viewModel.localSettings.collectAsState()
    val account by viewModel.account.state.collectAsState()
    val busy by viewModel.dataBusy.collectAsState()
    val status by viewModel.settingsStatus.collectAsState()
    val owner = account.user?.id
    // A dialog opened for one account must not edit another account's notes after sign-in/out.
    key(owner) {
        fun update(transform: (LocalPreferences) -> LocalPreferences): Boolean {
            if (viewModel.dataBusy.value || viewModel.account.state.value.busy || viewModel.account.state.value.user?.id != owner) return false
            return try {
                val next = transform(viewModel.localSettings.value)
                viewModel.updateSettings(next)
                viewModel.localSettings.value == next
            } catch (failure: IllegalArgumentException) {
                viewModel.settingsNotice(failure.message ?: "Memory could not be saved.")
                false
            }
        }
        MemoryContent(settings, enabled = !busy && !account.busy, signedIn = owner != null, status = status,
            onBack = onBack, onOpen = onOpen,
            onEnabled = { value -> update { it.copy(memoryEnabled = value) } },
            onSave = { index, original, text -> update { it.copy(memory = PersonalMemory.save(it.memory, index, original, text)) } },
            onDelete = { index, original -> update { it.copy(memory = PersonalMemory.delete(it.memory, index, original)) } })
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MemoryContent(settings: LocalPreferences, enabled: Boolean, signedIn: Boolean, status: String,
    onBack: () -> Unit, onOpen: (DuaDestination) -> Unit, onEnabled: (Boolean) -> Unit,
    onSave: (Int?, String, String) -> Boolean, onDelete: (Int, String) -> Boolean) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var editIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var original by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var deleteIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var deleteOriginal by rememberSaveable { mutableStateOf("") }
    val changed = editIndex?.let { settings.memory.getOrNull(it) != original } == true
    DuaPageScaffold("Memory", onBack) {
        SettingsGroup(null) {
            SettingsToggleRow(null, "Use Personal memory", "Turning this off keeps your saved notes.",
                checked = settings.memoryEnabled, enabled = enabled, onCheckedChange = onEnabled)
        }
        PageNote("Personal notes stay separate from project memory. Up to 10 notes, 200 characters each.")
        if (settings.memory.isEmpty()) PageBody("No saved notes. Add a preference or fact you want Dua AI to remember.")
        settings.memory.forEachIndexed { index, note ->
            PagePaddedCard {
                PageBody(note)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SheetTextButton("Edit note ${index + 1}", enabled = enabled, onClick = {
                        editIndex = index; original = note; text = note; editing = true
                    })
                    SheetTextButton("Delete note ${index + 1}", enabled = enabled, destructive = true, onClick = {
                        deleteIndex = index; deleteOriginal = note
                    })
                }
            }
        }
        SheetTextButton("Add note", enabled = enabled && settings.memory.size < 10, onClick = {
            editIndex = null; original = ""; text = ""; editing = true
        })
        PageNote(if (signedIn) "Changes are cached on this phone. Sync to save them to your account; the server copy wins on download."
            else "Guest notes stay on this phone. Sign in and review cloud sync to save them to your account.")
        SettingsGroup(null) {
            SettingsNavRow(Icons.Outlined.CloudSync, if (signedIn) "Cloud sync" else "Sign in", showChevron = true) {
                onOpen(if (signedIn) DuaDestination.CloudSync else DuaDestination.Account)
            }
        }
        if (status.isNotBlank()) PageStatus(status)
    }
    if (editing) DuaDialog(onDismissRequest = { editing = false }, title = if (editIndex == null) "Add memory note" else "Edit memory note",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(text, { if (it.length <= 200) text = it }, label = { Text("Memory note") },
                    supportingText = { Text("${text.length}/200") }, minLines = 2, maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("personal-memory-input"), colors = pageTextFieldColors(), enabled = enabled)
                if (changed) PageStatus("This note changed. Cancel and reopen it before saving.")
                if (status.isNotBlank()) PageStatus(status)
            }
        },
        confirmButton = { SheetTextButton("Save note", enabled = enabled && !changed && text.isNotBlank() && (editIndex != null || settings.memory.size < 10),
            onClick = { if (onSave(editIndex, original, text)) editing = false }) },
        dismissButton = { SheetTextButton("Cancel", onClick = { editing = false }) })
    deleteIndex?.let { index ->
        val deleteChanged = settings.memory.getOrNull(index) != deleteOriginal
        DuaDialog(onDismissRequest = { deleteIndex = null }, title = "Delete memory note?",
            text = { PageBody(if (deleteChanged) "This note changed. Cancel and review it before deleting."
                else if (signedIn) "Delete this note from the phone cache? Sync afterwards to remove it from your account too."
                else "Delete this note from this phone?") },
            confirmButton = { SheetTextButton("Delete note", enabled = enabled && !deleteChanged, destructive = true,
                onClick = { if (onDelete(index, deleteOriginal)) deleteIndex = null }) },
            dismissButton = { SheetTextButton("Cancel", onClick = { deleteIndex = null }) })
    }
}
