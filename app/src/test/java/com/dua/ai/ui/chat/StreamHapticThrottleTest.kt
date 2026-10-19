package com.dua.ai.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamHapticThrottleTest {
    @Test fun burstsAreLimitedWithoutDelayingTextOrReplacingThePreferenceAwareCallback() {
        var time = 0L
        var allowed = false
        var vibrations = 0
        val throttle = StreamHapticThrottle { time }
        val callback = { if (allowed) vibrations++ }
        throttle.tick(callback)
        allowed = true
        for (next in listOf(1L, 24L, 39L)) { time = next; throttle.tick(callback) }
        assertEquals(0, vibrations)
        time = 40L; throttle.tick(callback)
        time = 80L; throttle.tick(callback)
        assertEquals(2, vibrations)
        allowed = false
        time = 120L; throttle.tick(callback)
        assertEquals(2, vibrations)
    }
}
