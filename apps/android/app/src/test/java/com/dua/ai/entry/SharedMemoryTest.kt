package com.dua.ai.entry

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.*
import com.dua.ai.viewmodel.ChatViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedMemoryTest {
    private val store = ViewModelStore()
    private lateinit var vm: ChatViewModel
    private val owner = "11111111-1111-4111-8111-111111111111"
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        for (key in listOf("dua_chat_history", "dua_preferences", "dua_account_session", "dua_auto_sync")) app.getSharedPreferences(key, 0).edit().clear().commit()
        vm = ChatViewModel(app).also { store.put("memory", it) }
    }
    private fun signIn() {
        val repository = AccountRepository(AccountApi(), object : SessionPersistence {
            override fun read() = AccountSession(AccountUser(owner, "owner@example.com", "Owner"), "mock-access", "mock-refresh", Long.MAX_VALUE / 1000)
            override fun write(value: AccountSession?) = Unit
        })
        ChatViewModel::class.java.getDeclaredField("account").apply { isAccessible = true }.set(vm, repository)
    }
    @After fun cleanup() { store.clear() }
    @Test fun guestAndChangedAccountCannotSaveSharedNotes() {
        assertNotNull(vm.confirmSharedMemory("A note", owner)); assertTrue(vm.localSettings.value.memory.isEmpty())
        signIn(); assertNotNull(vm.confirmSharedMemory("A note", "different-owner")); assertTrue(vm.localSettings.value.memory.isEmpty())
    }
    @Test fun confirmedNotePersistsInExistingCacheWithoutEnablingMemoryAndRespectsLimits() {
        signIn()
        assertNull(vm.confirmSharedMemory(" I  prefer Hindi ", owner))
        assertEquals(listOf("I prefer Hindi"), vm.localSettings.value.memory); assertFalse(vm.localSettings.value.memoryEnabled)
        assertNull(vm.confirmSharedMemory("I prefer Hindi", owner)); assertEquals(1, vm.localSettings.value.memory.size)
        assertNotNull(vm.confirmSharedMemory("x".repeat(201), owner)); assertEquals(1, vm.localSettings.value.memory.size)
        val restored = ChatViewModel(RuntimeEnvironment.getApplication()).also { store.put("restored", it) }
        assertEquals(vm.localSettings.value.memory, restored.localSettings.value.memory)
    }
}
