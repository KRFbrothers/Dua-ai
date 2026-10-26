package com.dua.ai.ui.chat

import android.graphics.BitmapFactory
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
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

    private fun show(state: androidx.compose.runtime.State<ChatUiState>, actions: ChatActions = ChatActions()) {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
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
        compose.onNodeWithTag("supplied-chat").assertIsDisplayed()
        compose.onNodeWithText("Hello Fareed!", substring = true).assertIsDisplayed()
        capture("chat")
        compose.runOnIdle { state.value = state.value.copy(profileName = "Amina Khan") }
        compose.onNodeWithText("Hello Amina!", substring = true).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(profileName = "") }
        compose.onNodeWithText("Hello! Main Dua AI hoon", substring = true).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(messages = listOf(ChatMessage(text = "A real reply", isUser = false))) }
        compose.onNodeWithText("A real reply").assertIsDisplayed()
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        capture("conversation")
        compose.runOnIdle { state.value = ChatUiState(feature = ChatFeature.Agent) }
        compose.onNodeWithTag("supplied-chat").assertDoesNotExist()
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

    @Test fun navigationAndKeyboardInsetsUseTheirUnionAndDoNotAccumulateAcrossToggles() {
        show(mutableStateOf(ChatUiState()))
        dispatchKeyboard(0, navigationBar = 24)
        val closed = compose.onNodeWithTag("supplied-composer").fetchSemanticsNode().boundsInRoot
        repeat(2) {
            dispatchKeyboard(280, navigationBar = 24)
            val open = compose.onNodeWithTag("supplied-composer").fetchSemanticsNode().boundsInRoot
            assertEquals("Use max(IME, navigation), not their sum", closed.bottom - 256f, open.bottom, 2f)
            compose.onNode(hasText("Chat") and isSelectable()).assertIsDisplayed()
            compose.onNodeWithContentDescription("Voice").assertIsDisplayed()
            dispatchKeyboard(0, navigationBar = 24)
            assertEquals(closed.bottom, compose.onNodeWithTag("supplied-composer").fetchSemanticsNode().boundsInRoot.bottom, 2f)
        }
    }

    @Test fun predictiveBackCanCancelOrCloseTheDrawerWithOneHapticPerStateChange() {
        val haptics = mutableListOf<ChatHaptic>()
        compose.setContent { DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
            ChatScreen(ChatUiState(), ChatActions(haptic = { haptics += it }))
        } }
        compose.onNodeWithContentDescription("Dua AI, chats and settings").performClick()
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

}
