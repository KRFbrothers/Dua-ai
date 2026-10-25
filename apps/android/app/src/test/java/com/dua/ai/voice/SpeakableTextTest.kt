package com.dua.ai.voice

import org.junit.Assert.*
import org.junit.Test

class SpeakableTextTest {
    @Test fun markdownCodeLinksAndCitationsAreNotReadAloud() {
        val reply = """
            ## Summary
            **Dua** is _ready_ [1]. See [the docs](https://example.com/docs) or https://example.com.
            - First point
            1. Second point
            > A quote
            ```kotlin
            val secret = 42
            ```
            Use `npm test` now.

            Sources:
            [1] Example — https://example.com
        """.trimIndent()
        val spoken = SpeakableText.clean(reply)
        assertEquals("Summary\nDua is ready. See the docs or\nFirst point\nSecond point\nA quote\n(code omitted)\nUse npm test now.", spoken)
        for (hidden in listOf("```", "https://", "[1]", "**", "##", "secret", "Sources")) assertFalse(hidden, spoken.contains(hidden))
    }

    @Test fun tablesBecomeReadableRowsAndScriptsAreKept() {
        val spoken = SpeakableText.clean("| Name | City |\n|---|---|\n| Fareed | Delhi |\n\nनमस्ते *दोस्त*। آپ کیسے ہیں؟")
        assertEquals("Name, City\nFareed, Delhi\nनमस्ते दोस्त। آپ کیسے ہیں؟", spoken)
    }

    @Test fun chunksStartSmallStayUnderTheProviderLimitAndBreakAtSentences() {
        val sentence = "This is a sentence that is about sixty characters long ok. "
        val text = sentence.repeat(40).trim()
        val chunks = TtsChunker.split(text, maxCharacters = 1000)
        assertTrue(chunks.first().length <= 220)
        assertTrue(chunks.drop(1).all { it.length <= 600 })
        assertTrue(chunks.all { it.endsWith(".") })
        assertEquals(text.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
        assertTrue(TtsChunker.split(text, maxCharacters = 150).all { it.length <= 150 })
    }

    @Test fun chunkerSplitsHindiAndUrduSentenceMarksAndWordsWithoutBoundaries() {
        val hindi = "यह एक वाक्य है। ".repeat(30).trim()
        assertTrue(TtsChunker.split(hindi, 1000).all { it.endsWith("।") })
        val urdu = "یہ ایک جملہ ہے۔ ".repeat(30).trim()
        assertTrue(TtsChunker.split(urdu, 1000).all { it.endsWith("۔") })
        val noSpaces = "x".repeat(500)
        assertEquals(listOf(220, 280), TtsChunker.split(noSpaces, 1000).map { it.length })
    }

    @Test fun emptyAndOversizedRepliesAreRejected() {
        assertNotNull(runCatching { TtsChunker.split("  ", 1000) }.exceptionOrNull())
        assertNotNull(runCatching { TtsChunker.split("x ".repeat(7000), 1000) }.exceptionOrNull())
    }
}
