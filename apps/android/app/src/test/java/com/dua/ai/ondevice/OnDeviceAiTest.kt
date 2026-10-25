package com.dua.ai.ondevice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OnDeviceAiTest {
    private fun ai(model: FakeTextModel, @Suppress("UNUSED_PARAMETER") scope: Any? = null) = OnDeviceAi(model, CoroutineScope(Dispatchers.Unconfined)).also { it.refresh() }

    @Test fun readyModelMakesACleanTitle() = runBlocking {
        val model = FakeTextModel(answer = "Title: Weekend trip ideas.")
        val ai = ai(model)
        assertTrue(ai.supported)
        assertEquals("Weekend trip ideas", ai.title("Plan my weekend", "Here are ideas"))
        assertTrue(model.prompts.single().contains("User: Plan my weekend"))
    }

    @Test fun titlesNeverDownloadOrRunUnlessReady() = runBlocking {
        for (status in listOf(OnDeviceStatus.Unsupported, OnDeviceStatus.Downloadable, OnDeviceStatus.Downloading)) {
            val model = FakeTextModel(current = status)
            assertNull(ai(model).title("a", "b"))
            assertEquals(0, model.prepares); assertTrue(model.prompts.isEmpty())
        }
    }

    @Test fun unsupportedPhoneIsNotSupportedAndPolishReturnsNull() = runBlocking {
        val model = FakeTextModel(current = OnDeviceStatus.Unsupported)
        val ai = ai(model)
        assertFalse(ai.supported)
        assertNull(ai.polish("a cat"))
        assertEquals(0, model.prepares)
    }

    @Test fun polishPreparesTheModelThenRewrites() = runBlocking {
        val model = FakeTextModel(current = OnDeviceStatus.Downloadable, answer = "A sleepy orange cat on a windowsill, warm light")
        val ai = ai(model)
        assertEquals("A sleepy orange cat on a windowsill, warm light", ai.polish("a cat"))
        assertEquals(1, model.prepares)
        assertEquals(OnDeviceStatus.Ready, ai.status.value)
    }

    @Test fun failedDownloadOrEmptyAnswerFallsBackToNull() = runBlocking {
        assertNull(ai(FakeTextModel(current = OnDeviceStatus.Downloadable, prepareResult = false)).polish("a cat"))
        assertNull(ai(FakeTextModel(answer = null)).polish("a cat"))
        assertNull(ai(FakeTextModel(answer = null)).title("a", "b"))
    }
}
