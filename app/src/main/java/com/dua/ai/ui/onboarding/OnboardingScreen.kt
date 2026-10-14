package com.dua.ai.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import com.dua.ai.R
import com.dua.ai.account.AccountUser
import com.dua.ai.ui.auth.rememberGoogleSignIn
import com.dua.ai.ui.components.AccountAvatar
import com.dua.ai.ui.theme.*
import com.dua.ai.viewmodel.ChatViewModel

@Composable
fun OnboardingScreen(viewModel: ChatViewModel, onDone: () -> Unit, onEmail: () -> Unit = {}) {
    val account by viewModel.account.state.collectAsState()
    val google by viewModel.googleSignIn.collectAsState()
    val signIn = rememberGoogleSignIn(viewModel)
    LaunchedEffect(Unit) { viewModel.loadAccount() }
    LaunchedEffect(account.user?.id) { if (account.user != null) onDone() }
    OnboardingContent(busy = google.busy || account.busy, error = google.error, onSignIn = signIn,
        onSkip = { viewModel.skipOnboarding(); onDone() }, onEmail = onEmail,
        rememberedAccount = account.rememberedUser)
}

/** A remembered account is displayed only when supplied by the existing session or its in-memory last profile, never a fabricated email/photo. */
@Composable
fun OnboardingContent(busy: Boolean, error: String?, onSignIn: () -> Unit, onSkip: () -> Unit,
    onEmail: (() -> Unit)? = null, rememberedAccount: AccountUser? = null) {
    val c = WelcomeTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val viewport = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport)
            .padding(horizontal = WelcomeSizes.paddingH, vertical = WelcomeSizes.paddingV),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = PageSizes.onboardingLogo), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WelcomeSizes.gap)) {
                    // Preserve the original artwork; no accent tint or replacement branding.
                    Image(painterResource(R.drawable.dua_logo), "Dua logo", Modifier.size(WelcomeSizes.logo))
                    Text("Dua AI", style = WelcomeTitleStyle, color = c.text, modifier = Modifier.semantics { heading() })
                }
            }
            Column(Modifier.widthIn(max = PageSizes.onboardingActionsMaxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(WelcomeSizes.gap), horizontalAlignment = Alignment.CenterHorizontally) {
                rememberedAccount?.let { user ->
                    Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = WelcomeSizes.button),
                        shape = WelcomeSizes.pill, colors = ButtonDefaults.buttonColors(containerColor = c.action, contentColor = c.onAction)) {
                        Box(Modifier.size(WelcomeSizes.avatar)) {
                            AccountAvatar(user.email, user.displayName, user.avatarUrl, WelcomeSizes.avatar)
                            Image(painterResource(R.drawable.ic_google_g), null, Modifier.size(WelcomeSizes.badge).align(Alignment.BottomEnd))
                        }
                        Spacer(Modifier.width(WelcomeSizes.gap))
                        Column(Modifier.weight(1f)) {
                            Text("Continue with this account", style = WelcomeContinueStyle)
                            Text(user.email, style = WelcomeEmailStyle, color = c.email, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                OutlinedButton(onClick = onSignIn, enabled = !busy, shape = WelcomeSizes.pill,
                    border = BorderStroke(SettingsSizes.border, c.border),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = c.background, contentColor = c.text),
                    modifier = Modifier.fillMaxWidth().heightIn(min = WelcomeSizes.button)) {
                    if (busy) CircularProgressIndicator(Modifier.size(WelcomeSizes.googleIcon), color = c.text, strokeWidth = SettingsSizes.border)
                    else Image(painterResource(R.drawable.ic_google_g), null, Modifier.size(WelcomeSizes.googleIcon))
                    Spacer(Modifier.width(WelcomeSizes.gap))
                    Text(if (busy) "Signing in…" else "Continue with Google", style = WelcomeButtonStyle)
                }
                onEmail?.let { action ->
                    OutlinedButton(onClick = action, enabled = !busy, shape = WelcomeSizes.pill,
                        border = BorderStroke(SettingsSizes.border, c.border),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.background, contentColor = c.text),
                        modifier = Modifier.fillMaxWidth().heightIn(min = WelcomeSizes.button)) {
                        Text("Log in another way", style = WelcomeButtonStyle)
                    }
                }
                if (!error.isNullOrBlank()) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onSkip, enabled = !busy, modifier = Modifier.heightIn(min = Sizes.touchTarget)) {
                    Text("Skip for now", style = WelcomeEmailStyle, color = c.text)
                }
            }
        }
    }
}
