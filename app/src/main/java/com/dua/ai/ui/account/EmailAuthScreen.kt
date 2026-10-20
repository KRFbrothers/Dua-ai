package com.dua.ai.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dua.ai.account.EmailCredentials
import com.dua.ai.ui.components.DuaPageScaffold
import com.dua.ai.viewmodel.ChatViewModel

enum class EmailAuthMode { SignIn, SignUp, SetPassword }

@Composable
fun EmailAuthScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val account by viewModel.account.state.collectAsState()
    val progress by viewModel.emailAuth.collectAsState()
    val owner by rememberSaveable { mutableStateOf(account.user?.id) }
    val email = remember { account.user?.email ?: viewModel.emailSignInHint }
    DisposableEffect(Unit) { viewModel.clearEmailAuth(); onDispose { viewModel.clearEmailAuth() } }
    LaunchedEffect(Unit) { if (!account.emailAuthConfigured) viewModel.loadAccount() }
    LaunchedEffect(progress.completed, account.user?.id) {
        if (progress.completed || owner == null && account.user != null) { viewModel.prepareEmailSignIn(); onBack() }
    }
    EmailAuthContent(initialMode = if (owner == null) EmailAuthMode.SignIn else EmailAuthMode.SetPassword,
        initialEmail = email, configured = account.emailAuthConfigured, busy = progress.busy || account.busy,
        error = if (owner != null && account.user?.id != owner) "Account changed. Return to Account before setting a password." else progress.error,
        notice = progress.confirmation, onBack = { viewModel.clearEmailAuth(); viewModel.prepareEmailSignIn(); onBack() }, onRefresh = viewModel::loadAccount,
        onSubmit = { address, password, signup -> viewModel.submitEmailAuth(address, password, signup, owner) })
}

/** Credentials live in memory only: no rememberSaveable, preferences, cloud snapshot or backup. */
@Composable
fun EmailAuthContent(initialMode: EmailAuthMode = EmailAuthMode.SignIn, initialEmail: String = "", configured: Boolean = true,
    busy: Boolean = false, error: String? = null, notice: String = "", onBack: () -> Unit = {}, onRefresh: () -> Unit = {},
    onSubmit: (String, String, Boolean) -> Unit = { _, _, _ -> }) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { password = ""; confirmation = ""; visible = false } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val creating = mode != EmailAuthMode.SignIn
    val title = when (mode) { EmailAuthMode.SignIn -> "Email sign-in"; EmailAuthMode.SignUp -> "Create owner account"; EmailAuthMode.SetPassword -> "Set email password" }
    val ready = configured && !busy && EmailCredentials.validEmail(email) && EmailCredentials.validPassword(password, creating) && (!creating || password == confirmation)
    BackHandler { password = ""; confirmation = ""; onBack() }
    DuaPageScaffold(title, onBack = { password = ""; confirmation = ""; onBack() }) {
        Text(if (mode == EmailAuthMode.SetPassword) "Set a password for this signed-in account. Google sign-in remains available."
            else "Only the owner's configured email can use this Dua server.")
        if (mode == EmailAuthMode.SignUp) Text("Confirm your email before signing in. If you already use Google, sign in with Google and set an email password in Account.")
        OutlinedTextField(email, { email = it.take(512) }, readOnly = mode == EmailAuthMode.SetPassword, enabled = !busy,
            label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Account email" })
        OutlinedTextField(password, { password = it.take(1024) }, enabled = !busy, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Account password" })
        if (creating) OutlinedTextField(confirmation, { confirmation = it.take(1024) }, enabled = !busy,
            label = { Text("Confirm password") }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Confirm account password" })
        TextButton(onClick = { visible = !visible }, enabled = !busy) { Text(if (visible) "Hide password" else "Show password") }
        if (creating || password.length > 128) Text("Use 8–128 characters for a new password. Passwords are not saved on this phone.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (notice.isNotBlank()) Text(notice, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (!configured) {
            Text("Email authentication needs the updated owner backend and Supabase email setup.")
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh account setup") }
        }
        Button(enabled = ready, onClick = {
            val submitted = password; password = ""; confirmation = ""; visible = false
            onSubmit(email.trim(), submitted, mode == EmailAuthMode.SignUp)
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (busy) "Please wait…" else when (mode) { EmailAuthMode.SignIn -> "Sign in"; EmailAuthMode.SignUp -> "Create account"; EmailAuthMode.SetPassword -> "Set password" })
        }
        if (mode != EmailAuthMode.SetPassword) TextButton(enabled = !busy, onClick = {
            mode = if (mode == EmailAuthMode.SignIn) EmailAuthMode.SignUp else EmailAuthMode.SignIn
            password = ""; confirmation = ""; visible = false
        }) { Text(if (mode == EmailAuthMode.SignIn) "Create owner account" else "Use email sign-in") }
    }
}
