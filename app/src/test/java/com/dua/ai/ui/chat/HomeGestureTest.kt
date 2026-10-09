package com.dua.ai.ui.chat

import android.view.HapticFeedbackConstants
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.CodeExecutionConfig
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Mic tap / long-press, swipe-up to the + sheet, and the light haptics on mode toggles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class HomeGestureTest {
    @get:Rule val compose = createComposeRule()

    private val home = ChatUiState(webSearchConfigured = true, codeConfig = CodeExecutionConfig(true, providers = setOf("Dua", "Gemini")))
    private val conversation = home.copy(messages = listOf(
        ChatMessage(id = "u1", text = "Hi", isUser = true),
        ChatMessage(id = "m1", text = "Hello! How can I help?", isUser = false, model = "Dua"),
    ))

    private val events = mutableListOf<String>()
    private val actions = ChatActions(
        onOpenVoice = { events += "dictate" },
        onOpenHandsFree = { events += "hands-free" },
        onReasoning = { events += "reasoning:$it" },
        onWebMode = { events += "web:$it" },
        haptic = { events += "haptic:$it" },
    )

    private fun show(state: ChatUiState = home, sheet: ChatSheet? = null) =
        compose.setContent { DuaTheme(LocalPreferences(theme = "dark")) { ChatScreen(state, actions, initialSheet = sheet) } }

    private fun sheetShown() = compose.onAllNodesWithText("Add to chat").fetchSemanticsNodes().isNotEmpty()

    @Test fun micTapDictates() {
        show()
        compose.onNodeWithContentDescription("Voice input", substring = true).performClick()
        assertEquals(listOf("dictate"), events)
    }

    @Test fun micLongPressStartsHandsFreeWithHaptic() {
        show()
        compose.onNodeWithContentDescription("Long-press: hands-free voice", substring = true).performTouchInput { longClick() }
        assertEquals(listOf("haptic:LongPress", "hands-free"), events)
    }

    @Test fun swipeUpOnInputCardOpensPlusSheet() {
        show()
        compose.onNodeWithContentDescription("Message").performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f) }
        compose.waitForIdle()
        assertTrue(sheetShown())
        assertTrue("haptic:Tap" in events)
    }

    @Test fun swipeUpOnChipsOpensPlusSheet() {
        show()
        compose.onNodeWithContentDescription("Think before answering").performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f) }
        compose.waitForIdle()
        assertTrue(sheetShown())
        assertTrue("reasoning:true" !in events)
    }

    @Test fun swipeUpInsideFocusedTextFieldDoesNothing() {
        show()
        val field = compose.onNodeWithContentDescription("Message")
        field.performClick()
        field.performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f) }
        compose.waitForIdle()
        compose.onAllNodesWithText("Add to chat").assertCountEquals(0)
    }

    @Test fun shortOrSidewaysOrDownwardDragsDoNothing() {
        show()
        val field = compose.onNodeWithContentDescription("Message")
        field.performTouchInput { swipeUp(startY = centerY, endY = centerY - 40f) } // ~15 dp: below the 40 dp threshold
        field.performTouchInput { swipeLeft() }
        field.performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onAllNodesWithText("Add to chat").assertCountEquals(0)
    }

    @Test fun chipToggleGivesLightHaptic() {
        show()
        compose.onNodeWithContentDescription("Think before answering").performClick()
        assertEquals(listOf("haptic:Tap", "reasoning:true"), events)
    }

    @Test fun plusSheetSwitchGivesLightHaptic() {
        show(conversation, ChatSheet.Attach)
        compose.waitForIdle()
        // Semantics click: the sheet's window doesn't take injected touches reliably under Robolectric.
        compose.onNode(isToggleable() and hasText("Reasoning")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("haptic:Tap", "reasoning:true"), events)
    }

    @Test fun hapticConstants() {
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, ChatHaptic.Tap.constant(34))
        assertEquals(HapticFeedbackConstants.CONFIRM, ChatHaptic.Confirm.constant(34))
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, ChatHaptic.Confirm.constant(29))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, ChatHaptic.LongPress.constant(34))
    }
}
