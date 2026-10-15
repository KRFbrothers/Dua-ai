package com.dua.ai.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dua.ai.R
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.theme.Sizes

data class EntryDispatch(val entry: ExternalEntry, val consumed: (String) -> Unit, val ready: Boolean = true, val notice: String = "")
val LocalEntryDispatch = staticCompositionLocalOf<EntryDispatch?> { null }

/** Receipt never saves anything: the owner reviews and edits the note first. */
@Composable
fun RememberThisScreen(entry: ExternalEntry, owner: String?, enabled: Boolean, onSave: (String) -> String?, onSignIn: () -> Unit,
    onDismiss: () -> Unit) {
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.text) }
    var error by rememberSaveable(entry.id) { mutableStateOf("") }
    DuaPageScaffold(title = "Remember this", onBack = onDismiss) {
        Icon(painterResource(R.drawable.gs_bookmark_add), null)
        Text("Review a note for Personal memory. It stays in this phone's cache until synced to your account; Memory is not enabled automatically.", style = MaterialTheme.typography.bodyMedium)
        Text(owner ?: "Sign in to save this note to your account.", style = MaterialTheme.typography.labelLarge)
        if (entry.notice.isNotBlank()) Text(entry.notice, style = MaterialTheme.typography.bodySmall)
        BasicTextField(note, { note = it; error = "" }, Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget)
            .semantics { contentDescription = "Memory note" }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 6)
        Text("${note.trim().length}/200 characters", style = MaterialTheme.typography.labelMedium)
        if (note.trim().length > 200) Text("Shorten the note to 200 characters before saving.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onDismiss) { Text("Cancel") }
            if (owner == null) TextButton(onSignIn) { Text("Sign in") }
            else TextButton({ error = onSave(note).orEmpty() }, enabled = enabled && note.trim().length in 1..200) { Text("Save memory") }
        }
    }
}
