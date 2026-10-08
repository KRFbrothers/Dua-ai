package com.dua.ai.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.dua.ai.R
import com.dua.ai.auth.GoogleSignIn
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Starts native Google sign-in: the account picker runs in the UI scope (it needs the Activity), the token exchange in the view model. */
@Composable
fun rememberGoogleSignIn(viewModel: ChatViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        if (!viewModel.googleSignIn.value.busy) {
            viewModel.googleSignInStarted()
            scope.launch {
                try {
                    val identity = GoogleSignIn.requestIdToken(context)
                    viewModel.signInWithGoogle(identity.idToken, identity.nonce)
                } catch (cancelled: CancellationException) { viewModel.googleSignInFailed(null); throw cancelled }
                catch (_: GetCredentialCancellationException) { viewModel.googleSignInFailed(null) }
                catch (failure: Exception) { viewModel.googleSignInFailed(GoogleSignIn.friendlyMessage(failure)) }
            }
        }
    }
}

/** Primary "Sign in with Google" button with its loading state and any error shown below it. */
@Composable
fun GoogleSignInButton(busy: Boolean, error: String?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(containerColor = DuaTheme.colors.action, contentColor = DuaTheme.colors.onAction,
                disabledContainerColor = DuaTheme.colors.action.copy(alpha = 0.6f), disabledContentColor = DuaTheme.colors.onAction)) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = DuaTheme.colors.onAction, strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.md))
                Text("Signing in…", style = MaterialTheme.typography.labelLarge)
            } else {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(Spacing.md))
                Text("Sign in with Google", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (!error.isNullOrBlank()) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
    }
}
