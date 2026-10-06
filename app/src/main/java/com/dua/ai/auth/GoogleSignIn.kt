package com.dua.ai.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.dua.ai.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

data class GoogleIdentity(val idToken: String, val nonce: String)

/** Native Google Sign-In. The backend verifies the ID token with Supabase and the app stores only the Dua session (encrypted by AccountSessionStore). */
object GoogleSignIn {
    fun newNonce(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }

    suspend fun requestIdToken(context: Context): GoogleIdentity {
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Google sign-in is not configured for this build." }
        val nonce = newNonce()
        val option = GetGoogleIdOption.Builder().setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).setFilterByAuthorizedAccounts(false).setNonce(nonce).build()
        val result = CredentialManager.create(context).getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Unexpected Google credential." }
        return GoogleIdentity(GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce)
    }

    fun friendlyMessage(failure: Throwable): String = when (failure) {
        is GetCredentialCancellationException -> "Google sign-in was cancelled."
        is NoCredentialException -> "No Google account is available on this device."
        is GetCredentialException -> "Google sign-in failed. Check your connection and try again."
        else -> failure.message ?: "Google sign-in failed. Try again."
    }
}
