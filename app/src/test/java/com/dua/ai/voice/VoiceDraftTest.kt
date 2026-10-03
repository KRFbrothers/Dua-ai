package com.dua.ai.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceDraftTest {
    @Test fun speechAppendsWithoutReplacingExistingDraft() {
        assertEquals("Existing draft\nनमस्ते Dua", VoiceDraft.merge("Existing draft", "  नमस्ते Dua  "))
        assertEquals("Hello", VoiceDraft.merge("", " Hello "))
    }
    @Test fun blankAndOversizeResultsAreRejectedInsteadOfTruncated() {
        assertThrows(IllegalArgumentException::class.java) { VoiceDraft.merge("keep", "  ") }
        assertThrows(IllegalArgumentException::class.java) { VoiceDraft.merge("x".repeat(7999), "Hi") }
        assertEquals(8000, VoiceDraft.merge("", "x".repeat(8000)).length)
    }
}
