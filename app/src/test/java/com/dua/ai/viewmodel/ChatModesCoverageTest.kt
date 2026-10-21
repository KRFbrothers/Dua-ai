package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.SessionPersistence
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.IntegrationState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The mode behaviour [ChatModesModeFreezeTest] does not reach, added before `ChatModes.kt` is
 * extracted so the move has a net under the parts the freeze test never exercised:
 *
 *  - [ChatViewModel.replyWebMode] — the public reader of a reply's frozen web mode. The freeze
 *    test reads the backing map by reflection; nothing called the function itself.
 *  - [ChatViewModel.setReasoning] — its typing lock, and the exclusivity it applies on enable
 *    (Imagine and Agent off, Build left). This is also the only caller of `leaveBuild()` from a
 *    mode toggle that no test drove.
 *  - [ChatViewModel.selectAuto] — the Auto chip clears every mode, and does nothing mid-reply.
 *  - the `"page"` arm of `webModeError` and the invalid-mode guard in [ChatViewModel.setWebMode].
 *    The freeze test only drove `"search"`.
 *
 * Harness: identical to [ChatModesModeFreezeTest] / [RetryResponseTest] — real ViewModel, real
 * client with the network intercepted in front of it, `@LooperMode(PAUSED)`, no background writer
 * left running at teardown.
 *
 * Waits: none. Every assertion reads state that the call under test sets synchronously, so the
 * paused looper is never pumped in a test body and nothing blocks in the interceptor. There is no
 * `Thread.sleep`, no `CountDownLatch`, and no JUnit `Timeout` rule — that rule moves the body to
 * another thread while Robolectric binds the main Looper to the runner thread, which is the shape
 * of the hang this repo already hit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatModesCoverageTest {
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private lateinit var client: OkHttpClient
    private val store = ViewModelStore()
    /** The account stack from [setup]; held so [teardown] can shut down the pool it opened. */
    private var accountClient: OkHttpClient? = null

    private fun field(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> flow(name: String) = field(name).get(model) as MutableStateFlow<T>
    @Suppress("UNCHECKED_CAST") private fun <T> map(name: String) = field(name).get(model) as MutableMap<String, T>

    @Before fun setup() {
        application = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            application.getSharedPreferences(name, 0).edit().clear().commit()
        runBlocking { AppearanceStore(application).reset() }
        DevicePreferences(application).update(LocalPreferences(memoryEnabled = false))
        model = ChatViewModel(application).also { store.put("chat", it) }
        for (name in listOf("engineCheck", "cloudImagineCheck")) (field(name).get(model) as? Job)?.cancel()
        // Signed out, and any stray account call is a test bug rather than a quiet network hop.
        field("account").set(model, AccountRepository(AccountApi("https://account.test", rejectingClient()), object : SessionPersistence {
            override fun read(): AccountSession? = null
            override fun write(value: AccountSession?) = Unit
        }))
        field("appearanceLoaded").set(model, true)
        // Nothing in this file streams a reply, so the client refuses every request loudly: a mode
        // toggle that started talking to the network would fail here instead of passing quietly.
        client = OkHttpClient.Builder().addInterceptor { chain ->
            throw AssertionError("Unexpected test HTTP request: ${chain.request().url}")
        }.build()
        field("api").set(model, DuaApiClient("https://chat.test", client, apiBaseUrl = "https://chat.test"))
        flow<Boolean>("_agentAvailable").value = false
        flow<Boolean>("_online").value = true
        flow<Boolean>("_isTyping").value = false
        applyIntegrations(IntegrationState())
    }

    /**
     * Stops what this test started. Deliberately does NOT call [ViewModelStore.clear]: `onCleared()`
     * touches the drive rebinder, the backup delegate, the recorder and every poll job on the
     * paused Looper, and one blocked step there hangs the Gradle worker with no test-level failure.
     */
    @After fun teardown() {
        runCatching { (field("replyJob").get(model) as? Job)?.cancel() }
        runCatching { model.viewModelScope.cancel() }
        runCatching { repeat(25) { shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.MILLISECONDS) } }
        if (::client.isInitialized) closeHttp(client)
        accountClient?.let(::closeHttp)
    }

    /** Shuts an OkHttp stack down and waits (bounded) so no pool thread outlives this test. */
    private fun closeHttp(http: OkHttpClient) {
        runCatching {
            http.dispatcher.cancelAll()
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
            http.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    private fun applyIntegrations(state: IntegrationState) { flow<IntegrationState>("_integrations").value = state }

    /** The account API is never used here; refusing loudly keeps a stray call visible. */
    private fun rejectingClient() = OkHttpClient.Builder().addInterceptor { chain ->
        throw AssertionError("Unexpected account request: ${chain.request().url}")
    }.build().also { accountClient = it }

    // ---- 1. the public reader of a reply's frozen web mode ----

    @Test fun replyWebModeAnswersForTheFrozenReplyAndForNothingElse() {
        map<String?>("replyWebModes")[REPLY] = "search"
        map<String?>("replyWebModes")[OTHER] = null

        assertEquals("search", model.replyWebMode(REPLY))
        assertNull("a reply frozen with the mode off must answer null, not the composer's value", model.replyWebMode(OTHER))
        assertNull("an unknown id must not inherit another reply's mode", model.replyWebMode("missing"))
        // The reader is a pure lookup: asking must not create an entry.
        assertEquals(setOf(REPLY, OTHER), map<String?>("replyWebModes").keys)
    }

    // ---- 2. Reasoning: typing lock, and what enabling it turns off ----

    @Test fun reasoningIsLockedWhileTypingAndTakesOverImagineAgentAndBuildWhenEnabled() {
        flow<Boolean>("_isTyping").value = true
        model.setReasoning(true)
        assertFalse("the composer is locked while a reply streams", model.reasoning.value)
        flow<Boolean>("_isTyping").value = false

        // Every other mode on, then Reasoning turned on: the reply runs as Reasoning only.
        flow<Boolean>("_imagine").value = true
        flow<Boolean>("_agent").value = true
        flow<Boolean>("_runCode").value = true
        flow<String?>("_buildRepo").value = REPO
        model.setReasoning(true)

        assertTrue(model.reasoning.value)
        assertFalse("Reasoning and Imagine are exclusive", flow<Boolean>("_imagine").value)
        assertFalse("Reasoning and Agent are exclusive", flow<Boolean>("_agent").value)
        assertNull("Reasoning leaves Build mode", model.buildRepo.value)

        // Turning it off is not gated by the exclusivity above and leaves the others alone.
        flow<Boolean>("_isTyping").value = true
        model.setReasoning(false)
        assertTrue("off is not a mode change for the reply, so it is not locked", model.reasoning.value)
        flow<Boolean>("_isTyping").value = false
        model.setReasoning(false)
        assertFalse(model.reasoning.value)
    }

    // ---- 3. the Auto chip ----

    @Test fun autoClearsEveryModeAndIsIgnoredWhileAReplyStreams() {
        applyIntegrations(IntegrationState(webSearchConfigured = true))
        flow<String?>("_webMode").value = "search"
        flow<Boolean>("_reasoning").value = true
        flow<Boolean>("_agent").value = true
        flow<Boolean>("_xSearch").value = true
        flow<Boolean>("_imagine").value = true
        flow<Boolean>("_runCode").value = true
        flow<String?>("_buildRepo").value = REPO

        model.selectAuto()

        assertNull("Auto is plain chat: no web mode", model.webMode.value)
        assertFalse(model.reasoning.value)
        assertFalse(model.agent.value)
        assertFalse(model.xSearch.value)
        assertFalse(model.imagine.value)
        assertFalse(model.runCode.value)
        assertNull("Auto leaves Build mode", model.buildRepo.value)

        // Mid-reply the chip does nothing at all — not even half of the list.
        flow<String?>("_webMode").value = "search"
        flow<Boolean>("_reasoning").value = true
        flow<Boolean>("_isTyping").value = true
        model.selectAuto()
        assertEquals("search", model.webMode.value)
        assertTrue(model.reasoning.value)
        flow<Boolean>("_isTyping").value = false
    }

    // ---- 4. Read link: the arm of the capability guard the freeze test skipped ----

    @Test fun readLinkRefusesUntilTheServerOffersItAndIgnoresAnUnknownMode() {
        model.setWebMode("page")
        assertEquals("Read link is unavailable. Refresh Feature setup, or choose Off.", model.error.value)
        assertNull("a refused mode must not be stored", model.webMode.value)

        applyIntegrations(IntegrationState(pageReadingAvailable = true))
        model.setWebMode("page")
        assertEquals("page", model.webMode.value)
        assertNull(model.error.value)

        // A mode the composer cannot send is dropped without touching the stored choice.
        model.setWebMode("bogus")
        assertEquals("page", model.webMode.value)
        assertNull(model.error.value)
    }

    private companion object {
        const val REPLY = "reply"
        const val OTHER = "other-reply"
        const val REPO = "KRFbrothers/Dua-ai"
    }
}
