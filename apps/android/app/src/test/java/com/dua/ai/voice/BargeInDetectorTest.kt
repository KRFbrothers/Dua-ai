package com.dua.ai.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class BargeInDetectorTest {
    private val silence = ShortArray(640)
    private val voice = ShortArray(640) { (sin(it * 2 * Math.PI * 220 / 16000) * 12000).toInt().toShort() }
    @Test fun sustainedSpeechTriggersAfterCalibrationAndShortOnset() {
        val detector = BargeInDetector()
        repeat(5) { assertFalse(detector.accept(silence, silence.size)) }
        assertFalse(detector.accept(voice, voice.size))
        assertFalse(detector.accept(voice, voice.size))
        assertTrue(detector.accept(voice, voice.size))
    }
    @Test fun silenceAndSingleClicksCannotInterrupt() {
        val detector = BargeInDetector()
        repeat(10) { assertFalse(detector.accept(silence, silence.size)) }
        assertFalse(detector.accept(voice, voice.size))
        repeat(20) { assertFalse(detector.accept(silence, silence.size)) }
    }
    @Test fun steadyRoomNoiseIsNotMistakenForNewSpeech() {
        val detector = BargeInDetector()
        val fan = ShortArray(640) { (sin(it * .8) * 1800).toInt().toShort() }
        repeat(50) { assertFalse(detector.accept(fan, fan.size)) }
        assertFalse(detector.accept(voice, voice.size))
        assertFalse(detector.accept(voice, voice.size))
        assertTrue(detector.accept(voice, voice.size))
    }
}
