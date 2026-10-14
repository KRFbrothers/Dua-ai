package com.dua.ai.ui.agent

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Route callbacks use the real ViewModel; draft-only actions make no backend/provider requests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
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
        @Suppress("UNCHECKED_CAST")
        val capability = ChatViewModel::class.java.getDeclaredField("_agentAvailable").apply { isAccessible = true }
            .get(model) as MutableStateFlow<Boolean>
        capability.value = true // Prevent the route's availability effect from making a network request.
        compose.setContent {
            DuaTheme(LocalPreferences()) { AgentScreen(model, onBack = {}, onOpenChat = { openedChat++ }) }
        }
    }
    @After fun teardown() { store.clear() }

    @Test fun researchOpensANewAgentChatAndPreservesThePreviousConversation() {
        compose.onNodeWithText("Deep Web Research").performScrollTo().performClick()
        assertTrue(model.agent.value)
        assertFalse(model.reasoning.value)
        assertNotEquals(previous.id, model.activeChatId.value)
        assertTrue(model.messages.value.isEmpty())
        assertEquals("", model.draft.value)
        assertEquals(previous, model.savedChats.value.single { it.id == previous.id })
        assertEquals(1, openedChat)
    }

    @Test fun repoTaskPrefillsThePromptWithoutStartingGeneration() {
        compose.onNodeWithText("GitHub Repo & PR Assistant").performScrollTo().performClick()
        assertTrue(model.agent.value)
        assertEquals(GITHUB_AGENT_PROMPT, model.draft.value)
        assertFalse(model.isTyping.value)
        assertTrue(model.messages.value.isEmpty())
        assertEquals(previous, model.savedChats.value.single { it.id == previous.id })
        assertEquals(1, openedChat)
    }
}
