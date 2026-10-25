package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.navigation.DuaDestination
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
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComputerDrawerUiTest(private val dark: Boolean, private val small: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},small={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (small) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(if (small) 320.dp else 360.dp, if (small) 568.dp else 752.dp)) { content() }
                }
            }
        }
    }
    private fun capture(name: String) {
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/$name-${if (dark) "dark" else "light"}-${if (small) "small-font2" else "normal"}.png")
            file.parentFile?.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        }
    }

    @Test fun openDrawerLeavesBothRightHandHomeControlsEntirelyInTheScrim() {
        show { ChatScreen(ChatUiState(), ChatActions(), initialDrawerValue = DrawerValue.Open) }
        val drawer = compose.onNodeWithTag("history-drawer").fetchSemanticsNode().boundsInRoot
        val profile = compose.onNodeWithContentDescription("Open profile and settings").fetchSemanticsNode().boundsInRoot
        val voice = compose.onNodeWithContentDescription("Voice mode").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(if (small) 253f else 277f, drawer.width, .5f)
        for (control in listOf(profile, voice)) {
            assertTrue("Drawer overlaps Home control: $drawer / $control", drawer.right <= control.left + .5f)
            assertTrue(control.right <= root.right + .5f)
            assertTrue(control.bottom <= root.bottom + .5f)
            assertTrue(control.width >= 48f && control.height >= 48f)
        }
        capture("computer-drawer-open")
    }

    @Test fun homeProfileAndDrawerComputerKeepTheirExistingActions() {
        val opened = mutableListOf<DuaDestination>(); var newChats = 0; var settings = 0
        show { ChatScreen(ChatUiState(), ChatActions(onOpenDestination = { opened += it }, onNewChat = { newChats++ }, onOpenSettings = { settings++ })) }
        compose.onNodeWithContentDescription("Open profile and settings").performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Open chat history").performClick()
        compose.onNodeWithContentDescription("Open drawer profile and settings").assertIsDisplayed().performClick()
        compose.onNodeWithTag("history-drawer").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Open chat history").performClick()
        compose.onNodeWithText("Computer").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(DuaDestination.Computer), opened); assertEquals(0, newChats); assertEquals(2, settings) }
    }

    @Test fun expandedAuraUsesTheSameTextStyleAndColorAsMainRows() {
        show { ChatScreen(ChatUiState(), ChatActions(), initialDrawerValue = DrawerValue.Open) }
        val main = textLayout("Images").layoutInput.style
        compose.onNodeWithText("Aura").performScrollTo().performClick()
        for (label in listOf("Plugins", "Work")) {
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("history-drawer"))).performScrollTo()
            val style = textLayout(label, inDrawer = true).layoutInput.style
            assertEquals(main.fontSize, style.fontSize)
            assertEquals(main.fontWeight, style.fontWeight)
            assertEquals(main.color, style.color)
        }
        capture("computer-drawer-aura")
    }

    @Test fun conversationsKeepFindAndTheExistingNewChatAction() {
        var newChats = 0
        val state = ChatUiState(messages = listOf(ChatMessage(text = "Hello", isUser = true)))
        show { ChatScreen(state, ChatActions(onNewChat = { newChats++ })) }
        compose.onNodeWithContentDescription("Open profile and settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Find in chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("New chat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open chat history").performClick()
        compose.onNodeWithTag("drawer-new-chat").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, newChats) }
    }

    private fun textLayout(label: String, inDrawer: Boolean = false): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val matcher = hasText(label) and if (inDrawer) hasAnyAncestor(hasTestTag("history-drawer")) else SemanticsMatcher("any") { true }
        compose.onNode(matcher, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
