package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.HomeSizes
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
@Config(sdk = [34], qualifiers = "w600dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeComposerPolishUiTest(private val dark: Boolean, private val largeText: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()

    @Test fun chatAndTaskPlaceholdersStayReadableWithSeparateAccessibleActions() {
        val placeholder = mutableStateOf("Ask Dua AI...")
        var attachments = 0; var mic = 0; var live = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (largeText) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(320.dp, 650.dp).background(ChatTheme.colors.background), contentAlignment = Alignment.BottomCenter) {
                        Composer(homeLayout = true, controlled = true, showModelSelector = false,
                            placeholder = placeholder.value, onAttach = { attachments++ },
                            onMic = { mic++ }, onVoiceMode = { live++ })
                    }
                }
            }
        }
        for (label in listOf("Ask Dua AI...", "Describe...")) {
            compose.runOnIdle { placeholder.value = label }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val debug = File("build/approved-ui/home-polish-placeholder-$dark-$largeText.png")
            debug.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(debug.absolutePath)
            assertFalse("The full $label placeholder must fit: size=${layout.size}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, paragraph=${layout.multiParagraph.height}, constraints=${layout.layoutInput.constraints}", layout.hasVisualOverflow)
            assertFalse("The placeholder must not be replaced with an ellipsis", layout.isLineEllipsized(0))
            val field = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().boundsInRoot
            val plus = compose.onNodeWithContentDescription("Add photos and files").fetchSemanticsNode().boundsInRoot
            if (largeText) assertTrue("Large text needs a separate input row", field.bottom <= plus.top)
            else assertTrue("Normal text keeps the inline capsule", field.top < plus.bottom && plus.top < field.bottom)
            val container = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
            val controls = listOf("Add photos and files", "Dictate", "Voice mode").map { description ->
                compose.onNodeWithContentDescription(description).assertIsDisplayed()
                    .assertWidthIsAtLeast(Sizes.touchTarget).assertHeightIsAtLeast(Sizes.touchTarget)
                    .fetchSemanticsNode().boundsInRoot.also { assertTrue(it.left >= container.left && it.right <= container.right) }
            }
            assertTrue(controls[0].right <= controls[1].left)
            assertTrue(controls[1].right <= controls[2].left)
        }
        compose.onNodeWithContentDescription("Add photos and files").performClick()
        compose.onNodeWithContentDescription("Dictate").performClick()
        compose.onNodeWithContentDescription("Voice mode").performClick()
        assertEquals(listOf(1, 1, 1), listOf(attachments, mic, live))
        val file = File("build/approved-ui/home-polish-composer-$dark-$largeText.png")
        file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun windowResizeAndIdleTypingTransitionsPreserveFocusAndDraft() {
        val width = mutableStateOf(600.dp)
        val draft = mutableStateOf("")
        var sent = ""
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (largeText) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(width.value, 650.dp).background(ChatTheme.colors.background), contentAlignment = Alignment.BottomCenter) {
                        Composer(homeLayout = true, controlled = true, showModelSelector = false,
                            value = draft.value, onDraftChange = { draft.value = it }, onSend = { sent = it })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Message").performClick().assertIsFocused()
        val idleHeight = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithContentDescription("Message").performTextInput("Hi")
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains("Hi")
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
        val shortHeight = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot.height
        assertEquals("A short Home draft keeps the inline pill", idleHeight, shortHeight, 1f)
        val file = File("build/approved-ui/home-polish-short-draft-$dark-$largeText.png")
        file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        compose.onNodeWithContentDescription("Message").performTextInput("\nSecond line")
        val multiline = compose.onNodeWithContentDescription("Message").assertIsFocused().fetchSemanticsNode().boundsInRoot
        val attach = compose.onNodeWithContentDescription("Add photos and files").fetchSemanticsNode().boundsInRoot
        assertTrue("Multiline drafts get a separate input row", multiline.bottom <= attach.top)
        compose.onNodeWithContentDescription("Message").performTextClearance()
        assertEquals(idleHeight, compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { width.value = 320.dp }
        compose.onNodeWithContentDescription("Message").assertIsFocused().performTextInput("Keep this draft")
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains("Keep this draft")
        compose.runOnIdle { width.value = 600.dp }
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains("Keep this draft")
        compose.onNodeWithContentDescription("Send message").performClick()
        assertEquals("Keep this draft", sent)
        compose.onNodeWithContentDescription("Message").assertIsFocused()
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp-mdpi")
    fun wideComposerWrapsInsideItsActualCardAndKeepsItsInputWhenResized() {
        val width = mutableStateOf(1000.dp)
        val draft = mutableStateOf("")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (largeText) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(width.value, 650.dp), contentAlignment = Alignment.BottomCenter) {
                        Composer(homeLayout = true, controlled = true, showModelSelector = false,
                            value = draft.value, onDraftChange = { draft.value = it })
                    }
                }
            }
        }
        val card = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertTrue(card.width <= HomeSizes.composerMaxWidth.value + 1f)
        assertEquals(500f, card.center.x, 1f)
        val text = "A longer draft should wrap inside the centered composer rather than stretch across a tablet."
        compose.onNodeWithContentDescription("Message").performClick().performTextInput(text)
        val field = compose.onNodeWithContentDescription("Message").assertIsFocused().fetchSemanticsNode().boundsInRoot
        val attach = compose.onNodeWithContentDescription("Add photos and files").fetchSemanticsNode().boundsInRoot
        assertTrue("Wrapping uses the bounded card width", field.bottom <= attach.top)
        val file = File("build/approved-ui/home-polish-wide-draft-$dark-$largeText.png")
        file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
        compose.runOnIdle { width.value = 320.dp }
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains(text)
    }
}
