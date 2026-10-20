package com.dua.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.components.ReferenceIcons
import com.dua.ai.ui.theme.*
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
class HomeHeaderRefinementUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private var background = Color.Unspecified
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                background = ChatTheme.colors.background
                assertEquals(if (dark) HomeDarkColors else HomeLightColors, HomeTheme.colors)
                Box(Modifier.requiredSize(if (large) 320.dp else 360.dp, if (large) 568.dp else 752.dp)
                    .background(background)) { content() }
            }
        }
    }
    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
    private fun capture(name: String) {
        val file = File("build/approved-ui/header-$name-${if (dark) "dark" else "light"}-${if (large) "font2" else "regular"}.png")
        file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun brandIsProminentAndModelIsASeparateCompactPillWithoutEllipsis() {
        var model = 0; var menu = 0; var profile = 0; var newChat = 0
        show { ChatTopBar(modelName = "Gemini", onModel = { model++ }, onMenu = { menu++ },
            onProfile = { profile++ }, onNewChat = { newChat++ }) }
        for (text in listOf("Dua AI", "Gemini")) {
            val result = layout(text)
            assertEquals(if (text == "Dua AI") 19.sp else 14.sp, result.layoutInput.style.fontSize)
            assertEquals(FontWeight.Medium, result.layoutInput.style.fontWeight)
            assertFalse("$text: size=${result.size}, width=${result.didOverflowWidth}, height=${result.didOverflowHeight}, lines=${result.lineCount}, constraints=${result.layoutInput.constraints}, para=${result.multiParagraph.height}", result.hasVisualOverflow)
            compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        }
        val brand = compose.onNodeWithText("Dua AI", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("Gemini", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val divider = compose.onNodeWithTag("home-header-divider", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pill = compose.onNodeWithTag("home-model-pill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(brand.center.y, pill.center.y, 1f)
        assertTrue(brand.right < divider.left && divider.right < pill.left)
        assertTrue(pill.left < label.left && label.right < pill.right)
        compose.onNodeWithContentDescription("Model, Gemini").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        if (!large) compose.onNodeWithTag("home-model-pill", useUnmergedTree = true).assertHeightIsEqualTo(32.dp)
        val group = compose.onNodeWithTag("home-header-labels").fetchSemanticsNode().boundsInRoot
        assertTrue(label.left + layout("Gemini").size.width <= group.right + 1f)
        // Flat controls reveal the unchanged page color outside their glyphs.
        for (description in listOf("Open chat history", "New chat")) {
            val button = compose.onNodeWithContentDescription(description).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val file = File("build/approved-ui/header-control-${description.replace(' ', '-')}-${dark}-${large}.png")
            file.parentFile!!.mkdirs(); button.captureRoboImage(file.absolutePath)
            val pixels = BitmapFactory.decodeFile(file.absolutePath)
            if (pixels != null) {
                assertEquals(background.toArgb(), pixels.getPixel(4, pixels.height / 2))
            }
        }
        assertEquals("dua_circle_dashed_pen_nib", ReferenceIcons.TemporaryChat.name)
        capture("gemini")
        compose.onNodeWithContentDescription("Model, Gemini").performClick()
        compose.onNodeWithContentDescription("Open chat history").performClick()
        compose.onNodeWithContentDescription("Open profile and settings").performClick()
        compose.onNodeWithContentDescription("New chat").performClick()
        assertEquals(listOf(1, 1, 1, 1), listOf(model, menu, profile, newChat))
    }

    @Test fun transportSuffixIsRemovedAndLiveModelUpdatesStillOpenThePicker() {
        val name = mutableStateOf("Gemini (Cloud Run)")
        var selected = 0
        show { ChatTopBar(modelName = name.value, onModel = { selected++ }, task = true) }
        compose.onNodeWithText("Gemini (Cloud Run)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Model, Gemini").performClick()
        assertFalse("Gemini: ${layout("Gemini").size}, w=${layout("Gemini").didOverflowWidth}, h=${layout("Gemini").didOverflowHeight}", layout("Gemini").hasVisualOverflow)
        compose.runOnIdle { name.value = "gemini-next-pro-preview" }
        compose.onNodeWithContentDescription("Model, Pro").performClick()
        assertFalse(layout("Pro").hasVisualOverflow)
        assertEquals(2, selected)
        compose.onNodeWithContentDescription("New chat").assertDoesNotExist()
        capture("task-pro")
    }

    @Test fun chatAndTaskShareTheDuaLogoWithoutHeadlineAndKeepRealPromptHandlers() {
        val task = mutableStateOf(false)
        var chosen = ""
        show { ChatContent(task.value, onTask = { chosen = it }) }
        compose.onNodeWithContentDescription("Dua AI").assertWidthIsEqualTo(HomeSizes.heroLogo)
        compose.onNodeWithText("Dua always with you").assertIsDisplayed()
        compose.onNodeWithText("What can I take off\nyour plate?").assertDoesNotExist()
        capture("chat-hero")
        compose.runOnIdle { task.value = true }
        compose.onNodeWithContentDescription("Dua AI").assertExists()
        compose.onNodeWithText("Dua always with you").assertDoesNotExist()
        compose.onNodeWithTag("home-hero").assertExists(); compose.onNodeWithContentDescription("Dua AI").assertExists()
        for (prompt in HomeTaskPrompts) {
            compose.onNodeWithText(prompt).performScrollTo().performClick()
            assertEquals(prompt, chosen)
        }
        capture("task-hero")
    }

    @Test fun biggerLogoMatchesInBothModesAndNeverOverlapsTaskStartersOnSmallScreens() {
        val task = mutableStateOf(false)
        val boxHeight = mutableStateOf(568.dp)
        compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                    DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                        Box(Modifier.requiredSize(320.dp, boxHeight.value)) { ChatContent(task.value, onTask = {}) }
                    }
                }
            }
        for (height in listOf(568.dp, 330.dp)) {
            compose.runOnIdle { task.value = false; boxHeight.value = height }
            val logo = compose.onNodeWithContentDescription("Dua AI").assertWidthIsEqualTo(HomeSizes.heroLogo)
                .fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { task.value = true }
            compose.waitForIdle()
            val taskLogo = compose.onNodeWithContentDescription("Dua AI").assertWidthIsEqualTo(HomeSizes.heroLogo)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(logo.width, taskLogo.width, 0.5f)
            val firstPrompt = compose.onNodeWithText(HomeTaskPrompts.first()).fetchSemanticsNode().boundsInRoot
            assertTrue("logo $taskLogo overlaps prompt $firstPrompt at $height", taskLogo.bottom <= firstPrompt.top + 0.5f)
        }
    }
}
