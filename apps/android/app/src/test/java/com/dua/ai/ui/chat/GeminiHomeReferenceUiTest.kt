package com.dua.ai.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.ProviderCapability
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.InterFont
import com.dua.ai.ui.theme.ComposerInputStyle
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
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeminiHomeReferenceUiTest(private val dark: Boolean, private val compact: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},compact={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()

    @Test fun sharedInterFontIsOfflineAndDraftSurvivesLayoutChanges() {
        val home = mutableStateOf(true)
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Composer(value = "A real draft", controlled = true, homeLayout = home.value, showModelSelector = false)
            }
        }
        fun fieldStyle() = mutableListOf<TextLayoutResult>().also { results ->
            compose.onNodeWithContentDescription("Message").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        }.single().layoutInput.style
        assertEquals(InterFont, fieldStyle().fontFamily)
        compose.runOnIdle { home.value = false }
        assertEquals(ComposerInputStyle.fontFamily, fieldStyle().fontFamily)
        compose.onNodeWithContentDescription("Message").assertTextContains("A real draft")
    }

    @Test fun homeCentersItsBrandAndKeepsRealChatTaskAndComposerActions() {
        val state = mutableStateOf(ChatUiState(profileName = "Fareed", agentAvailable = true,
            providers = listOf(ProviderCapability("Dua", true, "gemini-2.5-flash", true))))
        var sent = ""; var voice = 0; var stop = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (compact) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(if (compact) 320.dp else 393.dp, if (compact) 568.dp else 798.dp)
                        .testTag("reference-home-viewport")) {
                        ChatScreen(state.value, ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) },
                            onAgent = { state.value = state.value.copy(agent = it) }, onSend = { sent = state.value.draft },
                            onOpenVoiceMode = { voice++ }, onStop = { stop++ }))
                    }
                }
            }
        }
        compose.onNodeWithTag("starter-chips").assertDoesNotExist()
        compose.onNodeWithText("How can I help you today?", substring = true).assertDoesNotExist()
        val content = compose.onNodeWithTag("home-empty-content").fetchSemanticsNode().boundsInRoot
        val hero = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
        if (!compact) {
            // The hero shares one spot with Task (its starter area is reserved), so it sits in the upper half.
            assertEquals(content.center.x, hero.center.x, 1f)
            assertTrue(hero.top > content.top && hero.center.y <= content.center.y + 1f)
            assertTrue("Hero should leave breathing space", hero.height < content.height)
        }
        compose.onNodeWithText("Hi Fareed, what's on your mind?").performScrollTo().assertIsDisplayed()
        val composer = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertTrue(hero.bottom <= composer.top)
        for (label in listOf("Add photos and files", "Dictate", "Voice mode")) {
            val button = compose.onNodeWithContentDescription(label).assertIsDisplayed()
                .assertWidthIsAtLeast(Sizes.touchTarget).assertHeightIsAtLeast(Sizes.touchTarget)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(button.left >= composer.left && button.right <= composer.right)
        }
        val file = File("build/approved-ui/gemini-home-$dark-$compact.png")
        file.parentFile!!.mkdirs(); compose.onNodeWithTag("reference-home-viewport").captureRoboImage(file.absolutePath)
        compose.onNodeWithContentDescription("Voice mode").performClick(); compose.waitForIdle(); assertEquals(1, voice)
        compose.onNode(hasText("Task") and isSelectable()).performClick()
        compose.onNodeWithText("Describe...").assertIsDisplayed()
        val entryFile = File("build/approved-ui/gemini-task-entry-$dark-$compact.png")
        compose.onNodeWithTag("reference-home-viewport").captureRoboImage(entryFile.absolutePath)
        if (compact) {
            val fullHero = compose.onNodeWithTag("home-hero").getUnclippedBoundsInRoot()
            val firstStarter = compose.onNodeWithText(HomeTaskPrompts[0]).getUnclippedBoundsInRoot()
            assertTrue("Task prompts must follow the complete greeting instead of clipping it", firstStarter.top >= fullHero.bottom)
            compose.onNodeWithText(HomeTaskPrompts[1]).performScrollTo().assertIsDisplayed()
            assertTrue("The greeting and starters scroll together in compact Task",
                compose.onNodeWithTag("home-hero").getUnclippedBoundsInRoot().top < fullHero.top)
        }
        val taskFile = File("build/approved-ui/gemini-task-$dark-$compact.png")
        compose.onNodeWithTag("reference-home-viewport").captureRoboImage(taskFile.absolutePath)
        compose.onNodeWithText(HomeTaskPrompts[1]).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Send message").performClick()
        assertEquals(HomeTaskPrompts[1], sent); assertTrue(state.value.agent)
        compose.runOnIdle { state.value = state.value.copy(draft = "", typing = true,
            messages = listOf(ChatMessage(text = "Working", isUser = false))) }
        compose.onNodeWithTag("home-empty-content").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stop response").performClick(); assertEquals(1, stop)
    }
}
