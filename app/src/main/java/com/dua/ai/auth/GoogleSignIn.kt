package com.dua.ai.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.dua.ai.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

data class GoogleIdentity(val idToken: String, val nonce: String, val photoUrl: String = "")

/** Native Google Sign-In. The backend verifies the ID token with Supabase and the app stores only the Dua session (encrypted by AccountSessionStore). */
object GoogleSignIn {
    fun newNonce(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }

    /** Lowercase hex SHA-256. Google embeds this in the ID token; Supabase hashes the raw nonce and compares. */
    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    suspend fun requestIdToken(context: Context, emailHint: String? = null): GoogleIdentity {
        val manager = CredentialManager.create(context)
        return requestIdentity(BuildConfig.GOOGLE_WEB_CLIENT_ID, emailHint) { request ->
            manager.getCredential(context, request)
        }
    }

    /** Explicit user-tapped buttons use the Google button flow, including account reauthentication. */
    internal suspend fun requestIdentity(clientId: String, emailHint: String? = null,
        requestCredential: suspend (GetCredentialRequest) -> GetCredentialResponse): GoogleIdentity {
        check(clientId.isNotBlank()) { "Google sign-in is not configured for this build." }
        val rawNonce = newNonce()
        val hashedNonce = sha256Hex(rawNonce)
        val signInOption = GetSignInWithGoogleOption.Builder(clientId).setNonce(hashedNonce).build()
        val result = requestCredential(GetCredentialRequest.Builder().addCredentialOption(signInOption).build())
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Unexpected Google credential." }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        // googleid 1.1.1 has no email-hint setter. Reject a different selection before exchanging its token.
        check(matchesAccountHint(google.id, emailHint)) { "Select $emailHint to continue, or use Continue with Google for another account." }
        return GoogleIdentity(google.idToken, rawNonce, google.profilePictureUri?.toString().orEmpty())
    }

    internal fun matchesAccountHint(selectedEmail: String, emailHint: String?): Boolean =
        emailHint == null || selectedEmail.trim().equals(emailHint.trim(), ignoreCase = true)

    fun friendlyMessage(failure: Throwable): String = when (failure) {
        is GetCredentialCancellationException -> "Google sign-in did not finish. Tap Continue with Google to try again."
        is NoCredentialException -> "Google could not return a sign-in credential. Add or sign in to your Google account on this device and try again."
        is GetCredentialException -> "Google sign-in failed. Check your connection and try again."
        else -> failure.message ?: "Google sign-in failed. Try again."
    }
}
