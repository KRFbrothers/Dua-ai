package com.dua.ai.account

import org.junit.Assert.*
import org.junit.Test

class GoogleAccountHintTest {
    @Test fun quickContinueRejectsADifferentSelectionAndMatchesEmailCaseInsensitively() {
        assertFalse(GoogleSignIn.matchesAccountHint("different@example.com", "owner@example.com"))
        assertFalse(GoogleSignIn.matchesAccountHint("", "owner@example.com"))
        assertTrue(GoogleSignIn.matchesAccountHint("OWNER@example.com", " owner@example.com "))
    }
    @Test fun genericGoogleSignInStillAllowsAccountSelection() {
        assertTrue(GoogleSignIn.matchesAccountHint("another@example.com", null))
    }
}
