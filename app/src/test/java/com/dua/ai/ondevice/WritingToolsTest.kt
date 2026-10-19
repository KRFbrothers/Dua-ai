package com.dua.ai.ondevice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WritingToolsTest {
    @Test fun promptsAskForTheTaskAndCapInput() {
        val summary = WritingPrompts.onDevice(WritingTask.Summarize, "Q".repeat(9000))
        assertTrue(summary.contains("3 short bullet points"))
        assertEquals(WritingPrompts.INPUT_MAX, summary.count { it == 'Q' })
        val rewrite = WritingPrompts.onDevice(WritingTask.Rewrite(RewriteStyle.Professional), "hey send file")
        assertTrue(rewrite.contains(RewriteStyle.Professional.instruction) && rewrite.contains("hey send file"))
    }

    @Test fun serverFallbackIsAPlainChatRequestWithTheText() {
        assertEquals("Summarize this in 3 short bullet points:\n\nLong reply", WritingPrompts.server(WritingTask.Summarize, "  Long reply "))
        assertTrue(WritingPrompts.server(WritingTask.Rewrite(RewriteStyle.Shorter), "abc").endsWith("\n\nabc"))
    }

    @Test fun cleanKeepsLinesDropsLabelsAndEchoes() {
        assertEquals("- One\n- Two", WritingPrompts.clean("Summary:\n- One\n- Two", "x"))
        assertEquals("Hello there", WritingPrompts.clean("\"Hello there\"", "hi"))
        assertEquals("a\n\nb", WritingPrompts.clean("a\n\n\n\nb", "x"))
        assertNull(WritingPrompts.clean("  ", "x"))
        assertNull(WritingPrompts.clean("Same text", "same text"))
        assertTrue(WritingPrompts.clean("word ".repeat(2000), "x")!!.length <= WritingPrompts.OUTPUT_MAX + 1)
    }

    @Test fun writeRunsOnlyWhenSupportedAndPreparesFirst() = runBlocking {
        val model = FakeTextModel(current = OnDeviceStatus.Downloadable, answer = "- Point")
        val ai = OnDeviceAi(model, CoroutineScope(Dispatchers.Unconfined)).also { it.refresh() }
        assertEquals("- Point", ai.write(WritingTask.Summarize, "long text"))
        assertEquals(1, model.prepares)
        val none = OnDeviceAi(FakeTextModel(current = OnDeviceStatus.Unsupported), CoroutineScope(Dispatchers.Unconfined)).also { it.refresh() }
        assertNull(none.write(WritingTask.Summarize, "long text"))
    }
}
