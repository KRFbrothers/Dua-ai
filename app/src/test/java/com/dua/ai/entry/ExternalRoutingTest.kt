package com.dua.ai.entry

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.SessionPersistence
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.navigation.DuaDestination
import com.dua.ai.ui.navigation.DuaNavHost
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.voice.rememberReplyVoice
import com.dua.ai.viewmodel.ChatViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ExternalRoutingTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var vm: ChatViewModel
    private lateinit var accountClient: OkHttpClient
    private lateinit var nav: NavHostController
    private var entry by mutableStateOf<ExternalEntry?>(null)
    private var consumed = 0
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        for (key in listOf("dua_chat_history", "dua_preferences", "dua_account_session", "dua_auto_sync", "dua_files", "dua_onboarding")) app.getSharedPreferences(key, 0).edit().clear().commit()
        vm = ChatViewModel(app).also { store.put("chat", it) }
        // Onboarding loads account capabilities. Never contact the production backend from UI tests.
        accountClient = OkHttpClient.Builder().addInterceptor { chain ->
            check(chain.request().url.host == "account.test")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"configured\":false,\"emailAuth\":false}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repository = AccountRepository(AccountApi("https://account.test", accountClient), object : SessionPersistence {
            override fun read(): AccountSession? = null
            override fun write(value: AccountSession?) = Unit
        })
        ChatViewModel::class.java.getDeclaredField("account").apply { isAccessible = true }.set(vm, repository)
    }
    @After fun close() {
        store.clear(); accountClient.dispatcher.cancelAll(); accountClient.connectionPool.evictAll(); accountClient.dispatcher.executorService.shutdown()
    }
    private fun show(skip: Boolean = true) {
        if (skip) vm.skipOnboarding()
        compose.setContent {
            val pending = entry
            DuaTheme(LocalPreferences(theme = "light")) {
                val controller = rememberNavController()
                SideEffect { nav = controller }
                DuaNavHost(vm, rememberReplyVoice(vm), null, controller, pending) { consumed++; entry = null }
            }
        }
    }
    @Test fun shareAppendsToActiveDraftOnceAndQueryFocusesItsComposer() {
        vm.updateDraft("Existing draft")
        entry = ExternalEntry(action = EntryAction.Share, text = "Shared text")
        show()
        compose.waitUntil(10000) { consumed == 1 }
        compose.onNodeWithContentDescription("Message").assertIsFocused()
        compose.runOnIdle { assertEquals("Existing draft\n\nShared text", vm.draft.value); assertTrue(vm.messages.value.isEmpty()) }
        compose.runOnIdle { entry = ExternalEntry(action = EntryAction.Query) }
        compose.waitForIdle()
        compose.waitUntil { consumed == 2 }
        compose.onNodeWithContentDescription("Message").assertIsFocused()
        compose.runOnIdle { assertEquals("Existing draft\n\nShared text", vm.draft.value) }
    }
    @Test fun onboardingHoldsShareUntilExplicitSkip() {
        entry = ExternalEntry(action = EntryAction.Share, text = "Held until welcome is done")
        show(skip = false)
        compose.runOnIdle { assertEquals(DuaDestination.Onboarding.route, nav.currentDestination?.route); assertEquals(0, consumed); assertTrue(vm.draft.value.isEmpty()) }
        compose.waitUntil(5000) { !vm.account.state.value.busy }
        compose.onNodeWithText("Skip for now").assertIsEnabled().performClick()
        // Drain the Android looper and navigation transition before polling asynchronous dispatch.
        compose.waitForIdle()
        compose.waitUntil(10000) { consumed == 1 }
        compose.runOnIdle { assertEquals("Held until welcome is done", vm.draft.value) }
    }
    @Test fun screenshareShortcutHasHonestDestinationAndNoMicrophoneOrFakeCapture() {
        entry = ExternalEntry(action = EntryAction.Screenshare)
        show()
        compose.waitUntil(10000) { nav.currentDestination?.route == DuaDestination.Screenshare.route }
        compose.onNodeWithText("Screenshare / Video").assertExists()
        compose.onNodeWithText("Coming soon").assertExists()
        compose.runOnIdle { assertEquals(1, consumed); assertTrue(vm.messages.value.isEmpty()) }
    }
    @Test fun microphoneAndLiveShortcutsOpenExistingVoiceScreensWithoutSending() {
        show()
        for ((action, destination) in listOf(EntryAction.Mic to DuaDestination.Voice, EntryAction.Live to DuaDestination.LiveVoice)) {
            compose.runOnIdle { entry = ExternalEntry(action = action) }
            compose.waitForIdle()
            compose.waitUntil(10000) { nav.currentDestination?.route == destination.route }
            compose.runOnIdle { assertTrue(vm.messages.value.isEmpty()) }
        }
        compose.runOnIdle { assertEquals(2, consumed) }
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun incomingShareWaitsForBusyDataOperations() {
        val busy = ChatViewModel::class.java.getDeclaredField("_dataBusy").apply { isAccessible = true }.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
        busy.value = true
        entry = ExternalEntry(action = EntryAction.Share, text = "Wait for import")
        show()
        compose.runOnIdle { assertEquals(0, consumed); assertTrue(vm.draft.value.isEmpty()); busy.value = false }
        compose.waitForIdle()
        compose.waitUntil(10000) { consumed == 1 }
        compose.runOnIdle { assertEquals("Wait for import", vm.draft.value); busy.value = true; entry = ExternalEntry(action = EntryAction.Remember, text = "A note") }
        compose.onNodeWithContentDescription("Memory note").assertDoesNotExist()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithContentDescription("Memory note").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("Wait for import", vm.draft.value); assertEquals(2, consumed) }
    }
    @Test fun rememberShareRequiresReviewInsteadOfChangingDraftOrMemory() {
        entry = ExternalEntry(action = EntryAction.Remember, text = "I like Hindi")
        show()
        compose.onNodeWithContentDescription("Memory note").assertExists()
        compose.runOnIdle { assertTrue(vm.localSettings.value.memory.isEmpty()); assertTrue(vm.draft.value.isEmpty()); assertEquals(0, consumed) }
        compose.onNodeWithText("Cancel", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, consumed); assertTrue(vm.localSettings.value.memory.isEmpty()) }
    }
}
