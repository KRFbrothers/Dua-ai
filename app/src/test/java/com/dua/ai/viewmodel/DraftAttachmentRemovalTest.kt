package com.dua.ai.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.PendingUpload
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
class DraftAttachmentRemovalTest {
    private lateinit var vm: ChatViewModel
    private val store = ViewModelStore()
    private val first = ChatAttachment("first.txt", "text/plain", "Zmlyc3Q=")
    private val second = ChatAttachment("second.txt", "text/plain", "c2Vjb25k")
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences")) app.getSharedPreferences(name, 0).edit().clear().commit()
        vm = ChatViewModel(app).also { store.put("vm", it) }
        (ChatViewModel::class.java.getDeclaredField("engineCheck").apply { isAccessible = true }.get(vm) as? Job)?.cancel()
        state<List<ChatAttachment>>("_attachments").value = listOf(first, second)
        vm.updateDraft("Keep draft")
    }
    @After fun cleanup() { store.clear() }
    @Suppress("UNCHECKED_CAST")
    private fun <T> state(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(vm) as MutableStateFlow<T>
    @Test fun removesOnlySelectedInlineFileWithoutDiscardingUploadsOrDraft() {
        val pending = PendingUpload("pending", "report.pdf", "application/pdf", 100)
        state<List<PendingUpload>>("_uploads").value = listOf(pending)
        vm.removeAttachment(0)
        assertEquals(listOf(second), vm.attachments.value)
        assertEquals(listOf(pending), vm.uploads.value)
        assertEquals("Keep draft", vm.draft.value)
        vm.removeAttachment(99)
        assertEquals(listOf(second), vm.attachments.value)
    }
    @Test fun cannotRemoveFilesFromAnActiveResponse() {
        state<Boolean>("_isTyping").value = true
        vm.removeAttachment(0)
        assertEquals(listOf(first, second), vm.attachments.value)
    }
}
