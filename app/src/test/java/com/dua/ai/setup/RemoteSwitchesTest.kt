package com.dua.ai.setup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteSwitchesTest {
    @Test fun olderBackendsAndMissingBlocksMeanEverythingOn() {
        assertEquals(RemoteSwitches(), RemoteSwitches.fromConfig(JSONObject("""{"providers":[]}""")))
        assertEquals(RemoteSwitches(), RemoteSwitches.fromConfig(null))
        assertEquals(RemoteSwitches(), RemoteSwitches.fromConfig(JSONObject("""{"switches":{"agent":false,"imagine":false,"voice":false,"maintenance":{"enabled":false}}}""")))
    }

    @Test fun readsEverySwitchAndCleansTheMessage() {
        val s = RemoteSwitches.fromConfig(JSONObject("""{"switches":{"agent":true,"imagine":true,"voice":true,"maintenance":{"enabled":true,"message":" Back\nat 6 pm "}}}"""))
        assertTrue(s.agent && s.imagine && s.voice && s.maintenance)
        assertEquals("Back at 6 pm", s.message)
    }

    @Test fun maintenanceWithoutMessageUsesTheDefault() {
        val s = RemoteSwitches.parse(JSONObject("""{"maintenance":{"enabled":true}}"""))
        assertEquals(RemoteSwitches.DEFAULT_MAINTENANCE_MESSAGE, s.message)
        assertFalse(s.agent)
    }

    @Test fun cacheRoundTripsAndExpires() {
        val value = RemoteSwitches(agent = true, maintenance = true, maintenanceMessage = "Soon")
        assertEquals(value, RemoteSwitches.fromCache(value.toJson(), savedAt = 1_000, now = 2_000))
        assertEquals(RemoteSwitches(), RemoteSwitches.fromCache(value.toJson(), savedAt = 0, now = RemoteSwitches.CACHE_MAX_AGE_MS + 1))
        assertEquals(RemoteSwitches(), RemoteSwitches.fromCache("not json", 1_000, 2_000))
        assertEquals(RemoteSwitches(), RemoteSwitches.fromCache(null, 1_000, 2_000))
        // A clock that moved backwards never keeps an old value.
        assertEquals(RemoteSwitches(), RemoteSwitches.fromCache(value.toJson(), savedAt = 5_000, now = 1_000))
    }
}
