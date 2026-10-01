package com.nova.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaPrimaryButton
import com.nova.app.ui.components.NovaSecondaryButton
import com.nova.app.ui.components.NovaTextButton
import com.nova.app.ui.components.NovaTextField
import com.nova.app.ui.theme.NovaTheme

@Composable
fun LoginScreen(
    onLogin: () -> Unit,
    onSignup: () -> Unit,
    onForgot: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AuthScaffold(title = "Welcome back", subtitle = "Sign in to Nova") {
        AnimatedVisibility(visible = error != null, enter = fadeIn() + slideInVertically()) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        NovaTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = "Email",
            keyboardType = KeyboardType.Email,
            leading = { Icon(Icons.Outlined.Email, contentDescription = null) }
        )
        Spacer(Modifier.height(16.dp))
        NovaTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Password",
            isPassword = true,
            leading = { Icon(Icons.Outlined.Lock, contentDescription = null) }
        )
        NovaTextButton("Forgot password?", onClick = onForgot)
        Spacer(Modifier.height(8.dp))
        NovaPrimaryButton(
            text = "Sign in",
            loading = loading,
            onClick = {
                if (email.isBlank() || password.length < 6) {
                    error = "Check your email and password."
                } else {
                    loading = true
                    onLogin()
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        NovaSecondaryButton("Continue with Google", onClick = onLogin)
        Spacer(Modifier.height(24.dp))
        NovaTextButton("New here? Create account", onClick = onSignup)
    }
}

@Composable
fun SignupScreen(onSignup: () -> Unit, onLogin: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AuthScaffold(title = "Create account", subtitle = "Join Nova in a minute") {
        NovaTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            leading = { Icon(Icons.Outlined.Person, contentDescription = null) }
        )
        Spacer(Modifier.height(16.dp))
        NovaTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
            leading = { Icon(Icons.Outlined.Email, contentDescription = null) }
        )
        Spacer(Modifier.height(16.dp))
        NovaTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            isPassword = true,
            supportingText = if (password.isNotEmpty() && password.length < 6) "Use at least 6 characters." else null,
            isError = password.isNotEmpty() && password.length < 6,
            leading = { Icon(Icons.Outlined.Lock, contentDescription = null) }
        )
        Spacer(Modifier.height(16.dp))
        NovaTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm password",
            isPassword = true,
            isError = confirm.isNotEmpty() && confirm != password,
            supportingText = if (confirm.isNotEmpty() && confirm != password) "Passwords do not match." else null
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        NovaPrimaryButton(
            text = "Create account",
            loading = loading,
            onClick = {
                when {
                    name.isBlank() || email.isBlank() -> error = "Name and email are required."
                    password.length < 6 -> error = "Use at least 6 characters."
                    password != confirm -> error = "Passwords do not match."
                    else -> {
                        loading = true
                        onSignup()
                    }
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        NovaTextButton("Already have an account? Sign in", onClick = onLogin)
    }
}

@Composable
fun ForgotPasswordScreen(onSent: () -> Unit, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AuthScaffold(title = "Reset password", subtitle = "We’ll email a reset link.") {
        if (sent) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Email sent",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))
            Text("Check your inbox", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "If an account exists for that email, a link is on the way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            NovaTextButton("Back to sign in", onClick = onBack)
        } else {
            NovaTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = "Email",
                keyboardType = KeyboardType.Email,
                isError = error != null,
                supportingText = error,
                leading = { Icon(Icons.Outlined.Email, contentDescription = null) }
            )
            Spacer(Modifier.height(24.dp))
            NovaPrimaryButton(
                text = "Send link",
                onClick = {
                    if (!email.contains("@")) error = "Enter a valid email."
                    else {
                        sent = true
                        onSent()
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            NovaTextButton("Back to sign in", onClick = onBack)
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginLight() {
    NovaTheme(false) { LoginScreen({}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun LoginDark() {
    NovaTheme(true) { LoginScreen({}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun SignupLight() {
    NovaTheme(false) { SignupScreen({}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun ForgotLight() {
    NovaTheme(false) { ForgotPasswordScreen({}, {}) }
}
