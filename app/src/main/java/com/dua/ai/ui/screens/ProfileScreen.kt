package com.dua.ai.ui.screens

import com.dua.ai.ui.theme.SheetBodyStyle
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.chat.DuaDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import com.dua.ai.ui.auth.rememberGoogleSignIn
import com.dua.ai.ui.components.pageTextFieldColors
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.ui.settings.AccountContent
import com.dua.ai.ui.onboarding.OnboardingContent

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
    val phoneNumber: String = "",
    val sync: String = "Off",
    val accountId: String = "",
    val rememberedAccount: com.dua.ai.account.AccountUser? = null,
)

class ProfileActions(
    val onSignIn: () -> Unit = {},
    val onEmail: () -> Unit = {},
    val onLogOut: () -> Unit = {},
    val onDeleteAccount: (String) -> Unit = {},
    val onSync: () -> Unit = {},
    val onSavePhone: suspend (String, String) -> Unit = { _, _ -> },
)

/** Dedicated Account destination: original authentication, sync navigation and guarded destructive actions. */
@Composable
fun ProfileScreen(viewModel: ChatViewModel, onBack: () -> Unit, onEmail: () -> Unit = {}, onSync: () -> Unit = {}) {
    val account by viewModel.account.state.collectAsState()
    val google by viewModel.googleSignIn.collectAsState()
    val sync by viewModel.automaticSyncState.collectAsState()
    val signIn = rememberGoogleSignIn(viewModel)
    LaunchedEffect(Unit) { viewModel.loadAccount() }
    val state = ProfileUiState(email = account.user?.email, displayName = account.user?.displayName.orEmpty(), busy = account.busy,
        signingIn = google.busy, signInError = google.error, deletionConfigured = account.deletionConfigured, notice = account.notice, emailAuthConfigured = account.emailAuthConfigured, sync = if (sync.enabled) "On" else "Off", phoneNumber = account.user?.phoneNumber.orEmpty(), accountId = account.user?.id.orEmpty(), rememberedAccount = account.rememberedUser)
    ProfileContent(state, ProfileActions(
        onSignIn = signIn, onEmail = onEmail, onSync = onSync,
        onLogOut = viewModel::logOut,
        onSavePhone = { value, ownerId -> viewModel.setAccountPhoneNumber(value, ownerId) },
        onDeleteAccount = { confirmation ->
            val owner = account.user?.id
            viewModel.accountAction { viewModel.account.deleteAccount(confirmation, owner) }
        },
    ), onBack = onBack)
}

@Composable
fun ProfileContent(state: ProfileUiState, actions: ProfileActions, onBack: () -> Unit = {}, initialDialog: ProfileDialog? = null, showLogout: Boolean = true) {
    var dialog by rememberSaveable { mutableStateOf(initialDialog) }
    if (state.email == null) {
        OnboardingContent(busy = state.busy || state.signingIn, error = state.signInError,
            onSignIn = actions.onSignIn, onSkip = onBack, onEmail = actions.onEmail, rememberedAccount = state.rememberedAccount)
    } else AccountContent(state, actions, onBack, onLogOutRequest = { dialog = ProfileDialog.LogOut },
        onDeleteRequest = { dialog = ProfileDialog.Delete }, showLogout = showLogout)
    when (dialog) {
        ProfileDialog.LogOut -> DuaDialog(onDismissRequest = { dialog = null },
            title = "Log out?",
            text = { Text(style = SheetBodyStyle, text = "Your chats and memories are kept on the server until you delete them; this phone only holds a cache.") },
            confirmButton = { SheetTextButton("Log out", onClick = { dialog = null; actions.onLogOut() }, enabled = !state.busy, destructive = true) },
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
                    enabled = confirmation == "DELETE MY ACCOUNT" && !state.busy, destructive = true) },
                dismissButton = { SheetTextButton("Cancel", onClick = { dialog = null }) })
        }
        null -> Unit
    }
}

enum class ProfileDialog { LogOut, Delete }
