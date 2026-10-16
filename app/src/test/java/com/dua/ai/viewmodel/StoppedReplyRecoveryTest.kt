package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.imagine.ImagineInfo
import com.dua.ai.model.BuildInfo
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.CodeRun
import com.dua.ai.model.ReplyRetryPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercise public Stop/chat-switch paths without starting a provider request. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class StoppedReplyRecoveryTest {
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private val store = ViewModelStore()
    private val prompt = ChatMessage(id = "prompt", text = "Explain Kotlin coroutines", isUser = true, model = "Gemini")

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) {
            application.getSharedPreferences(name, 0).edit().clear().commit()
        }
        model = ChatViewModel(application).also { store.put("chat", it) }
    }

    @After fun teardown() { store.clear() }

    @Suppress("UNCHECKED_CAST")
    private fun <T> state(name: String): MutableStateFlow<T> = field(name) as MutableStateFlow<T>
    private fun field(name: String): Any? = ChatViewModel::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(model)
    private fun setField(name: String, value: Any?) {
        ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.set(model, value)
    }
    private fun streaming(reply: ChatMessage, original: ChatMessage? = null): Job {
        state<List<ChatMessage>>("_messages").value = listOf(prompt, reply)
        state<Boolean>("_isTyping").value = true
        setField("retryOriginal", original)
        return Job().also { setField("replyJob", it) }
    }
    private fun blankReply() = ChatMessage(id = "reply", text = "", isUser = false, model = "Gemini", state = ChatMessage.STREAMING)

    @Test fun snippetRunCannotUseBaseProviderGateToPretendSeparateEngineExecutionWorks() {
        setField("api", com.dua.ai.network.DuaApiClient("https://chat.example", apiBaseUrl = "https://base.example"))
        state<String>("_selectedModel").value = "Gemini"
        state<Boolean>("_online").value = true
        state<com.dua.ai.setup.IntegrationState>("_integrations").value = com.dua.ai.setup.IntegrationState(
            codeExecution = com.dua.ai.setup.CodeExecutionConfig(true, providers = setOf("Gemini")))
        state<List<ChatMessage>>("_messages").value = listOf(prompt)
        assertTrue(model.codeSnippetUnavailableReason()!!.contains("active Cloud Run chat engine"))
        assertFalse(model.runCodeSnippet("print(42)"))
        assertEquals(listOf(prompt), model.messages.value)
        assertFalse(model.isTyping.value)
        assertFalse(model.runCode.value)
    }

    @Test fun snippetRunDoesNotOverwriteAnExistingDraft() {
        setField("api", com.dua.ai.network.DuaApiClient("https://api.example"))
        state<String>("_selectedModel").value = "Gemini"
        state<Boolean>("_online").value = true
        state<com.dua.ai.setup.IntegrationState>("_integrations").value = com.dua.ai.setup.IntegrationState(
            codeExecution = com.dua.ai.setup.CodeExecutionConfig(true, providers = setOf("Gemini")))
        state<String>("_draft").value = "My unsent message"
        assertFalse(model.runCodeSnippet("print(42)"))
        assertEquals("My unsent message", model.draft.value)
        assertFalse(model.isTyping.value)
        assertFalse(model.runCode.value)
    }

    @Test fun stopBeforeFirstTokenKeepsARetryableTurnAndPersistsIt() {
        val reply = blankReply()
        val job = streaming(reply)

        model.stopReply()

        val stopped = reply.copy(state = "stopped")
        assertEquals(listOf(prompt, stopped), model.messages.value)
        assertFalse(model.isTyping.value)
        assertTrue(job.isCancelled)
        assertEquals("Response stopped.", model.error.value)
        assertEquals(listOf(prompt), ReplyRetryPlan.create(model.messages.value, stopped.id).context)
        model.flushHistory()
        val raw = application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!
        assertEquals(listOf(prompt, stopped), ChatHistoryCodec.decode(raw).second.single().messages)
    }

    @Test fun stopBeforeAnswerTextKeepsThinkingAndExecutedCode() {
        val reply = blankReply().copy(thinking = "Checking the example", thinkingSeconds = 3,
            codeRuns = listOf(CodeRun(code = "print(2 + 2)", output = "4\n", outcome = "ok")),
            notice = "Provider note", build = BuildInfo("KRFbrothers/Dua-ai"))
        streaming(reply)

        model.stopReply()

        assertEquals(reply.copy(state = "stopped"), model.messages.value.last())
        assertEquals(listOf(prompt), ReplyRetryPlan.create(model.messages.value, reply.id).context)
    }

    @Test fun stopAnEmptyRetryRestoresTheOriginalReply() {
        val original = blankReply().copy(text = "The original explanation", state = "complete",
            thinking = "Original reasoning", thinkingSeconds = 5,
            codeRuns = listOf(CodeRun(code = "print('original')", output = "original", outcome = "ok")))
        val job = streaming(blankReply().copy(thinking = "Trying again"), original)

        model.stopReply()

        assertEquals(listOf(prompt, original), model.messages.value)
        assertTrue(job.isCancelled)
        assertFalse(model.isTyping.value)
        assertNull(field("retryOriginal"))
    }

    @Test fun stopAPartialRetryKeepsTheNewReplyAndItsFields() {
        val original = blankReply().copy(text = "Old answer", state = "complete")
        val partial = blankReply().copy(text = "New partial answer", thinking = "New reasoning", thinkingSeconds = 2,
            codeRuns = listOf(CodeRun(code = "print('new')", output = "new", outcome = "ok")))
        streaming(partial, original)

        model.stopReply()

        assertEquals(partial.copy(state = "stopped"), model.messages.value.last())
        assertEquals("Response stopped. Partial reply kept.", model.error.value)
        assertNull(field("retryOriginal"))
    }

    @Test fun switchingAwayBeforeFirstTokenKeepsTheRetryableReplyInItsChat() {
        val reply = blankReply()
        streaming(reply)
        val chatId = model.activeChatId.value

        model.startNewChat()
        model.openChat(chatId)

        assertEquals(listOf(prompt, reply.copy(state = "stopped")), model.messages.value)
        assertEquals(listOf(prompt), ReplyRetryPlan.create(model.messages.value, reply.id).context)
        assertFalse(model.isTyping.value)
    }

    @Test fun stopBeforeImageResultsRemovesItsPendingPlaceholder() {
        assertStoppedImaginePlaceholderIsRemoved(ImagineInfo.IMAGE)
    }

    @Test fun stopBeforeVideoJobStartsRemovesItsPendingPlaceholder() {
        assertStoppedImaginePlaceholderIsRemoved(ImagineInfo.VIDEO)
    }

    private fun assertStoppedImaginePlaceholderIsRemoved(kind: String) {
        val reply = blankReply().copy(imagine = ImagineInfo(kind = kind, prompt = prompt.text,
            status = ImagineInfo.PENDING))
        val job = streaming(reply)

        model.stopReply()

        assertEquals(listOf(prompt), model.messages.value)
        assertFalse(model.isTyping.value)
        assertTrue(job.isCancelled)
        model.flushHistory()
        val raw = application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!
        assertEquals(listOf(prompt), ChatHistoryCodec.decode(raw).second.single().messages)
    }
}
