package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synthetic keyboard insets test layout transitions, not the platform IME or Back dispatcher. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeKeyboardReferenceUiTest(private val dark: Boolean, private val largeText: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()

    private fun capture(stage: String) {
        val file = File("build/approved-ui/home-keyboard-$stage-$dark-$largeText.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun taskKeyboardHidesOnlyHeroAndRestoresItWithoutChangingDraftOrFocus() {
        val keyboard = mutableStateOf(false)
        val task = mutableStateOf(true)
        val draft = mutableStateOf("")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (largeText) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    // Simulate the viewport already reduced by ChatScreen's single inset owner.
                    Column(Modifier.requiredSize(393.dp, if (keyboard.value) 430.dp else 798.dp)) {
                        Box(Modifier.weight(1f)) {
                            ChatContent(task.value, composing = draft.value.isNotBlank(),
                                keyboardInsets = WindowInsets(bottom = if (keyboard.value) 320 else 0),
                                onTask = { draft.value = it })
                        }
                        Composer(homeLayout = true, controlled = true, value = draft.value,
                            onDraftChange = { draft.value = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("home-hero").assertExists()
        compose.onNodeWithContentDescription("Message").performClick().assertIsFocused()
        compose.runOnIdle { keyboard.value = true }
        compose.onNodeWithTag("home-hero").assertDoesNotExist()
        HomeTaskPrompts.forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        capture("task-open")
        compose.runOnIdle { task.value = false }
        compose.onNodeWithTag("home-hero").assertExists()
        compose.runOnIdle { task.value = true }
        compose.onNodeWithText(HomeTaskPrompts[1]).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains(HomeTaskPrompts[1])
        compose.onNodeWithTag("supplied-task-starters").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
        compose.runOnIdle { keyboard.value = false }
        compose.onNodeWithContentDescription("Message").assertTextContains(HomeTaskPrompts[1])
        compose.runOnIdle { draft.value = "" }
        compose.onNodeWithTag("home-hero").assertExists()
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
        capture("task-closed")
    }

    @Test fun homeMultilineCapsAndKeepsActionsAndRejectedDraftThroughResize() {
        val draft = mutableStateOf("")
        val height = mutableStateOf(650.dp)
        val enabled = mutableStateOf(false)
        val sent = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (largeText) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(320.dp, height.value)) {
                        Composer(homeLayout = true, controlled = true, value = draft.value,
                            onDraftChange = { draft.value = it }, sendEnabled = enabled.value,
                            onSend = { sent += it })
                    }
                }
            }
        }
        val message = compose.onNodeWithContentDescription("Message")
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
        message.performClick().performTextInput((1..6).joinToString("\n") { "Line $it" })
        val sixLines = message.fetchSemanticsNode().boundsInRoot.height
        message.performTextInput((7..30).joinToString("\n", prefix = "\n") { "Line $it" })
        assertEquals(sixLines, message.fetchSemanticsNode().boundsInRoot.height, 1f)
        val expected = draft.value
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        message.performImeAction()
        assertTrue(sent.isEmpty())
        compose.runOnIdle { height.value = 240.dp; enabled.value = true }
        val field = message.assertIsFocused().fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithContentDescription("Send message")
            .assertIsDisplayed().assertHeightIsAtLeast(Sizes.touchTarget).fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertTrue(field.bottom <= send.top)
        assertTrue(send.bottom <= card.bottom)
        capture("long-small-window")
        compose.onNodeWithContentDescription("Send message").performClick()
        assertEquals(listOf(expected), sent)
        assertEquals(expected, draft.value) // Owner rejected/deferred; composer must not consume it.
        compose.runOnIdle { height.value = 650.dp }
        message.assertIsFocused().assertTextContains("Line 30", substring = true)
        compose.runOnIdle { draft.value = "" }
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }
}
