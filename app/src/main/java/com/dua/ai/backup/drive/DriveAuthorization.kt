package com.dua.ai.backup.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface DriveAccess {
    data class Token(val value: String) : DriveAccess { override fun toString() = "DriveAccess.Token(redacted)" }
    data class Consent(val intent: PendingIntent) : DriveAccess
}

/** Separate from Dua sign-in; grants only the hidden, app-owned Drive folder. No web client secret. */
class DriveAuthorization(private val context: Context) {
    suspend fun request(email: String): DriveAccess = suspendCancellableCoroutine { continuation ->
        val request = requestFor(email)
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result -> if (continuation.isActive) continuation.resumeWith(runCatching { access(result) }) }
            .addOnFailureListener { failure -> if (continuation.isActive) continuation.resumeWith(Result.failure(friendly(failure))) }
    }

    fun result(intent: Intent?): DriveAccess = try {
        access(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent))
    } catch (failure: Exception) { throw friendly(failure) }

    fun invalidate(token: String) {
        // Google Play services owns its token cache. Rejected tokens must not survive a reconnect.
        runCatching { Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()) }
    }

    internal fun requestFor(email: String): AuthorizationRequest = AuthorizationRequest.builder().setAccount(Account(email, "com.google"))
        .setRequestedScopes(listOf(Scope(APP_DATA_SCOPE))).build()
    internal fun access(result: AuthorizationResult): DriveAccess {
        if (result.hasResolution()) return DriveAccess.Consent(result.pendingIntent ?: throw DriveSetupRequired())
        if (APP_DATA_SCOPE !in result.grantedScopes || result.accessToken.isNullOrBlank()) throw DriveSetupRequired()
        return DriveAccess.Token(result.accessToken!!)
    }

    private fun friendly(failure: Exception): Exception = when ((failure as? ApiException)?.statusCode) {
        10 -> DriveSetupRequired("Drive authorization needs setup. Enable Google Drive API and check the Android OAuth package and signing SHA-1 in Google Cloud.")
        7 -> DriveFailure("Drive authorization could not connect. Check your network and retry.", retryable = true)
        16 -> DriveSetupRequired("Drive permission was cancelled. Existing backups were kept.")
        else -> DriveSetupRequired("Google Drive permission is unavailable. Check Google Play services, connect Drive and try again.")
    }
    companion object { const val APP_DATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata" }
}

open class DriveFailure(message: String, val retryable: Boolean = false) : Exception(message)
class DriveSetupRequired(message: String = "Open Storage and connect Google Drive again to grant backup permission.") : DriveFailure(message)
