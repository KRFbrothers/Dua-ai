package com.dua.ai.ui.account

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.dua.ai.account.GoogleIdentity
import com.dua.ai.account.GoogleSignIn
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GoogleSignInFlowTest {
    private val clientId = "test-web-client.apps.googleusercontent.com"
    private val email = "owner@example.com"
    private fun credential() = GetCredentialResponse(GoogleIdTokenCredential.Builder()
        .setId(email).setIdToken("header.payload.signature")
        .setProfilePictureUri(Uri.parse("https://example.com/avatar.png")).build())

    @Test fun explicitButtonRequestsGoogleSignInAndHandsOffFreshRawNonceWithTheIdentity() = runBlocking {
        val identities = mutableListOf<GoogleIdentity>()
        val sentNonces = mutableListOf<String>()
        val failures = mutableListOf<String?>()
        repeat(2) {
            completeGoogleSignIn(requestIdentity = {
                GoogleSignIn.requestIdentity(clientId) { request ->
                    assertEquals(1, request.credentialOptions.size)
                    val option = request.credentialOptions.single() as GetSignInWithGoogleOption
                    assertEquals(clientId, option.serverClientId)
                    sentNonces += option.nonce!!
                    credential()
                }
            }, submitIdentity = { identities += it }, onFailure = { failures += it })
        }
        assertTrue(failures.isEmpty())
        assertEquals(2, identities.size)
        assertNotEquals(identities[0].nonce, identities[1].nonce)
        identities.forEachIndexed { index, identity ->
            assertEquals("header.payload.signature", identity.idToken)
            assertEquals("https://example.com/avatar.png", identity.photoUrl)
            assertTrue(identity.nonce.matches(Regex("[A-Za-z0-9_-]{43}")))
            assertEquals(GoogleSignIn.sha256Hex(identity.nonce), sentNonces[index])
            assertNotEquals(identity.nonce, sentNonces[index])
        }
    }

    @Test fun providerCancellationShowsRetryInsteadOfSilentlyReturningToLogin() = runBlocking {
        var exchanges = 0
        val failures = mutableListOf<String?>()
        completeGoogleSignIn(requestIdentity = { throw GetCredentialCancellationException() },
            submitIdentity = { exchanges++ }, onFailure = { failures += it })
        assertEquals(0, exchanges)
        assertEquals(listOf("Google sign-in did not finish. Tap Continue with Google to try again."), failures)
    }

    @Test fun missingCredentialShowsAnErrorWithoutLaunchingAnotherPicker() = runBlocking {
        var requests = 0; var exchanges = 0
        val failures = mutableListOf<String?>()
        completeGoogleSignIn(requestIdentity = {
            GoogleSignIn.requestIdentity(clientId) { requests++; throw NoCredentialException() }
        }, submitIdentity = { exchanges++ }, onFailure = { failures += it })
        assertEquals(1, requests)
        assertEquals(0, exchanges)
        assertTrue(failures.single()!!.contains("could not return a sign-in credential"))
    }

    @Test fun rememberedAccountMismatchNeverReachesTheSessionExchange() = runBlocking {
        var exchanges = 0
        val failures = mutableListOf<String?>()
        completeGoogleSignIn(requestIdentity = {
            GoogleSignIn.requestIdentity(clientId, "different@example.com") { credential() }
        }, submitIdentity = { exchanges++ }, onFailure = { failures += it })
        assertEquals(0, exchanges)
        assertTrue(failures.single()!!.contains("Select different@example.com"))
    }

    @Test fun screenCancellationIsRethrownWithoutShowingAProviderErrorOrExchangingTokens() = runBlocking {
        val cancelled = CancellationException("Screen left")
        var exchanges = 0
        val failures = mutableListOf<String?>()
        val failure = runCatching {
            completeGoogleSignIn(requestIdentity = { throw cancelled },
                submitIdentity = { exchanges++ }, onFailure = { failures += it })
        }.exceptionOrNull()
        assertSame(cancelled, failure)
        assertEquals(0, exchanges)
        assertEquals(listOf<String?>(null), failures)
    }

    @Test fun unexpectedCredentialAndMissingConfigurationCannotExchangeTokens() = runBlocking {
        var requests = 0; var exchanges = 0
        val failures = mutableListOf<String?>()
        for (configured in listOf(true, false)) completeGoogleSignIn(requestIdentity = {
            GoogleSignIn.requestIdentity(if (configured) clientId else "") {
                requests++; GetCredentialResponse(CustomCredential("unexpected", Bundle()))
            }
        }, submitIdentity = { exchanges++ }, onFailure = { failures += it })
        assertEquals(1, requests)
        assertEquals(0, exchanges)
        assertEquals(listOf("Unexpected Google credential.", "Google sign-in is not configured for this build."), failures)
    }
}
