package com.dua.ai.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPillTest {
    @Test fun defaultModelShowsAuto() = assertEquals("Auto", modelPillLabel("Dua"))

    @Test fun otherModelsShowTheirName() {
        listOf("ChatGPT", "Gemini", "Claude", "Grok", "Kimi").forEach { assertEquals(it, modelPillLabel(it)) }
    }
}
