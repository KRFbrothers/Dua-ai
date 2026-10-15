package com.dua.ai.model

import com.dua.ai.network.ChatStreamParser
import com.dua.ai.network.ReplyEvent
import com.dua.ai.network.StreamAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProgressTest {
    @Test fun newStepFinishesEarlierOnesAndNeverGoesBack() {
        var steps = AgentProgress.apply(emptyList(), "plan", "Planning…", done = false)
        steps = AgentProgress.apply(steps, "search", "Searching the web…", done = false)
        assertEquals(listOf(AgentStep("plan", "Planning…", true), AgentStep("search", "Searching the web…", false)), steps)
        steps = AgentProgress.apply(steps, "plan", "Planning…", done = false)
        assertTrue(steps[0].done)
        assertEquals(steps, AgentProgress.apply(steps, "BAD id", "x", false))
        assertEquals(steps, AgentProgress.apply(steps, "read", "   ", false))
        assertTrue(AgentProgress.finish(steps).all { it.done })
    }

    @Test fun stepsAreCapped() {
        var steps = emptyList<AgentStep>()
        repeat(30) { steps = AgentProgress.apply(steps, "s" .plus("abcdefghijklmnopqrstuvwxyz"[it % 26]).repeat(1) + "_".repeat(it / 26), "Step $it", false) }
        assertTrue(steps.size <= AgentProgress.MAX_STEPS)
        assertEquals(AgentProgress.MAX_LABEL, AgentProgress.apply(emptyList(), "plan", "x".repeat(200), false)[0].label.length)
    }

    @Test fun parserReadsProgressAndIgnoresBadOnes() {
        assertEquals(ReplyEvent.Progress("search", "Searching the web…", false),
            ChatStreamParser.decode("""{"type":"progress","step":"search","label":"Searching the web…","state":"active"}"""))
        assertEquals(ReplyEvent.Progress("pr", "Opening PR…", true),
            ChatStreamParser.decode("""{"type":"progress","step":"pr","label":"Opening PR…","state":"done"}"""))
        assertNull(ChatStreamParser.decode("""{"type":"progress","step":"../x","label":"a"}"""))
        assertNull(ChatStreamParser.decode("""{"type":"progress","step":"plan","label":""}"""))
    }

    @Test fun accumulatorTracksStepsAndFinishesThemOnDone() {
        val acc = StreamAccumulator(0L) { 1000L }
        acc.apply(ReplyEvent.Progress("plan", "Planning…", false))
        acc.apply(ReplyEvent.Thinking("**Step 1 - Plan**"))
        acc.apply(ReplyEvent.Progress("search", "Searching the web…", false))
        assertEquals(listOf(true, false), acc.snapshot().progress.map { it.done })
        acc.apply(ReplyEvent.Done(false))
        assertTrue(acc.snapshot().progress.all { it.done })
        assertEquals("**Step 1 - Plan**", acc.snapshot().thinking)
    }
}
