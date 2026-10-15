package com.dua.ai.model

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.chat.ModelPickerSheet
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class FreeModelsTest {
    @get:Rule val compose = createComposeRule()
    private val gemma = FreeModel("google/gemma-4-31b-it:free", "Gemma 4 31B")

    @Test fun engineListIsValidatedAndOptional() {
        val caps = DuaApiClient.parseEngine("""{"agent":true,"freeModels":[{"id":"google/gemma-4-31b-it:free","name":"Gemma 4 31B"},
            {"id":"openai/gpt-5","name":"Paid"},{"id":"x/y:free","name":""},{"id":"google/gemma-4-31b-it:free","name":"dup"}]}""")
        assertTrue(caps.agent); assertEquals(listOf(gemma), caps.freeModels)
        assertEquals(emptyList<FreeModel>(), DuaApiClient.parseEngine("""{"agent":true}""").freeModels)
        assertFalse(DuaApiClient.parseEngine("<html>").agent)
    }

    @Test fun requestSendsFreeModelOnlyForPlainChatOnCloudRun() {
        val history = listOf(ChatMessage(text = "hi", isUser = true))
        val cloud = DuaApiClient("https://engine.example/", apiBaseUrl = "https://vercel.example/")
        fun body(api: DuaApiClient, web: String? = null, agent: Boolean = false, stream: Boolean = true) = JSONObject(Buffer().also {
            api.buildRequest("Dua", history, emptyList(), "", null, web, false, stream, agent = agent, freeModel = gemma.id).body!!.writeTo(it) }.readUtf8())
        assertEquals(gemma.id, body(cloud).getString("openrouter"))
        assertEquals("Dua", body(cloud).getString("model"))
        assertFalse(body(cloud, agent = true).has("openrouter"))
        assertFalse(body(cloud, web = "search").has("openrouter"))
        assertFalse(body(cloud, stream = false).has("openrouter"))
        assertFalse(body(DuaApiClient("https://vercel.example/")).has("openrouter"))
    }

    @Test fun choiceIsSavedAndLabelled() {
        val saved = PreferencesCodec.decode(PreferencesCodec.encode(LocalPreferences(freeModel = gemma.id)))
        assertEquals(gemma.id, saved.freeModel)
        val old = JSONObject(PreferencesCodec.encode(LocalPreferences())).apply { put("freeModel", "evil/paid") }.toString()
        assertEquals("", PreferencesCodec.decode(old).freeModel)
        assertEquals("Gemma 4 31B", ChatUiState(freeModel = gemma).modelLabel)
        assertEquals("Dua", ChatUiState().modelLabel)
    }

    @Test fun pickerListsFreeModelsAndSelectsOne() {
        val picked = mutableListOf<String>()
        compose.setContent { DuaTheme(LocalPreferences()) {
            ModelPickerSheet("Dua", emptyList(), {}, {}, {}, freeModels = listOf(gemma), currentFree = null, onSelectFree = { picked += it })
        } }
        compose.onNodeWithText("Free models").performScrollTo().assertExists()
        compose.onNodeWithTag("free-model-${gemma.id}").performScrollTo().assertHeightIsAtLeast(Sizes.touchTarget)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(listOf(gemma.id), picked) }
    }

    @Test fun selectedFreeModelShowsAsSelected() {
        compose.setContent { DuaTheme(LocalPreferences()) {
            ModelPickerSheet("Dua", emptyList(), {}, {}, {}, freeModels = listOf(gemma), currentFree = gemma.id)
        } }
        compose.onNodeWithTag("free-model-${gemma.id}").performScrollTo().assertIsSelected()
    }
}
