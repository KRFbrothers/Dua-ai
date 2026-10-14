package com.dua.ai.ui.screens

import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.chat.DuaDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.ui.auth.GoogleSignInButton
import com.dua.ai.ui.auth.rememberGoogleSignIn
import com.dua.ai.ui.chat.SheetTonalButton
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.ui.components.PageCard
import com.dua.ai.ui.components.PageStatus
import com.dua.ai.ui.components.SettingsGroup
import com.dua.ai.ui.components.SettingsNavRow
import com.dua.ai.ui.components.pageTextFieldColors
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SettingsAvatarStyle
import com.dua.ai.ui.theme.SettingsNameStyle
import com.dua.ai.ui.theme.SettingsShapes
import com.dua.ai.ui.theme.SettingsTheme
import com.dua.ai.ui.theme.SettingsValueStyle
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
    val emailAuthConfigured: Boolean = false,
)

class ProfileActions(
    val onSignIn: () -> Unit = {},
    val onEmail: () -> Unit = {},
    val onLogOut: () -> Unit = {},
    val onDeleteAccount: (String) -> Unit = {},
)

/** Account: Google sign-in and the existing delete-account confirmation. Logout lives in Profile & Settings. */
@Composable
fun ProfileScreen(viewModel: ChatViewModel, onBack: () -> Unit, onEmail: () -> Unit = {}) {
    val account by viewModel.account.state.collectAsState()
    val google by viewModel.googleSignIn.collectAsState()
    val signIn = rememberGoogleSignIn(viewModel)
    LaunchedEffect(Unit) { viewModel.loadAccount() }
    val state = ProfileUiState(email = account.user?.email, displayName = account.user?.displayName.orEmpty(), busy = account.busy,
        signingIn = google.busy, signInError = google.error, deletionConfigured = account.deletionConfigured, notice = account.notice, emailAuthConfigured = account.emailAuthConfigured)
    ProfileContent(state, ProfileActions(
        onSignIn = signIn, onEmail = onEmail,
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
            if (showLogout) SettingsGroup(title = null, modifier = Modifier.padding(top = Spacing.sm)) {
                SettingsNavRow(Icons.AutoMirrored.Outlined.Logout, "Log out", titleColor = PageTheme.colors.danger, showChevron = false,
                    enabled = !state.busy, onClick = { dialog = ProfileDialog.LogOut })
            }
            if (state.deletionConfigured) SheetTextButton("Delete account", onClick = { dialog = ProfileDialog.Delete }, enabled = !state.busy,
                destructive = true, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (state.emailAuthConfigured) SheetTonalButton(if (state.email == null) "Sign in with email" else "Set email password", onClick = actions.onEmail,
            enabled = !state.busy && !state.signingIn, modifier = Modifier.fillMaxWidth())
        if (state.notice.isNotBlank()) PageStatus(state.notice)
    }
    when (dialog) {
        ProfileDialog.LogOut -> DuaDialog(onDismissRequest = { dialog = null },
            title = "Log out?",
            text = { Text(style = SheetBodyStyle, text = "Your chats and memories are kept on the server until you delete them; this phone only holds a cache.") },
            confirmButton = { SheetTextButton("Log out", onClick = { dialog = null; actions.onLogOut() }, destructive = true) },
            dismissButton = { SheetTextButton("Cancel", onClick = { dialog = null }) })
        ProfileDialog.Delete -> {
            var confirmation by rememberSaveable { mutableStateOf("") }
            DuaDialog(onDismissRequest = { dialog = null },
                title = "Delete account?",
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(style = SheetBodyStyle, text = "This permanently deletes the account and its cloud snapshot. The cached chats and memories on this phone stay until cleared. Type DELETE MY ACCOUNT to confirm.")
                        OutlinedTextField(confirmation, { confirmation = it.take(32) }, label = { Text("Confirmation") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = pageTextFieldColors())
                    }
                },
                confirmButton = { SheetTextButton("Delete", onClick = { dialog = null; actions.onDeleteAccount(confirmation) },
                    enabled = confirmation == "DELETE MY ACCOUNT", destructive = true) },
                dismissButton = { SheetTextButton("Cancel", onClick = { dialog = null }) })
        }
        null -> Unit
    }
}

enum class ProfileDialog { LogOut, Delete }

@Composable
private fun AccountHeader(email: String?, displayName: String) {
    val c = SettingsTheme.colors
    PageCard {
        Row(Modifier.padding(PageSizes.cardPadding), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(PageSizes.avatar).background(if (email != null) c.avatar else PageTheme.colors.fill, SettingsShapes.circle),
                contentAlignment = Alignment.Center) {
                val initial = (displayName.ifBlank { email.orEmpty() }).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                if (initial != null) Text(initial.toString(), style = SettingsAvatarStyle, color = c.onAvatar)
                else Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = c.value)
            }
            Column(Modifier.weight(1f).padding(start = PageSizes.cardPadding)) {
                if (email != null) {
                    Text(displayName.ifBlank { "Dua account" }, style = SettingsNameStyle, color = c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email, style = SettingsValueStyle, color = c.value, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Not signed in", style = SettingsNameStyle, color = c.name)
                    Text("Sign in to sync across devices. Chats and memory are stored on the server; without an account the phone only holds a temporary cache.",
                        style = SettingsValueStyle, color = c.value)
                }
            }
        }
    }
}
