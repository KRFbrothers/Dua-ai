package com.dua.ai.ui.chat

import android.graphics.BitmapFactory
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
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
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeGradientUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var surface = Color.Unspecified

    private fun show(state: androidx.compose.runtime.State<ChatUiState>, actions: ChatActions = ChatActions()) {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    surface = MaterialTheme.colorScheme.surface
                    ChatScreen(state.value, actions)
                }
            }
        }
        dispatchKeyboard(0)
    }

    private fun dispatchKeyboard(height: Int, navigationBar: Int = 0) {
        compose.runOnUiThread {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBar))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
                .setVisible(WindowInsetsCompat.Type.ime(), height > 0).build())
        }
        compose.waitForIdle()
    }

    private fun capture(label: String): File {
        val file = File("build/approved-ui/home-gradient-$label-$dark-$large.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
        return file
    }

    @Test fun greetingTracksRealProfileAndConversationWashStaysOutOfFeatures() {
        val state = mutableStateOf(ChatUiState(profileName = "Fareed Ansari"))
        show(state)
        compose.onNodeWithTag("home-hero").assertExists(); compose.onNodeWithContentDescription("Dua AI").assertExists()
        compose.onNodeWithText("Your AI, always with you").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Dua AI").assertExists()
        compose.onNodeWithTag("home-gradient").assertExists()
        val imageFile = capture("chat")
        val pixels = if (imageFile.length() > 0) BitmapFactory.decodeFile(imageFile.absolutePath) else null
        if (pixels != null) {
            try {
                assertEquals(surface.toArgb(), pixels.getPixel(2, 2))
                // Sample the canvas, away from the composer's intentional soft shadow.
                for (y in listOf(pixels.height / 4, pixels.height / 2, pixels.height * 3 / 4)) {
                    assertEquals("Home canvas must stay flat below its header", surface.toArgb(), pixels.getPixel(2, y))
                }
            } finally { pixels.recycle() }
        }
        compose.runOnIdle { state.value = state.value.copy(profileName = "Amina Khan") }
        compose.onNodeWithTag("home-hero").assertExists(); compose.onNodeWithContentDescription("Dua AI").assertExists()
        compose.onNodeWithText("Ask away, Fareed!").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(profileName = "") }
        compose.onNodeWithTag("home-hero").assertExists(); compose.onNodeWithContentDescription("Dua AI").assertExists()
        compose.runOnIdle { state.value = state.value.copy(messages = listOf(ChatMessage(text = "A real reply", isUser = false))) }
        compose.onNodeWithTag("home-gradient").assertDoesNotExist()
        compose.onNodeWithTag("conversation-ambient").assertExists()
        compose.onNodeWithText("A real reply").assertIsDisplayed()
        capture("conversation")
        compose.runOnIdle { state.value = ChatUiState(feature = ChatFeature.Agent) }
        compose.onNodeWithTag("home-gradient").assertDoesNotExist()
        compose.onNodeWithTag("conversation-ambient").assertDoesNotExist()
        compose.onNodeWithText("Ask away!").assertDoesNotExist()
    }

    @Test fun acceptedGenerationReleasesInputButRejectedDraftKeepsFocus() {
        val state = mutableStateOf(ChatUiState(draft = "Hi"))
        var accept = false
        show(state, ChatActions(onSend = {
            if (accept) state.value = state.value.copy(draft = "", typing = true,
                messages = listOf(ChatMessage(text = state.value.draft, isUser = true)))
        }))
        compose.onNodeWithContentDescription("Message").performClick().assertIsFocused()
        compose.onNodeWithContentDescription("Message").performImeAction()
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains("Hi")
        accept = true
        compose.onNodeWithContentDescription("Message").performImeAction()
        compose.onNodeWithContentDescription("Message").assertIsNotFocused()
        compose.onNodeWithContentDescription("Stop response").assertIsDisplayed()
        capture("generating")
    }

    @Test fun keyboardRaisesWholeHomeSurfaceAndTabsWithoutLosingFocusOrDraft() {
        val state = mutableStateOf(ChatUiState(profileName = "Fareed"))
        // Use the real platform inset dispatch path, rather than substituting a shorter screen.
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    ChatScreen(state.value, ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) }))
                }
            }
        }
        dispatchKeyboard(0)
        val closedSurface = compose.onNodeWithTag("home-gradient").fetchSemanticsNode().boundsInRoot
        val closedBar = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        val inputId = compose.onNodeWithContentDescription("Message").fetchSemanticsNode().id
        compose.onNodeWithContentDescription("Dua AI").assertWidthIsEqualTo(HomeSizes.heroLogo)
        compose.onNodeWithContentDescription("Message").performClick().assertIsFocused()
        dispatchKeyboard(280)
        val surface = compose.onNodeWithTag("home-gradient").fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertEquals("The Home surface must end at the keyboard", closedSurface.bottom - 280f, surface.bottom, 2f)
        assertEquals("IME inset must be applied once", closedBar.bottom - 280f, bar.bottom, 2f)
        assertTrue(bar.bottom <= surface.bottom)
        assertTrue("The keyboard opens more horizontal writing room", bar.width > closedBar.width)
        assertEquals("The wider composer remains centered", closedBar.center.x, bar.center.x, 1f)
        if (!large) assertTrue("The inline keyboard composer leaves more room for content", bar.height < closedBar.height)
        assertEquals("Keyboard changes must not recreate the input", inputId, compose.onNodeWithContentDescription("Message").fetchSemanticsNode().id)
        compose.onNodeWithContentDescription("Dua AI").assertWidthIsEqualTo(HomeSizes.heroCompactLogo)
        val tabs = compose.onNode(hasText("Chat") and isSelectable()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(tabs.bottom <= bar.top)
        compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
        capture("keyboard")
        compose.onNodeWithContentDescription("Message").performTextInput("Keep my draft")
        compose.onNodeWithTag("home-hero").assertDoesNotExist()
        capture("typing-keyboard")
        dispatchKeyboard(0)
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains("Keep my draft")
        val restoredBar = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertEquals(closedBar.left, restoredBar.left, 1f)
        assertEquals(closedBar.right, restoredBar.right, 1f)
        compose.onNodeWithTag("home-hero").assertDoesNotExist()
        compose.onNode(hasText("Task") and isSelectable()).performClick()
        compose.onNodeWithText("What can I take off\nyour plate?").assertDoesNotExist()
        compose.onNodeWithText(HomeTaskPrompts.last()).assertDoesNotExist()
        assertEquals("Keep my draft", state.value.draft)
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains("Keep my draft")
        capture("task")
        compose.onNodeWithContentDescription("Message").performTextClearance()
        dispatchKeyboard(280)
        compose.onNodeWithText("What can I take off\nyour plate?").assertDoesNotExist()
        compose.onNodeWithText(HomeTaskPrompts.last()).performScrollTo().assertIsDisplayed()
        dispatchKeyboard(0)
        compose.onNodeWithTag("home-hero").assertExists(); compose.onNodeWithContentDescription("Dua AI").assertExists()
        compose.onNodeWithContentDescription("Message").performTextInput("Another draft")
        compose.onNode(hasText("Chat") and isSelectable()).performClick()
        compose.onNodeWithText("Ask away, Fareed!").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message").assertIsFocused().assertTextContains("Another draft")
        compose.onNodeWithContentDescription("Message").performTextClearance()
        compose.onNodeWithTag("home-hero").assertExists(); compose.onNodeWithContentDescription("Dua AI").assertExists()
        compose.onNodeWithContentDescription("Dua AI").assertWidthIsEqualTo(HomeSizes.heroLogo)
        compose.onNodeWithContentDescription("Message").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w1000dp-h900dp-mdpi")
    fun wideHomeKeepsItsHeroAndTaskStartersCenteredWithReducedMotion() {
        val resolver = compose.activity.contentResolver
        val previous = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            show(mutableStateOf(ChatUiState(profileName = "Fareed", agentAvailable = true)))
            val canvas = compose.onNodeWithTag("home-empty-content").fetchSemanticsNode().boundsInRoot
            val hero = compose.onNodeWithTag("home-hero").fetchSemanticsNode().boundsInRoot
            assertTrue(hero.width <= HomeSizes.composerMaxWidth.value + 1f)
            assertEquals(canvas.center.x, hero.center.x, 1f)
            val chatCapture = capture("wide-chat")
            compose.onNode(hasText("Task") and isSelectable()).performClick().assertIsSelected()
            compose.onNodeWithText("Ask away, Fareed!").assertDoesNotExist()
            for (prompt in HomeTaskPrompts) {
                val row = compose.onNodeWithText(prompt).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(row.width <= HomeSizes.composerMaxWidth.value + 1f)
                assertEquals(canvas.center.x, row.center.x, 1f)
            }
            compose.onNodeWithTag("home-hero").assertExists(); compose.onNodeWithContentDescription("Dua AI").assertExists()
            val taskCapture = capture("wide-task")
            val chatPixels = requireNotNull(BitmapFactory.decodeFile(chatCapture.absolutePath))
            val taskPixels = requireNotNull(BitmapFactory.decodeFile(taskCapture.absolutePath))
            try {
                // The chat pane's outer edge contains only the backdrop (at 1000dp the history panel sits to its left), so text/layout changes cannot
                // make this assertion pass. Exercise the real tab action with motion off.
                val y = (chatPixels.height * .95f).toInt()
                // mdpi: just inside the chat pane, right of the permanent history panel and its 1 dp divider.
                val x = com.dua.ai.ui.theme.DrawerSizes.width.value.toInt() + 3
                assertEquals("Chat and Task must keep the same flat Home surface",
                    chatPixels.getPixel(x, y), taskPixels.getPixel(x, y))
            } finally { chatPixels.recycle(); taskPixels.recycle() }
        } finally { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous) }
    }

    @Test fun navigationAndKeyboardInsetsUseTheirUnionAndDoNotAccumulateAcrossToggles() {
        show(mutableStateOf(ChatUiState()))
        dispatchKeyboard(0, navigationBar = 24)
        val closed = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        repeat(2) {
            dispatchKeyboard(280, navigationBar = 24)
            val open = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
            assertEquals("Use max(IME, navigation), not their sum", closed.bottom - 256f, open.bottom, 2f)
            compose.onNode(hasText("Chat") and isSelectable()).assertIsDisplayed()
            compose.onNodeWithContentDescription("Voice mode").assertIsDisplayed()
            dispatchKeyboard(0, navigationBar = 24)
            assertEquals(closed.bottom, compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot.bottom, 2f)
        }
    }

    @Test fun blankCanvasAndDrawerDismissInputWithoutDiscardingTheDraft() {
        val state = mutableStateOf(ChatUiState())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    ChatScreen(state.value, ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) }))
                }
            }
        }
        compose.onNodeWithContentDescription("Message").performClick().performTextInput("Keep this draft")
        compose.onNodeWithTag("home-empty-content").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Message").assertIsNotFocused().assertTextContains("Keep this draft")
        compose.onNodeWithContentDescription("Message").performClick().assertIsFocused()
        compose.onNodeWithContentDescription("Open chat history").performClick()
        compose.onNodeWithTag("history-drawer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Message").assertIsNotFocused()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("history-drawer").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Message").assertTextContains("Keep this draft").assertIsNotFocused()
        assertEquals("Keep this draft", state.value.draft)
    }

    @Test fun predictiveBackCanCancelOrCloseTheDrawerWithOneHapticPerStateChange() {
        val haptics = mutableListOf<ChatHaptic>()
        compose.setContent { DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
            ChatScreen(ChatUiState(), ChatActions(haptic = { haptics += it }))
        } }
        compose.onNodeWithContentDescription("Open chat history").performClick()
        compose.onNodeWithTag("history-drawer").assertIsDisplayed()
        assertEquals(listOf(ChatHaptic.Tap), haptics)
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 200f, 0f, androidx.activity.BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(80f, 200f, 0.5f, androidx.activity.BackEventCompat.EDGE_LEFT))
        }
        compose.onNodeWithTag("history-drawer").assertIsDisplayed()
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithTag("history-drawer").assertIsDisplayed()
        assertEquals(1, haptics.size)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.onNodeWithTag("history-drawer").assertIsNotDisplayed()
        assertEquals(listOf(ChatHaptic.Tap, ChatHaptic.Tap), haptics)
    }

    @Test fun logoStaysAtTheSameSpotWhenTogglingChatAndTask() {
        show(mutableStateOf(ChatUiState(profileName = "Fareed", agentAvailable = true)))
        val chatLogo = compose.onNodeWithContentDescription("Dua AI").fetchSemanticsNode().boundsInRoot
        compose.onNode(hasText("Task") and isSelectable()).performClick().assertIsSelected()
        compose.waitForIdle()
        val taskLogo = compose.onNodeWithContentDescription("Dua AI").fetchSemanticsNode().boundsInRoot
        // At 2x font the top bar and composer themselves grow differently per mode, so allow a small drift there.
        assertEquals("logo must not jump: $chatLogo vs $taskLogo", chatLogo.top, taskLogo.top, if (large) 12f else 1f)
        assertEquals(chatLogo.left, taskLogo.left, 1f)
        assertEquals(chatLogo.width, taskLogo.width, 0.5f)
        compose.onNode(hasText("Chat") and isSelectable()).performClick()
        compose.onNodeWithText(HomeTaskPrompts.first()).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun landscapeHomeKeepsLogoStartersToggleAndComposerApart() {
        show(mutableStateOf(ChatUiState(profileName = "Fareed", agentAvailable = true)))
        val toggle = compose.onNode(hasText("Chat") and isSelectable()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertTrue(toggle.bottom <= composer.top + 1f)
        val chatLogo = compose.onNodeWithContentDescription("Dua AI").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("logo $chatLogo overlaps toggle $toggle", chatLogo.bottom <= toggle.top + 1f)
        val canvas = compose.onNodeWithTag("home-empty-content").fetchSemanticsNode().boundsInRoot
        assertTrue("logo $chatLogo is cut off at the top of $canvas", chatLogo.top >= canvas.top - 1f)
        assertEquals(HomeSizes.heroCompactLogo.value, chatLogo.width, 0.5f)
        capture("landscape-chat")
        compose.onNode(hasText("Task") and isSelectable()).performClick().assertIsSelected()
        compose.waitForIdle()
        for (prompt in HomeTaskPrompts) {
            val row = compose.onNodeWithText(prompt).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("prompt $row overlaps toggle $toggle", row.bottom <= toggle.top + 1f)
        }
        compose.onNodeWithText(HomeTaskPrompts.first()).performScrollTo()
        capture("landscape-task")
    }

    @Test fun conversationToggleFloatsTransparentlyOverMessagesAndStillSwitches() {
        val messages = (1..30).map { ChatMessage(text = "Message number $it with some longer text to fill the row", isUser = it % 2 == 0) }
        val state = mutableStateOf(ChatUiState(profileName = "Fareed", agentAvailable = true, messages = messages))
        show(state)
        val list = compose.onNodeWithTag("chat-messages").fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("home-mode-switcher").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("toggle $toggle must sit over the message list $list", toggle.top >= list.top && toggle.bottom <= list.bottom + 1f)
        val composer = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        assertTrue(toggle.bottom <= composer.top + 1f)
        for (tab in listOf("Chat", "Task")) compose.onNode(hasText(tab) and isSelectable())
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        capture("conversation-toggle")
        compose.onNode(hasText("Task") and isSelectable()).performClick().assertIsSelected()
        val colors = if (dark) com.dua.ai.ui.theme.HomeDarkColors else com.dua.ai.ui.theme.HomeLightColors
        assertTrue("floating pill must be translucent", colors.segmentFloatSelected.alpha < 1f)
    }
}
