package com.dua.ai.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.dua.ai.account.GoogleSignIn
import com.dua.ai.account.GoogleIdentity
import com.dua.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Starts native Google sign-in: the account picker runs in the UI scope (it needs the Activity), the token exchange in the view model. */
@Composable
fun rememberGoogleSignIn(viewModel: ChatViewModel, emailHint: String? = null): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        if (!viewModel.googleSignIn.value.busy) {
            viewModel.googleSignInStarted()
            scope.launch {
                completeGoogleSignIn(
                    requestIdentity = { GoogleSignIn.requestIdToken(context, emailHint) },
                    submitIdentity = { identity -> viewModel.signInWithGoogle(identity.idToken, identity.nonce, identity.photoUrl, expectedEmail = emailHint) },
                    onFailure = viewModel::googleSignInFailed
                )
            }
        }
    }
}

/** Provider cancellation is a visible incomplete sign-in; leaving the screen still cancels normally. */
internal suspend fun completeGoogleSignIn(requestIdentity: suspend () -> GoogleIdentity,
    submitIdentity: (GoogleIdentity) -> Unit, onFailure: (String?) -> Unit) {
    try { submitIdentity(requestIdentity()) }
    catch (cancelled: CancellationException) { onFailure(null); throw cancelled }
    catch (failure: Exception) { onFailure(GoogleSignIn.friendlyMessage(failure)) }
}
