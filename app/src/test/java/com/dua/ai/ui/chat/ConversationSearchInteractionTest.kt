package com.dua.ai.ui.chat

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRun
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class ConversationSearchInteractionTest(private val dark: Boolean, private val small: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},small={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private var back: OnBackPressedDispatcher? = null
    private fun chat() = ChatUiState(chatId = "first", draft = "Keep this draft", messages = (1..12).map {
        ChatMessage("m$it", if (it == 2 || it == 9) "Invoice details $it" else "Other message $it", it % 2 == 0)
    })
    private fun show(initial: ChatUiState = chat()): androidx.compose.runtime.MutableState<ChatUiState> {
        val state = mutableStateOf(initial)
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (small) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(if (small) 320.dp else 360.dp, if (small) 568.dp else 752.dp)) {
                        ChatScreen(state.value, ChatActions())
                    }
                }
            }
        }
        return state
    }
    private fun query(text: String) {
        compose.onNodeWithContentDescription("Search within chat").performTextReplacement(text)
        compose.mainClock.advanceTimeBy(250)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Searching messages…").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }
    private fun selected(id: String) = compose.onNodeWithTag("message-$id").assertIsDisplayed()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected search result"))
    @Test fun searchNavigatesInBothDirectionsWrapsAndKeepsDraft() {
        val state = show()
        compose.onNodeWithContentDescription("Open chat history").performTouchInput { longClick() }; query("invoice")
        compose.onNodeWithText("1 of 2 matching messages").assertIsDisplayed(); selected("m2")
        compose.onNodeWithContentDescription("Next matching message").performClick()
        compose.onNodeWithText("2 of 2 matching messages").assertIsDisplayed(); selected("m9")
        compose.onNodeWithContentDescription("Next matching message").performClick(); selected("m2")
        compose.onNodeWithContentDescription("Previous matching message").performClick(); selected("m9")
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/chat-search-${if (dark) "dark" else "light"}-${if (small) "small-font2" else "normal"}.png")
            file.parentFile?.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        }
        compose.onNodeWithContentDescription("Close chat search").performClick()
        compose.onNodeWithContentDescription("Search within chat").assertDoesNotExist()
        assertEquals("Keep this draft", state.value.draft)
    }
    @Test fun noMatchesClearAndSwitchingConversationsInvalidateResults() {
        val state = show()
        compose.onNodeWithContentDescription("Open chat history").performTouchInput { longClick() }; query("absent")
        compose.onNodeWithText("No matching messages").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next matching message").assertIsNotEnabled()
        query(""); compose.onNodeWithText("Search messages in this chat").assertIsDisplayed()
        query("invoice"); selected("m2")
        compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages.toMutableList(), draft = "Updated draft") }
        compose.onNodeWithText("1 of 2 matching messages").assertIsDisplayed(); selected("m2")
        compose.runOnIdle { state.value = state.value.copy(chatId = "second", messages = listOf(ChatMessage("other", "Second chat", true))) }
        compose.onNodeWithContentDescription("Search within chat").assertDoesNotExist()
        compose.onNodeWithTag("message-other").assertIsDisplayed()
    }
    @Test fun streamingUpdatesDoNotPullSearchBackToTheTailAndBackClosesSearch() {
        val state = show(chat().copy(typing = true, messages = chat().messages.map { if (it.id == "m12") it.copy(state = "streaming") else it }))
        compose.onNodeWithContentDescription("Open chat history").performTouchInput { longClick() }; query("invoice"); selected("m2")
        repeat(3) { n ->
            compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages.map {
                if (it.streaming) it.copy(text = "Streaming token $n") else it
            }) }
            compose.waitForIdle()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("1 of 2 matching messages").fetchSemanticsNodes().isNotEmpty()
            }
            selected("m2")
        }
        compose.runOnIdle { requireNotNull(back).onBackPressed() }
        compose.onNodeWithContentDescription("Search within chat").assertDoesNotExist()
        compose.onNodeWithTag("message-m2").assertIsDisplayed()
    }
    @Test fun codeOutputSnippetIsSearchableWithoutExpandingTheCodeBlock() {
        show(ChatUiState(chatId = "code", messages = listOf(ChatMessage("code", "Finished", false,
            codeRuns = listOf(CodeRun(code = "print(42)", output = "result-marker 42", outcome = "ok"))))))
        compose.onNodeWithContentDescription("Open chat history").performTouchInput { longClick() }; query("result-marker")
        compose.onNodeWithText("1 of 1 matching messages").assertIsDisplayed()
        compose.onNodeWithText("Output: result-marker 42").assertIsDisplayed(); selected("code")
        for (description in listOf("Close chat search", "Previous matching message", "Next matching message")) {
            compose.onNodeWithContentDescription(description).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }
    @Test fun incomingTokensDoNotRestartANewQueriesDebounce() {
        val state = show(chat().copy(typing = true))
        compose.onNodeWithContentDescription("Open chat history").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val started = compose.mainClock.currentTime
        compose.onNodeWithContentDescription("Search within chat").performTextReplacement("invoice")
        repeat(10) { n ->
            compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages.map {
                if (it.id == "m12") it.copy(text = "Streaming token $n", state = "streaming") else it
            }) }
            compose.mainClock.advanceTimeBy(20, ignoreFrameDuration = true)
        }
        // Results must arrive before another 150 ms passes after the last token.
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("1 of 2 matching messages").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.mainClock.currentTime - started < 350)
        compose.mainClock.autoAdvance = true
        selected("m2")
    }
}
