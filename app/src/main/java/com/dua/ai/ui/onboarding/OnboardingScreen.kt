package com.dua.ai.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.dua.ai.R
import com.dua.ai.ui.auth.GoogleSignInButton
import com.dua.ai.ui.auth.rememberGoogleSignIn
import com.dua.ai.ui.chat.SheetTextButton
import com.dua.ai.ui.theme.DuaPageTheme
import com.dua.ai.ui.theme.PageBodyStyle
import com.dua.ai.ui.theme.PageHeadlineStyle
import com.dua.ai.ui.theme.PageSizes
import com.dua.ai.ui.theme.PageTheme
import com.dua.ai.ui.theme.SheetButtonStyle
import com.dua.ai.ui.theme.SheetShapes
import com.dua.ai.ui.theme.Spacing
import com.dua.ai.viewmodel.ChatViewModel

/** Welcome screen shown at launch while signed out: one Google sign-in button and a quiet "Skip for now". */
@Composable
fun OnboardingScreen(viewModel: ChatViewModel, onDone: () -> Unit, onEmail: () -> Unit = {}) {
    val account by viewModel.account.state.collectAsState()
    val google by viewModel.googleSignIn.collectAsState()
    val signIn = rememberGoogleSignIn(viewModel)
    LaunchedEffect(Unit) { viewModel.loadAccount() }
    LaunchedEffect(account.user?.id) { if (account.user != null) onDone() }
    OnboardingContent(busy = google.busy, error = google.error, onSignIn = signIn, onSkip = { viewModel.skipOnboarding(); onDone() }, onEmail = if (account.emailAuthConfigured && !account.busy) onEmail else null)
}

@Composable
fun OnboardingContent(busy: Boolean, error: String?, onSignIn: () -> Unit, onSkip: () -> Unit, onEmail: (() -> Unit)? = null) = DuaPageTheme {
    val p = PageTheme.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(p.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val viewport = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport)
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Spacer(Modifier.height(Spacing.lg))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.dua_logo), contentDescription = "Dua logo", modifier = Modifier.height(PageSizes.onboardingLogo))
                Spacer(Modifier.height(Spacing.xxl))
                Text("Welcome to Dua", style = PageHeadlineStyle, color = p.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.sm))
                Text("Your personal AI for chat, voice and ideas.", style = PageBodyStyle, color = p.subText, textAlign = TextAlign.Center)
            }
            Column(Modifier.widthIn(max = PageSizes.onboardingActionsMaxWidth).fillMaxWidth().padding(top = Spacing.huge), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                // Google sign-in keeps its own branding (GoogleSignInButton is unchanged).
                GoogleSignInButton(busy = busy, error = error, onClick = onSignIn)
                onEmail?.let { SheetTextButton("Continue with email", onClick = it, enabled = !busy) }
                TextButton(onClick = onSkip, enabled = !busy, shape = SheetShapes.button, modifier = Modifier.heightIn(min = PageSizes.touch),
                    colors = ButtonDefaults.textButtonColors(contentColor = p.subText)) {
                    Text("Skip for now", style = SheetButtonStyle)
                }
            }
        }
    }
}
