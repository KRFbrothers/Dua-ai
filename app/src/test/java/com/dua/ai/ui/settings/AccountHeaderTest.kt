package com.dua.ai.ui.settings

import org.junit.Assert.*
import org.junit.Test

class AccountHeaderTest {
    @Test fun signedInUsesRealNameEmailAndInitial() {
        val h = accountHeader("Asha Rao", "asha@example.com")
        assertEquals("Asha Rao", h.name); assertEquals("asha@example.com", h.email); assertEquals("A", h.initial); assertTrue(h.signedIn)
    }
    @Test fun blankNameFallsBackToEmail() {
        val h = accountHeader("  ", "zed@example.com")
        assertEquals("zed", h.name); assertEquals("Z", h.initial); assertEquals("zed@example.com", h.email)
    }
    @Test fun signedOutShowsNotSignedInAndNoInitial() {
        val h = accountHeader(null, null)
        assertEquals("Not signed in", h.name); assertEquals("Not signed in", h.email); assertNull(h.initial); assertFalse(h.signedIn)
    }
    @Test fun neverHardcodesOwnerAccount() {
        val h = accountHeader(null, null)
        assertFalse(h.name.contains("Fareed")); assertFalse(h.email.contains("fareedkrf"))
    }
}
