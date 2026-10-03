package com.dua.ai.voice

import org.junit.Assert.*
import org.junit.Test

class HandsFreeSessionTest {
    private class Fake : HandsFreeSession.Actions {
        var listens = 0; var sends = 0; var speaks = 0; var cancels = 0
        override fun render(phase: String) {}
        override fun listen() { listens++ }
        override fun send(text: String, token: Int) { sends++ }
        override fun speak(text: String) { speaks++ }
        override fun cancel() { cancels++ }
        override fun notice(text: String) {}
    }
    @Test fun oneTurnAtATimeAndStoppedRepliesCannotRestartSpeech() {
        val fake = Fake(); val session = HandsFreeSession(fake)
        session.start(); session.start(); assertEquals(1, fake.listens)
        session.transcript("Hi"); val old = session.generation
        session.transcript("Duplicate"); assertEquals(1, fake.sends)
        session.stop(); session.start(); session.reply("Old", old)
        assertEquals("listening", session.phase); assertEquals(0, fake.speaks)
        session.transcript("Again"); session.reply("New", session.generation)
        assertEquals("speaking", session.phase)
        session.spoken(); session.spoken(); assertEquals(3, fake.listens)
    }
    @Test fun emptyOrOversizedTranscriptStopsWithoutSend() {
        for (text in listOf("", "x".repeat(8001))) { val fake = Fake(); val session = HandsFreeSession(fake); session.start(); session.transcript(text); assertEquals("idle", session.phase); assertEquals(0, fake.sends) }
    }
}
