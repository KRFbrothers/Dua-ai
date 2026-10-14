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
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Google sign-in is not configured for this build." }
        val rawNonce = newNonce()
        val hashedNonce = sha256Hex(rawNonce)
        val manager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder().setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).setFilterByAuthorizedAccounts(false).setNonce(hashedNonce).build()
        val result = try {
            manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build())
        } catch (_: NoCredentialException) {
            val signInOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashedNonce).build()
            manager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(signInOption).build())
        }
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
        is GetCredentialCancellationException -> "Google sign-in was cancelled."
        is NoCredentialException -> "No Google account is available on this device."
        is GetCredentialException -> "Google sign-in failed. Check your connection and try again."
        else -> failure.message ?: "Google sign-in failed. Try again."
    }
}
