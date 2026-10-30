package com.dua.ai.ui.chat

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
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
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
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
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatWiringUiTest(private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "large={0}")
        fun sizes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun show(state: State<ChatUiState>, actions: ChatActions = ChatActions()) {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = "light")) {
                    ChatScreen(state.value, actions)
                }
            }
        }
        keyboard(0)
    }
    private fun keyboard(height: Int) {
        compose.runOnUiThread {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
                .setVisible(WindowInsetsCompat.Type.ime(), height > 0).build())
        }
        compose.waitForIdle()
    }
    private fun capture(label: String) {
        val file = File("build/approved-ui/chat-wired-$label-$large.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }

    @Test fun sendUsesOwnerDraftAndRealStreamWithoutTimerOrTypewriterReplay() {
        val state = mutableStateOf(ChatUiState(profileName = "Amina Khan"))
        var accept = false
        var sends = 0
        val actions = ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) }, onSend = {
            sends++
            if (accept) state.value = state.value.copy(draft = "", typing = true,
                messages = listOf(ChatMessage(id = "user", text = state.value.draft, isUser = true)))
        }, onStop = { state.value = state.value.copy(typing = false,
            messages = state.value.messages.map { if (it.streaming) it.copy(state = "stopped") else it }) })
        show(state, actions)
        compose.onNodeWithText("Hi Amina,", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Dua AI").assertIsDisplayed()
        compose.onNodeWithText("Hi Another,", substring = true).assertDoesNotExist()
        capture("welcome")
        val field = compose.onNodeWithContentDescription("Message")
        field.performClick().performTextInput("Explain this")
        compose.onNodeWithContentDescription("Send message").performClick()
        field.assertIsFocused().assertTextContains("Explain this")
        assertEquals(1, sends)
        compose.runOnIdle { accept = true }
        field.performImeAction()
        assertEquals(2, sends)
        field.assertIsNotFocused()
        compose.onNodeWithContentDescription("Dua is thinking").assertIsDisplayed()
        compose.onNodeWithContentDescription("Stop response").assertIsEnabled()
        capture("waiting")
        compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages +
            ChatMessage(id = "server", text = "Server token one", isUser = false, state = ChatMessage.STREAMING)) }
        compose.onNodeWithText("Server token one").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dua is thinking").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(messages = state.value.messages.map {
            if (it.id == "server") it.copy(text = "Server token one and token two") else it }) }
        compose.onNodeWithText("Server token one and token two").assertIsDisplayed()
        capture("streaming")
        compose.onNodeWithContentDescription("Stop response").performClick()
        compose.onNodeWithText("Stopped · partial reply").assertIsDisplayed()
        compose.onNodeWithText("Server token one and token two").assertIsDisplayed()
        compose.onNodeWithText("Bilkul makkhan UI!", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Dictate").assertIsDisplayed()
    }

    @Test fun attachmentPickersAndVoiceUseTheExistingHandlers() {
        val hits = mutableListOf<String>()
        show(mutableStateOf(ChatUiState()), ChatActions(onPickPhotos = { hits += "photos" },
            onPickCamera = { hits += "camera" }, onPickFiles = { hits += "files" },
            onOpenVoice = { hits += "voice" }))
        compose.onNodeWithContentDescription("Dictate").assertHeightIsAtLeast(48.dp).performClick()
        for (label in listOf("Photos", "Camera", "Files")) {
            compose.onNodeWithContentDescription("Add photos and files").performClick()
            compose.onNodeWithText("Add to chat").assertIsDisplayed()
            compose.onNodeWithText(label).performScrollTo().performClick()
        }
        assertEquals(listOf("voice", "photos", "camera", "files"), hits)
    }

    @Test fun drawerRestoresSavedChatAndSettingsAndModelChoiceRemainsReachable() {
        val messages = listOf(ChatMessage(id = "saved-user", text = "Saved request", isUser = true),
            ChatMessage(id = "saved-answer", text = "Saved server answer", isUser = false))
        val chat = SavedChat("saved", "Gemini", "Saved draft", messages, 1L, customTitle = "Saved project")
        val state = mutableStateOf(ChatUiState(savedChats = listOf(chat)))
        var settings = 0
        show(state, ChatActions(onOpenChat = { state.value = state.value.copy(chatId = it, messages = chat.messages, draft = chat.draft) },
            onOpenSettings = { settings++ }))
        val title = compose.onNodeWithContentDescription("Open chat history")
        title.assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Saved project").performScrollTo().performClick()
        compose.onNodeWithText("Saved server answer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Message").assertTextContains("Saved draft")
        capture("restored")
        title.performClick()
        compose.onNodeWithContentDescription("Open drawer profile and settings").performClick()
        assertEquals(1, settings)
        compose.onNode(hasContentDescription("Model,", substring = true)).performClick()
        compose.onNodeWithText("Choose a model").assertIsDisplayed()
    }

    @Test fun uploadCancelUsesLatestItemsAndFilesCanSendWithoutText() {
        val ready = PendingUpload("ready", "ready.pdf", "application/pdf", 10,
            state = PendingUpload.DONE, ref = FileRef("ready-path", "ready.pdf", "application/pdf", 10))
        val state = mutableStateOf(ChatUiState(uploads = listOf(PendingUpload("first", "first.pdf", "application/pdf", 100))))
        val cancelled = mutableListOf<String>()
        var sends = 0
        show(state, ChatActions(onSend = { sends++ }, onCancelUpload = { id ->
            cancelled += id; state.value = state.value.copy(uploads = state.value.uploads.filterNot { it.id == id })
        }))
        compose.onNodeWithContentDescription("Send message").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(uploads = listOf(ready,
            PendingUpload("second", "second.pdf", "application/pdf", 100))) }
        compose.onNodeWithContentDescription("Cancel upload").performClick()
        assertEquals(listOf("second"), cancelled)
        assertEquals(listOf(ready), state.value.uploads)
        compose.onNodeWithContentDescription("Send message").assertIsEnabled().performClick()
        assertEquals(1, sends)
        capture("ready-file")
    }

    @Test fun keyboardInsetsAreAppliedOnceAndMultilineDraftKeepsFocus() {
        val state = mutableStateOf(ChatUiState())
        show(state, ChatActions(onDraftChange = { state.value = state.value.copy(draft = it) }))
        val card = compose.onNodeWithTag("supplied-composer")
        val closed = card.fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithContentDescription("Message")
        input.performClick().assertIsFocused()
        keyboard(280)
        assertEquals(closed.bottom - 256f, card.fetchSemanticsNode().boundsInRoot.bottom, 2f)
        input.performTextInput("First line\nSecond line")
        input.assertIsFocused().assertTextContains("Second line", substring = true)
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("keyboard")
        keyboard(0)
        input.assertIsFocused().assertTextContains("Second line", substring = true)
        assertEquals(closed.bottom, card.fetchSemanticsNode().boundsInRoot.bottom, 2f)
    }

    @Test fun codeBlocksDelegateToTheExistingExecutionHandler() {
        var sent: String? = null
        show(mutableStateOf(ChatUiState(messages = listOf(
            ChatMessage(id = "code", text = "```python\nprint(42)\n```", isUser = false)),
            codeSnippetUnavailableReason = null)),
            ChatActions(onRunCodeSnippet = { sent = it; true }))
        compose.onNodeWithContentDescription("Run code").performClick()
        compose.runOnIdle { assertEquals("print(42)", sent) }
        compose.onNodeWithText("Code run requested.", substring = true).assertIsDisplayed()
    }

    @Test fun errorsStayVisibleAndDoNotGenerateOrConsumeTheDraft() {
        val state = mutableStateOf(ChatUiState(draft = "Keep my request", error = "Request could not be sent"))
        show(state)
        compose.onNodeWithText("Request could not be sent").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").performClick()
        compose.mainClock.advanceTimeBy(2400)
        assertTrue(state.value.messages.isEmpty())
        compose.onNodeWithContentDescription("Message").assertTextContains("Keep my request")
        compose.onNodeWithContentDescription("Dua is thinking").assertDoesNotExist()
        capture("error")
    }

    @Test fun workShowsComingSoonWithoutComposerOrChatControls() {
        show(mutableStateOf(ChatUiState(homeTab = HomeTab.Work, draft = "Keep work draft",
            messages = listOf(ChatMessage(text = "Hidden chat", isUser = true)))))
        compose.onNodeWithTag("supplied-chat").assertIsDisplayed()
        compose.onNodeWithText("Coming soon").assertIsDisplayed()
        compose.onNodeWithTag("supplied-composer").assertDoesNotExist()
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        for (description in listOf("Message", "Add photos and files", "Dictate", "Send message")) {
            compose.onNodeWithContentDescription(description).assertDoesNotExist()
        }
        compose.onNodeWithText("Hidden chat").assertDoesNotExist()
        capture("pr4-after-work")
    }

    @Test fun taskSwitcherUsesAgentAndExistingStartersThenRestoresChatLogo() {
        val state = mutableStateOf(ChatUiState(profileName = "Amina Khan", agentAvailable = true))
        val modes = mutableListOf<Boolean>()
        var sends = 0
        show(state, ChatActions(onAgent = {
            modes += it; state.value = state.value.copy(agent = it)
        }, onDraftChange = { state.value = state.value.copy(draft = it) }, onSend = { sends++ }))
        compose.onNodeWithText("Hi Amina,", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Dua AI").assertIsDisplayed()
        compose.onNodeWithText("Ask Dua AI...").assertIsDisplayed()
        compose.onNodeWithTag("home-mode-switcher").assertIsDisplayed()
        capture("pr4-after-chat")
        compose.onNode(hasText("Task") and isSelectable()).performClick().assertIsSelected()
        assertEquals(listOf(true), modes)
        compose.onNodeWithTag("supplied-chat").assertIsDisplayed()
        compose.onNodeWithTag("supplied-task-starters").assertIsDisplayed()
        compose.onNodeWithText("Hi Amina,", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Describe...").assertIsDisplayed()
        val prompts = HomeTaskPromptIds.map { compose.activity.getString(it) }
        for (prompt in prompts) compose.onNodeWithText(prompt).performScrollTo().assertIsDisplayed()
        capture("pr4-after-task")
        compose.onNodeWithText(prompts.first()).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Message").assertTextContains(prompts.first()).assertIsFocused()
        assertEquals(prompts.first(), state.value.draft)
        assertEquals(0, sends)
        compose.runOnIdle { state.value = state.value.copy(draft = "") }
        keyboard(0)
        compose.onNode(hasText("Chat") and isSelectable()).performClick().assertIsSelected()
        assertEquals(listOf(true, false), modes)
        compose.onNodeWithText("Hi Amina,", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Dua AI").assertIsDisplayed()
        compose.onNodeWithText("Ask Dua AI...").assertIsDisplayed()
        compose.onNodeWithTag("supplied-task-starters").assertDoesNotExist()
        assertEquals(0, sends)
    }

    @Test fun homeSwitcherStaysHiddenForMessagesAndPendingReply() {
        val state = mutableStateOf(ChatUiState(agentAvailable = true))
        show(state, ChatActions(onAgent = { state.value = state.value.copy(agent = it) }))
        compose.onNode(hasText("Task") and isSelectable()).performClick()
        compose.runOnIdle { state.value = state.value.copy(typing = true,
            messages = listOf(ChatMessage(text = "Real question", isUser = true))) }
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        compose.onNodeWithTag("supplied-task-starters").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(typing = false, messages = state.value.messages +
            ChatMessage(text = "Real answer", isUser = false)) }
        compose.onNodeWithText("Real answer").assertIsDisplayed()
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        compose.onNodeWithTag("supplied-task-starters").assertDoesNotExist()
    }

    @Test fun unavailableTaskKeepsDraftAndCannotSendPlainChat() {
        val state = mutableStateOf(ChatUiState(draft = "Keep this task", agentAvailable = false))
        var sends = 0
        show(state, ChatActions(onAgent = { state.value = state.value.copy(agent = it) }, onSend = { sends++ }))
        compose.onNode(hasText("Task") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("Message").assertTextContains("Keep this task").performImeAction()
        compose.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        assertEquals(0, sends)
        assertEquals("Keep this task", state.value.draft)
    }


    @Test fun projectCustomHeaderUsesCanonicalPaneAndComposer() {
        compose.setContent { DuaTheme(LocalPreferences(theme = "light")) {
            ChatScreen(ChatUiState(feature = ChatFeature.Projects,
                messages = listOf(ChatMessage(text = "Project reply", isUser = false))), ChatActions(),
                topBar = { androidx.compose.material3.Text("Project chat header") })
        } }
        compose.onNodeWithTag("supplied-chat").assertDoesNotExist()
        compose.onNodeWithText("Project chat header").assertIsDisplayed()
        compose.onNodeWithText("Project reply").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add photos and files").assertIsDisplayed()
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        capture("pr5b-project-chat")
    }

    @Test fun imagesFeatureUsesCanonicalPaneAndFeatureHeader() {
        show(mutableStateOf(ChatUiState(feature = ChatFeature.Images)))
        compose.onNodeWithTag("supplied-chat").assertDoesNotExist()
        compose.onNodeWithText("Images").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add photos and files").assertIsDisplayed()
        compose.onNodeWithTag("home-mode-switcher").assertDoesNotExist()
        capture("pr5b-images")
    }
}
