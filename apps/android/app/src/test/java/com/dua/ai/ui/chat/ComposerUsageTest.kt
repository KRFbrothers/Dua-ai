package com.dua.ai.ui.chat

import com.dua.ai.imagine.ImagineUsage
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerUsageTest {
    private val zone = ZoneOffset.UTC
    private val resetAt = Instant.parse("2026-10-30T23:48:00Z").epochSecond

    @Test fun clockLabelsUseTwelveHourLocalTime() {
        assertEquals("11:48 pm", composerResetLabel(resetAt, zone))
        assertEquals("12:05 am", composerResetLabel(Instant.parse("2026-10-30T00:05:00Z").epochSecond, zone))
        assertNull(composerResetLabel(0, zone))
    }

    @Test fun quotaErrorIsShownAsWritten() {
        val message = "Daily request limit reached. It resets at midnight UTC."
        val notice = composerUsage(message, ImagineUsage(1, 20, 0, 60, resetAt), videoEnabled = true, zone = zone)
        assertEquals(message, notice?.message)
        assertEquals("See usage", notice?.action)
    }

    @Test fun imageAllowanceNamesItsResetBeforeItIsExhausted() {
        val notice = composerUsage(null, ImagineUsage(3, 20, 0, 60, resetAt), videoEnabled = true, zone = zone)
        assertEquals("Your image limit resets at 11:48 pm", notice?.message)
    }

    @Test fun exhaustedImageLimitMentionsTheReset() {
        val notice = composerUsage(null, ImagineUsage(20, 20, 10, 60, resetAt), videoEnabled = true, zone = zone)
        assertEquals("You've reached today's image limit. It resets at 11:48 pm.", notice?.message)
    }

    @Test fun exhaustedVideoLimitIsUsedWhenImagesRemainAvailable() {
        val notice = composerUsage(null, ImagineUsage(1, 20, 60, 60, resetAt), videoEnabled = false, zone = zone)
        assertEquals("Your image limit resets at 11:48 pm", notice?.message)
        val video = composerUsage(null, ImagineUsage(0, 0, 60, 60, resetAt), videoEnabled = true, zone = zone)
        assertEquals("You've reached today's video limit. It resets at 11:48 pm.", video?.message)
    }

    @Test fun ordinaryErrorsAndMissingUsageStayOutOfTheBanner() {
        assertNull(composerUsage("Sign in and retry.", null, videoEnabled = false, zone = zone))
        assertNull(composerUsage(null, null, videoEnabled = true, zone = zone))
        assertNull(composerUsage(null, ImagineUsage(0, 0, 0, 0, 0), videoEnabled = false, zone = zone))
    }
}
