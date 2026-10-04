package com.dua.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*
import com.dua.ai.viewmodel.ChatViewModel

@Composable
fun AccountScreen(viewModel: ChatViewModel, page: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by viewModel.account.state.collectAsState()
    val preview by viewModel.cloudPreview.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    // Password is ephemeral: never saved across process/activity recreation.
    var password by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by rememberSaveable(state.user?.displayName) { mutableStateOf(state.user?.displayName.orEmpty()) }
    LaunchedEffect(page) { viewModel.loadAccount() }
    LaunchedEffect(state.user?.id) { if (state.user != null) password = "" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(when(page) { "CloudSync" -> "Cloud sync"; "Logout" -> "Log out"; else -> "Profile" }, fontSize = 22.sp)
        Text(state.user?.email ?: "Not signed in")
        if (page == "Profile") {
            if (state.user == null) {
                Text("Sign in to keep a cloud copy across web and Android. Local chat stays available without an account.", fontSize = 14.sp)
                OutlinedTextField(email, { email = it.take(254) }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                OutlinedTextField(password, { password = it.take(256) }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                FilledTonalButton(enabled = state.configured && !state.busy && email.isNotBlank() && password.isNotBlank(), onClick = { viewModel.signIn(email, password, false) }) { Text("Sign in") }
                TextButton(enabled = state.configured && !state.busy && email.isNotBlank() && password.length >= 8, onClick = { viewModel.signIn(email, password, true); password = "" }) { Text("Create account") }
                Text("Use at least 8 password characters for signup. Confirm the email, then sign in.", fontSize = 14.sp)
                TextButton(enabled = state.configured && !state.busy, onClick = { recovery = !recovery; recoveryCode = "" }) { Text("Forgot password") }
                if (recovery) {
                    Text("Enter your email above and request recovery. Use the code from the recovery email. Your email template must include the recovery code.", fontSize = 14.sp)
                    FilledTonalButton(enabled = state.configured && !state.busy && email.isNotBlank(), onClick = { viewModel.accountAction { viewModel.account.resetPassword(email) } }) { Text("Send recovery email") }
                    OutlinedTextField(recoveryCode, { recoveryCode = it.take(12) }, label = { Text("Recovery code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                    FilledTonalButton(enabled = state.configured && !state.busy && email.isNotBlank() && recoveryCode.isNotBlank(), onClick = {
                        val code = recoveryCode; recoveryCode = ""
                        viewModel.accountAction { viewModel.account.verifyRecovery(email, code) }
                    }) { Text("Verify recovery code") }
                }
                TextButton(enabled = state.googleConfigured && !state.busy, onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.dua.ai.BuildConfig.DUA_API_BASE_URL.trimEnd('/') + "/profile.html"))) }
                        .onFailure { viewModel.account.notice("A browser is unavailable on this device.") }
                }) { Text("Open Google sign-in on website") }
                Text("Google sign-in uses the browser. It does not sign this native app in; use email/password here.", fontSize = 14.sp)
            } else {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                FilledTonalButton(enabled = state.configured && !state.busy && name.isNotBlank(), onClick = { viewModel.accountAction { viewModel.account.profile(name) } }) { Text("Save profile") }
                FilledTonalButton(enabled = !state.busy, onClick = { onOpen("CloudSync") }) { Text("Cloud chat/memory sync") }
                Text(if (state.recoveryVerified) "Recovery verified. Set your new password." else "Change password", fontSize = 18.sp)
                if (!state.recoveryVerified) OutlinedTextField(currentPassword, { currentPassword = it.take(256) }, label = { Text("Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                OutlinedTextField(newPassword, { newPassword = it.take(256) }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                FilledTonalButton(enabled = !state.busy && newPassword.length >= 8 && (state.recoveryVerified || currentPassword.isNotBlank()), onClick = {
                    val next = newPassword; val current = currentPassword; newPassword = ""; currentPassword = ""
                    viewModel.accountAction { viewModel.account.changePassword(next, current) }
                }) { Text("Update password") }
                TextButton(enabled = state.deletionConfigured && !state.busy, onClick = { deleting = true; deleteConfirmation = "" }) { Text("Delete account") }
                if (!state.deletionConfigured) Text("Account deletion requires backend admin setup.", fontSize = 14.sp)
                if (deleting) {
                    Text("Delete this account and its cloud snapshot permanently? Local chats and memories remain on this device. Type DELETE MY ACCOUNT to confirm.", fontSize = 14.sp)
                    OutlinedTextField(deleteConfirmation, { deleteConfirmation = it.take(32) }, label = { Text("Deletion confirmation") }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                    FilledTonalButton(enabled = !state.busy && deleteConfirmation == "DELETE MY ACCOUNT", onClick = {
                        val confirmation = deleteConfirmation; val shownOwner = state.user?.id
                        deleteConfirmation = ""; deleting = false
                        viewModel.accountAction { viewModel.account.deleteAccount(confirmation, shownOwner) }
                    }) { Text("Permanently delete account") }
                    TextButton(enabled = !state.busy, onClick = { deleting = false; deleteConfirmation = "" }) { Text("Cancel deletion") }
                }
            }
            TextButton(enabled = !state.busy, onClick = { onOpen("Logout") }) { Text("Log out / clear saved session") }
        } else if (page == "Logout") {
            Text("Log out of this device? Local chats and memories remain on it. Use Data controls/Memory to remove them. Cloud data is retained.", fontSize = 14.sp)
            FilledTonalButton(enabled = !state.busy, onClick = { password = ""; viewModel.logOut() }) { Text("Confirm log out") }
            TextButton(onClick = { onOpen("Profile") }) { Text("Profile") }
        } else {
            Text("Manually upload a snapshot or download and merge another device's snapshot. Includes saved drafts and memory notes; no file/audio bytes. Conflicting chats stay as copies. Local or cloud deletion does not automatically delete the other copy.", fontSize = 14.sp)
            listOf("upload" to "Review upload", "download" to "Review download", "delete" to "Clear cloud snapshot").forEach { (kind, title) ->
                FilledTonalButton(enabled = state.configured && state.user != null && !state.busy, onClick = { viewModel.reviewCloud(kind) }) { Text(title) }
            }
            preview?.let { reviewed ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Confirm ${reviewed.kind} · cloud revision ${reviewed.review.revision}")
                        if (reviewed.kind != "delete") {
                            reviewed.review.snapshot.chats.forEach { chat -> Text("${chat.title}: ${chat.messages.size} messages\nDraft: ${chat.draft}", fontSize = 14.sp) }
                            Text("Memories:\n" + reviewed.review.snapshot.memory.joinToString("\n"), fontSize = 14.sp)
                        } else Text("Cloud snapshot becomes empty. Local data stays saved.")
                        Text("Check the account shown above. Signing in never uploads automatically. Imported memories keep their current on/off setting.", fontSize = 14.sp)
                        FilledTonalButton(enabled = !state.busy, onClick = { viewModel.confirmCloud() }) { Text("Confirm") }
                        TextButton(enabled = !state.busy, onClick = { viewModel.cancelCloudReview() }) { Text("Cancel") }
                    }
                }
            }
            TextButton(onClick = { onOpen("Profile") }) { Text("Profile") }
        }
        if (!state.configured) Text("Supabase account setup is not configured yet. Local data is unchanged.", fontSize = 14.sp)
        Text(state.notice, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
