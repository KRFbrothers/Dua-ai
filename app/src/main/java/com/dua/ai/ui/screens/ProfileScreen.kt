package com.dua.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.ui.auth.GoogleSignInButton
import com.dua.ai.ui.auth.rememberGoogleSignIn
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.viewmodel.ChatViewModel

@Immutable
data class ProfileUiState(
    /** Signed-in account email; null when signed out. */
    val email: String? = null,
    val displayName: String = "",
    val busy: Boolean = false,
    val signingIn: Boolean = false,
    val signInError: String? = null,
    val deletionConfigured: Boolean = false,
    val notice: String = "",
)

class ProfileActions(
    val onSignIn: () -> Unit = {},
    val onLogOut: () -> Unit = {},
    val onDeleteAccount: (String) -> Unit = {},
)

/** Account: Google sign-in and the existing delete-account confirmation. Logout lives in Profile & Settings. */
@Composable
fun ProfileScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val account by viewModel.account.state.collectAsState()
    val google by viewModel.googleSignIn.collectAsState()
    val signIn = rememberGoogleSignIn(viewModel)
    LaunchedEffect(Unit) { viewModel.loadAccount() }
    val state = ProfileUiState(email = account.user?.email, displayName = account.user?.displayName.orEmpty(), busy = account.busy,
        signingIn = google.busy, signInError = google.error, deletionConfigured = account.deletionConfigured, notice = account.notice)
    ProfileContent(state, ProfileActions(
        onSignIn = signIn,
        onLogOut = viewModel::logOut,
        onDeleteAccount = { confirmation ->
            val owner = account.user?.id
            viewModel.accountAction { viewModel.account.deleteAccount(confirmation, owner) }
        },
    ), onBack = onBack, showLogout = false)
}

@Composable
fun ProfileContent(state: ProfileUiState, actions: ProfileActions, onBack: () -> Unit = {}, initialDialog: ProfileDialog? = null, showLogout: Boolean = true) {
    var dialog by rememberSaveable { mutableStateOf(initialDialog) }
    DuaPageScaffold(title = if (showLogout) "Profile" else "Account", onBack = onBack) {
        AccountHeader(state.email, state.displayName)
        if (state.email == null) {
            GoogleSignInButton(busy = state.signingIn, error = state.signInError, onClick = actions.onSignIn, modifier = Modifier.padding(top = Spacing.sm))
        } else {
            if (showLogout) OutlinedButton(onClick = { dialog = ProfileDialog.LogOut }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = Spacing.sm),
                shape = MaterialTheme.shapes.extraLarge, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Log out", style = MaterialTheme.typography.labelLarge)
            }
            if (state.deletionConfigured) TextButton(onClick = { dialog = ProfileDialog.Delete }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Delete account", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.notice.isNotBlank()) Text(state.notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    when (dialog) {
        ProfileDialog.LogOut -> AlertDialog(onDismissRequest = { dialog = null },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
            title = { Text("Log out?") },
            text = { Text("Your chats and memories are kept on the server until you delete them; this phone only holds a cache.") },
            confirmButton = { TextButton(onClick = { dialog = null; actions.onLogOut() }) { Text("Log out", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })
        ProfileDialog.Delete -> {
            var confirmation by rememberSaveable { mutableStateOf("") }
            AlertDialog(onDismissRequest = { dialog = null },
                title = { Text("Delete account?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text("This permanently deletes the account and its cloud snapshot. The cached chats and memories on this phone stay until cleared. Type DELETE MY ACCOUNT to confirm.")
                        OutlinedTextField(confirmation, { confirmation = it.take(32) }, label = { Text("Confirmation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(enabled = confirmation == "DELETE MY ACCOUNT", onClick = { dialog = null; actions.onDeleteAccount(confirmation) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })
        }
        null -> Unit
    }
}

enum class ProfileDialog { LogOut, Delete }

@Composable
private fun AccountHeader(email: String?, displayName: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = DuaShape.card, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(if (email != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center) {
                val initial = (displayName.ifBlank { email.orEmpty() }).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                if (initial != null) Text(initial.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                else Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(start = Spacing.lg)) {
                if (email != null) {
                    Text(displayName.ifBlank { "Google account" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Not signed in", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Sign in to sync across devices. Chats and memory are stored on the server; without an account the phone only holds a temporary cache.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
