package com.dua.ai.ondevice

import org.junit.Assert.*
import org.junit.Test

class OnDeviceTextTest {
    @Test fun titleIsOneCleanLineWithoutQuotesLabelsOrFullStop() {
        assertEquals("Trip plan for Goa", OnDeviceText.cleanTitle("Title: \"Trip plan for Goa.\"\nExtra line"))
        assertEquals("Python list sorting", OnDeviceText.cleanTitle("  **Python   list sorting**  "))
        assertEquals("Chai recipe", OnDeviceText.cleanTitle("“Chai recipe”"))
    }

    @Test fun badTitlesFallBackToNull() {
        assertNull(OnDeviceText.cleanTitle(null))
        assertNull(OnDeviceText.cleanTitle("   \n  "))
        assertNull(OnDeviceText.cleanTitle("\"\""))
        assertNull(OnDeviceText.cleanTitle("A"))
    }

    @Test fun longTitlesAreCutAtAWordBoundary() {
        val title = OnDeviceText.cleanTitle(List(30) { "word$it" }.joinToString(" "))!!
        assertTrue(title.length <= OnDeviceText.TITLE_MAX)
        assertFalse(title.endsWith(" "))
        assertTrue(title.split(" ").all { it.startsWith("word") })
    }

    @Test fun promptsCapChatTextAndAskForPlainAnswers() {
        val prompt = OnDeviceText.titlePrompt("Q".repeat(5000), "Z".repeat(5000))
        assertEquals(OnDeviceText.TITLE_INPUT, prompt.count { it == 'Q' })
        assertEquals(OnDeviceText.TITLE_INPUT, prompt.count { it == 'Z' })
        assertTrue(OnDeviceText.polishPrompt("a red fox").contains("Idea: a red fox"))
    }

    @Test fun polishJoinsLinesStripsLabelAndRejectsEchoes() {
        assertEquals("A red fox in snowy woods, soft morning light", OnDeviceText.cleanPolish("Prompt: \"A red fox in snowy woods,\nsoft morning light\"", "red fox"))
        assertNull(OnDeviceText.cleanPolish("Red fox", "red fox"))
        assertNull(OnDeviceText.cleanPolish("", "red fox"))
        assertTrue(OnDeviceText.cleanPolish("word ".repeat(400), "x")!!.length <= OnDeviceText.POLISH_MAX)
    }
}
