package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.dua.ai.imagine.CloudImagine
import com.dua.ai.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ImageHubGuardTest {
    private lateinit var vm: ChatViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) app.getSharedPreferences(name, 0).edit().clear().commit()
        vm = ChatViewModel(app).also { store.put("vm", it) }
        (ChatViewModel::class.java.getDeclaredField("engineCheck").apply { isAccessible = true }.get(vm) as? Job)?.cancel()
        state<CloudImagine?>("_cloudImagine").value = CloudImagine("image-model", 10, listOf("1:1"))
        vm.updateDraft("Keep my unrelated draft")
    }
    @After fun cleanup() { store.clear() }
    @Suppress("UNCHECKED_CAST")
    private fun <T> state(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(vm) as MutableStateFlow<T>
    @Test fun invalidImageInputDoesNotClearTheExistingChatOrDraft() {
        val before = vm.activeChatId.value
        for (prompt in listOf("", "x".repeat(4001))) assertFalse(vm.generateImageFromHub(prompt, "1:1"))
        assertEquals(before, vm.activeChatId.value)
        assertEquals("Keep my unrelated draft", vm.draft.value)
        assertTrue(vm.messages.value.isEmpty())
    }
    @Test fun signedOutImageSubmissionCannotFallThroughToOrdinaryChat() {
        assertFalse(vm.generateImageFromHub("Create a landscape", "1:1"))
        assertTrue(vm.error.value.orEmpty().contains("Sign in"))
        assertFalse(vm.isTyping.value)
        assertTrue(vm.messages.value.isEmpty())
        assertEquals("Keep my unrelated draft", vm.draft.value)
    }
    @Test fun activeResponseCannotBeCancelledByStartingAnImage() {
        val message = ChatMessage("reply", "Existing response", false, state = ChatMessage.STREAMING)
        state<List<ChatMessage>>("_messages").value = listOf(message)
        state<Boolean>("_isTyping").value = true
        assertFalse(vm.generateImageFromHub("Image", "1:1"))
        assertEquals(listOf(message), vm.messages.value)
        assertTrue(vm.isTyping.value)
    }
}
