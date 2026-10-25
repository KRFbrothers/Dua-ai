package com.dua.ai.ui.agent

import android.app.Application
import com.dua.ai.attachments.ChatAttachment
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.chat.ChatRoute
import com.dua.ai.ui.voice.rememberReplyVoice
import com.dua.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode

/**
 * Route callbacks use the real ViewModel; draft-only actions make no backend/provider requests.
 * Activate navigation through accessibility after modal transitions so these callback assertions
 * do not depend on Robolectric's native dialog touch routing. Feature UI tests retain touch clicks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class AgentRouteTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val previous = SavedChat("existing-chat", "Dua", "Unsent draft",
        listOf(ChatMessage(id = "previous", text = "Previous question", isUser = true)), 123)
    private var openedChat = 0

    @Before fun setup() {
        val application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) {
            application.getSharedPreferences(name, 0).edit().clear().commit()
        }
        application.getSharedPreferences("dua_chat_history", 0).edit()
            .putString("history", ChatHistoryCodec.encode(listOf(previous), previous.id)).commit()
        model = ChatViewModel(application).also { store.put("chat", it) }
        (ChatViewModel::class.java.getDeclaredField("engineCheck").apply { isAccessible = true }.get(model) as? Job)?.cancel()
        @Suppress("UNCHECKED_CAST")
        val capability = ChatViewModel::class.java.getDeclaredField("_agentAvailable").apply { isAccessible = true }
            .get(model) as MutableStateFlow<Boolean>
        capability.value = true // Prevent the route's availability effect from making a network request.
    }
    private fun show(home: Boolean = false) {
        compose.setContent {
            DuaTheme(LocalPreferences()) {
                if (home) ChatRoute(model, rememberReplyVoice(model), onOpenSettings = {}, onOpenVoice = {}, onOpenSetup = {})
                else AgentScreen(model, onBack = { openedChat++ })
            }
        }
    }
    @After fun teardown() { store.clear() }

    @Test fun researchOpensANewAgentChatAndPreservesThePreviousConversation() {
        show()
        compose.onNodeWithText("Deep Web Research").performScrollTo().performClick()
        assertTrue("Agent available=${model.agentAvailable.value}, typing=${model.isTyping.value}", model.agent.value)
        assertFalse(model.reasoning.value)
        assertNotEquals(previous.id, model.activeChatId.value)
        assertTrue(model.messages.value.isEmpty())
        assertEquals("", model.draft.value)
        assertEquals(previous, model.savedChats.value.single { it.id == previous.id })
        assertEquals(0, openedChat)
        compose.onNodeWithTag("feature-top-bar").assertIsDisplayed()
        compose.onNodeWithText("Chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Model, Dua").assertDoesNotExist()
    }

    @Test fun repoTaskPrefillsThePromptWithoutStartingGeneration() {
        show()
        compose.onNodeWithText("GitHub Repo & PR Assistant").performScrollTo().performClick()
        assertTrue("Agent available=${model.agentAvailable.value}, typing=${model.isTyping.value}", model.agent.value)
        assertEquals(GITHUB_AGENT_PROMPT, model.draft.value)
        assertFalse(model.isTyping.value)
        assertTrue(model.messages.value.isEmpty())
        assertEquals(previous, model.savedChats.value.single { it.id == previous.id })
        assertEquals(0, openedChat)
        compose.onNodeWithTag("feature-top-bar").assertIsDisplayed()
        compose.onNodeWithText("Chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Model, Dua").assertDoesNotExist()
    }
    @Test fun plusAgentUsesDedicatedViewAndNewSessionKeepsAgentMode() {
        val file = ChatAttachment("notes.txt", "text/plain", "bm90ZQ==")
        @Suppress("UNCHECKED_CAST")
        val selectedFiles = ChatViewModel::class.java.getDeclaredField("_attachments").apply { isAccessible = true }
            .get(model) as MutableStateFlow<List<ChatAttachment>>
        selectedFiles.value = listOf(file)
        show(home = true)
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Agent", substring = false).performScrollTo().performClick()
        assertTrue("Agent available=${model.agentAvailable.value}, typing=${model.isTyping.value}", model.agent.value)
        assertEquals(previous.id, model.activeChatId.value)
        assertEquals(previous.messages, model.messages.value) // Mode UI must not drop the existing context.
        assertEquals(listOf(file), model.attachments.value)
        assertEquals(previous.draft, model.draft.value)
        compose.onNodeWithText("Previous question").assertDoesNotExist()
        compose.onNodeWithTag("feature-top-bar").assertIsDisplayed()
        compose.onNodeWithText("Chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Model, Dua").assertDoesNotExist()
        compose.onNodeWithText("Deep Web Research").performScrollTo().assertIsDisplayed()
        model.updateDraft("Unsent agent prompt")
        val id = model.activeChatId.value
        compose.onNodeWithContentDescription("New AI Agent session").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        assertTrue("Agent available=${model.agentAvailable.value}, typing=${model.isTyping.value}", model.agent.value) // In particular, the handler must not capture the initial false mode.
        assertNotEquals(id, model.activeChatId.value)
        assertEquals("", model.draft.value)
        compose.onNodeWithText("AI Agent").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        assertFalse(model.agent.value)
        compose.onNodeWithTag("supplied-chat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dua AI, chats and settings").performClick()
        compose.onNodeWithContentDescription("Open drawer profile and settings").assertIsDisplayed()
        assertEquals(previous.messages, model.savedChats.value.single { it.id == previous.id }.messages)
        assertEquals("Unsent agent prompt", model.savedChats.value.single { it.id == previous.id }.draft)
    }
    @Test fun streamedAndCompletedAgentOutputStayInTheAgentDestination() {
        show()
        compose.onNodeWithText("Deep Web Research").performScrollTo().performClick()
        @Suppress("UNCHECKED_CAST")
        val messages = ChatViewModel::class.java.getDeclaredField("_messages").apply { isAccessible = true }
            .get(model) as MutableStateFlow<List<ChatMessage>>
        for (status in listOf(ChatMessage.STREAMING, "complete")) {
            compose.runOnIdle { messages.value = listOf(ChatMessage("u", "Research", true),
                ChatMessage("a", "Agent result", false, state = status, thinking = "Read the sources")) }
            compose.onNodeWithText("AI Agent").assertIsDisplayed()
            compose.onNodeWithText("Agent result").assertIsDisplayed()
            compose.onNodeWithText("Deep Web Research").assertDoesNotExist()
            compose.onNodeWithContentDescription("Model, Dua").assertDoesNotExist()
            assertEquals(0, openedChat)
        }
    }

    @Test fun suppliedChatEntersAgentAndRestoresThePreviousConversationFromHistory() {
        show(home = true)
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Agent", substring = false).performScrollTo().performClick()
        assertTrue(model.agent.value)
        assertEquals(previous.id, model.activeChatId.value)
        assertEquals(previous.messages, model.messages.value)
        assertEquals(previous.draft, model.draft.value)
        compose.onNodeWithTag("feature-top-bar").assertIsDisplayed()
        compose.onNodeWithTag("supplied-chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        assertFalse(model.agent.value)
        // The existing feature exit starts a fresh chat; the preceding conversation remains restorable.
        compose.onNodeWithTag("supplied-chat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dua AI, chats and settings").performClick()
        compose.onNodeWithText(previous.title).performScrollTo().performClick()
        compose.onNodeWithTag("message-previous").assertIsDisplayed()
        assertEquals(previous.id, model.activeChatId.value)
        assertEquals(previous.messages, model.messages.value)
        assertEquals(previous.draft, model.draft.value)
        compose.onNodeWithContentDescription("Message").assertTextContains(previous.draft)
    }

}
