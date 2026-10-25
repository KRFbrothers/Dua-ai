package com.dua.ai.account

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarUrlTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    @Test fun onlyHttpsPhotosAreKept() {
        assertEquals("https://lh3.googleusercontent.com/a/x", safeAvatarUrl(" https://lh3.googleusercontent.com/a/x "))
        assertEquals("", safeAvatarUrl("http://example.com/x.png"))
        assertEquals("", safeAvatarUrl("javascript:alert(1)"))
        assertEquals("", safeAvatarUrl(null))
    }
    @Test fun codecRoundTripsPhotoAndOlderSessionsDecodeWithout() {
        val session = AccountSession(AccountUser(id, "a@b.co", "A", "https://lh3.googleusercontent.com/a/x"), "acc", "ref", 1L)
        assertEquals(session, AccountSessionCodec.decode(AccountSessionCodec.encode(session)))
        val old = JSONObject().put("id", id).put("email", "a@b.co").put("displayName", "A")
        assertEquals("", AccountSessionCodec.user(old).avatarUrl)
    }
    @Test fun profileLoadKeepsGooglePhotoForSameAccount() {
        val prior = AccountUser(id, "a@b.co", "A", "https://lh3.googleusercontent.com/a/x")
        assertEquals(prior.avatarUrl, AccountUser(id, "a@b.co", "A").withAvatarFallback(prior).avatarUrl)
        assertEquals("", AccountUser("00000000-0000-0000-0000-000000000002", "c@d.co", "C").withAvatarFallback(prior).avatarUrl)
    }
}
