package com.dua.ai.backup.drive

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import com.google.android.gms.auth.api.identity.AuthorizationResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveAuthorizationTest {
    @Test fun authorizationIsBoundToDisplayedAccountAndOnlyRequestsThePrivateAppFolder() {
        val auth = DriveAuthorization(RuntimeEnvironment.getApplication())
        val request = auth.requestFor("owner@example.test")
        assertEquals("owner@example.test", request.account!!.name)
        assertEquals(listOf(DriveAuthorization.APP_DATA_SCOPE), request.requestedScopes.map { it.scopeUri })
        assertFalse(request.isOfflineAccessRequested)
    }
    @Test fun missingScopeOrTokenNeverLooksLikeAWorkingDriveConnection() {
        val auth = DriveAuthorization(RuntimeEnvironment.getApplication())
        fun result(token: String?, scopes: List<String>) = AuthorizationResult(null, token, null, scopes, null, null, Bundle())
        assertThrows(DriveSetupRequired::class.java) { auth.access(result("token", emptyList())) }
        assertThrows(DriveSetupRequired::class.java) { auth.access(result(null, listOf(DriveAuthorization.APP_DATA_SCOPE))) }
        assertEquals(DriveAccess.Token("token"), auth.access(result("token", listOf(DriveAuthorization.APP_DATA_SCOPE))))
    }
    @Test fun requiredConsentIsReturnedToTheForegroundUiInsteadOfBeingSilentlyGranted() {
        val app = RuntimeEnvironment.getApplication()
        val pending = PendingIntent.getActivity(app, 2, Intent("test.consent").setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)
        val result = AuthorizationResult(null, null, null, emptyList(), null, pending, Bundle())
        assertTrue(DriveAuthorization(app).access(result) is DriveAccess.Consent)
    }
}
