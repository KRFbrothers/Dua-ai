package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.AccountState
import com.dua.ai.account.AccountUser
import com.dua.ai.account.SessionPersistence
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.BuildInfo
import com.dua.ai.model.ChatMessage
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.BuildModeConfig
import com.dua.ai.setup.CodeExecutionConfig
import com.dua.ai.setup.IntegrationState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
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
 * The per-reply mode freeze that `ChatModes.kt` has to preserve when it is extracted: a retry
 * replays the mode the ORIGINAL request was frozen with (`replyWebModes` / `replyBuild`), not the
 * composer's current chip; a NEW turn uses the composer. Plus the mode toggles themselves
 * (`setWebMode`, `setXSearch`, `setBuildRepository`, `setRunCode`), each direction of their
 * capability guards, and the fact that the modes are session-only while the chat's Build
 * repository survives the disk round trip.
 *
 * Harness: the same one as [RetryResponseTest] / [DeleteChatCleanupTest] — real ViewModel, real
 * streaming client, HTTP intercepted before the network, `@LooperMode(PAUSED)`.
 *
 * Waits: every behavioural assertion here reads state that the call under test set synchronously,
 * and the paused looper is never pumped for those. The only wait is [awaitRequest], used by the two
 * tests that must see the outgoing wire payload: that body is built on the coroutine and read on an
 * OkHttp worker thread, so the virtual clock alone cannot advance it. It is the same bounded pump
 * the merged [RetryResponseTest] uses, and it fails with a message instead of hanging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatModesModeFreezeTest {
    // Deliberately no JUnit `Timeout` rule: it moves the body to another thread while Robolectric
    // binds the main Looper to the runner thread, and `idleFor()` from a foreign thread wedges
    // instead of failing — the shape of the 25-minute job timeout this repo has already hit.
    private lateinit var application: Application
    private lateinit var model: ChatViewModel
    private lateinit var client: OkHttpClient
    private val store = ViewModelStore()
    /** Every /api/chat body, in order. The interceptor fails on any other host, so nothing escapes. */
    private val requests = CopyOnWriteArrayList<JSONObject>()
    /** The account stack from [setup]; held so [teardown] can shut down the pool it opened. */
    private var accountClient: OkHttpClient? = null

    private val prompt = ChatMessage(id = PROMPT, text = "Explain coroutines", isUser = true, model = MODEL)
    private val original = ChatMessage(id = REPLY, text = "Original answer", isUser = false, model = MODEL, state = "complete")

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
        // `appearance` starts from the store's initial value (reset above), so this is exactly
        // "loaded with defaults" — `retryResponse` needs it and nothing here waits for an emission.
        field("appearanceLoaded").set(model, true)
        intercept()
        applyIntegrations(IntegrationState())
    }

    /**
     * Stops what this test started. Deliberately does NOT call [ViewModelStore.clear]: `onCleared()`
     * touches the drive rebinder, the backup delegate, the recorder and every poll job on the paused
     * Looper, and one blocked step there hangs the Gradle worker with no test-level failure.
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

    // ---- harness ----

    /**
     * Answers a chat request with one complete SSE turn and records its body. It never blocks, so a
     * retry that somehow survived its cancellation finishes on that thread instead of parking a
     * pool thread — the failure mode that wedged the Android job before PR #316.
     */
    private fun intercept() {
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.host == "chat.test") { "Unexpected test HTTP request: ${request.url}" }
            requests += JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "text/event-stream")
                .body("data: {\"type\":\"text\",\"delta\":\"Answer\"}\n\ndata: {\"type\":\"done\",\"truncated\":false}\n\n"
                    .toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        field("api").set(model, DuaApiClient("https://chat.test", client, apiBaseUrl = "https://chat.test"))
        // Signed out and off the cloud engine: a retry then takes the plain local path.
        flow<Boolean>("_agentAvailable").value = false
        flow<Boolean>("_online").value = true
    }

    private fun applyIntegrations(state: IntegrationState) { flow<IntegrationState>("_integrations").value = state }

    /**
     * Build is gated on a server Build config AND at least one of its providers being configured;
     * the fallback list has none configured, so enable Gemini exactly like [RetryResponseTest] does.
     */
    private fun buildIntegrations(): IntegrationState {
        val base = IntegrationState(build = BuildModeConfig(enabled = true))
        return base.copy(providers = base.providers.map { if (it.name == "Gemini") it.copy(configured = true) else it })
    }

    /** The account API is never used here; refusing loudly keeps a stray call visible. */
    private fun rejectingClient() = OkHttpClient.Builder().addInterceptor { chain ->
        throw AssertionError("Unexpected account request: ${chain.request().url}")
    }.build().also { accountClient = it }

    /** One finished turn plus the per-reply mode it was frozen with — exactly what Retry accepts. */
    private fun seed(webMode: String? = null, build: String? = null) {
        flow<String>("_activeChatId").value = CHAT
        flow<List<ChatMessage>>("_messages").value = listOf(prompt, if (build == null) original else original.copy(build = BuildInfo(build)))
        flow<List<SavedChat>>("_savedChats").value = listOf(SavedChat(id = CHAT, model = MODEL, draft = "",
            messages = model.messages.value, updatedAt = 1_000L, buildRepository = build))
        flow<String>("_draft").value = ""
        flow<Boolean>("_isTyping").value = false
        map<String?>("replyWebModes")[REPLY] = webMode
        build?.let { map<String>("replyBuild")[REPLY] = it }
        saveHistory()
        model.flushHistory()
        // The seed must be on disk before the action, so a broken seed fails loudly instead of
        // looking like a pass when the assertion below compares against the same empty cache.
        val (activeId, chats) = ChatHistoryCodec.decode(rawHistory())
        assertEquals(CHAT, activeId)
        assertEquals(listOf("Explain coroutines", "Original answer"), chats.single().messages.map { it.text })
        assertEquals(build, chats.single().buildRepository)
    }

    private fun saveHistory() = ChatViewModel::class.java.getDeclaredMethod("saveHistory").apply { isAccessible = true }.invoke(model)

    private fun rawHistory(): String {
        model.flushHistory()
        return application.getSharedPreferences("dua_chat_history", 0).getString("history", null)!!
    }

    /** Signs the owner in, which Build mode requires (it reads repositories as the owner). */
    private fun signIn() {
        val account = field("account").get(model) as AccountRepository
        @Suppress("UNCHECKED_CAST")
        val state = AccountRepository::class.java.getDeclaredField("mutable").apply { isAccessible = true }
            .get(account) as MutableStateFlow<AccountState>
        state.value = state.value.copy(user = AccountUser(OWNER, "owner@example.test", "Owner"))
    }

    /**
     * The only wait in this file: records the first outgoing /api/chat body. The body is built on
     * the coroutine and the SSE response is read on an OkHttp worker thread, so the paused virtual
     * clock alone cannot advance it. Bounded and asserted, so a stuck step names itself.
     */
    private fun awaitRequest(description: String): JSONObject {
        val until = System.nanoTime() + 10_000_000_000L
        while (requests.isEmpty() && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            Thread.sleep(2)
        }
        assertTrue("Timed out waiting for $description; error=${model.error.value}, typing=${model.isTyping.value}",
            requests.isNotEmpty())
        return requests.first()
    }

    // ---- 1. a retry keeps the reply's frozen web mode ----

    @Test fun aRetrySendsTheRepliesFrozenWebModeAfterTheComposerWasTurnedOff() {
        applyIntegrations(IntegrationState(webSearchConfigured = true))
        seed(webMode = "search")
        // The user turned the chip off after the original answer arrived.
        model.setWebMode(null)
        assertNull("the seed must start with the composer's mode off", model.webMode.value)

        model.retryResponse(REPLY)

        // The retry freezes the ORIGINAL request's choice, and it does so synchronously — before
        // its coroutine is dispatched — so this needs no wait and cannot see a half-applied frame.
        assertEquals("search", map<String?>("replyWebModes")[REPLY])
        assertEquals(null, model.webMode.value)
        assertEquals(ChatMessage.STREAMING, model.messages.value.last().state)
        assertEquals(2, model.messages.value.size)

        val body = awaitRequest("the retry request")
        assertEquals("search", body.getString("web"))
        assertEquals("auto", body.getString("mode"))
        assertEquals(1, body.getJSONArray("messages").length())
        assertEquals("Explain coroutines", body.getJSONArray("messages").getJSONObject(0).getString("text"))
    }

    // ---- 2. the freeze is scoped to the old reply; a new turn uses the composer ----

    @Test fun aNewTurnUsesTheComposersCurrentModeWhileTheOldReplyKeepsItsFrozenOne() {
        applyIntegrations(IntegrationState(webSearchConfigured = true))
        seed(webMode = "search")
        model.setWebMode(null)

        model.retryResponse(REPLY)
        model.stopReply()
        assertFalse("the stopped retry must hand the composer back", model.isTyping.value)
        assertEquals("Original answer", model.messages.value.last().text)

        model.sendMessage("Next question")

        val newReply = model.messages.value.last()
        assertFalse("a new turn must create a new reply", newReply.id == REPLY)
        assertTrue("the new turn must be recorded against its own reply",
            map<String?>("replyWebModes").containsKey(newReply.id))
        assertNull("the new turn starts from the composer, not the frozen mode", map<String?>("replyWebModes")[newReply.id])
        assertEquals("search", map<String?>("replyWebModes")[REPLY])
        assertNull("a new turn must not inherit the old freeze", model.webMode.value)
    }

    // ---- 3. a Build reply keeps its repository even after Build mode is left ----

    @Test fun aBuildReplyKeepsItsRepositoryAfterTheComposerLeftBuildMode() {
        signIn()
        applyIntegrations(buildIntegrations())
        seed(build = REPO)
        assertNull("Build must be usable in this test", model.buildUnavailableReason())
        // The user left Build mode after the answer arrived.
        flow<String?>("_buildRepo").value = null

        model.retryResponse(REPLY)

        assertEquals(REPO, map<String>("replyBuild")[REPLY])
        assertEquals(REPO, model.messages.value.last().build?.repository)
        assertEquals(null, model.buildRepo.value)

        val body = awaitRequest("the Build retry request")
        assertEquals("build", body.getString("mode"))
        assertEquals(REPO, body.getString("repository"))
        assertFalse("Build replaces the web mode entirely", body.has("web"))
    }

    // ---- 4. a saved chat restores its repository; the session modes reset ----

    @Test fun openingASavedChatAppliesItsStoredRepositoryAndClearsTheSessionModes() {
        val plain = chat(id = PLAIN)
        val built = chat(id = BUILT, buildRepository = REPO)
        flow<String>("_activeChatId").value = PLAIN
        flow<List<SavedChat>>("_savedChats").value = listOf(plain, built)
        flow<List<ChatMessage>>("_messages").value = plain.messages
        flow<String>("_draft").value = ""
        flow<Boolean>("_isTyping").value = false
        saveHistory()
        model.flushHistory()

        flow<String?>("_webMode").value = "search"
        flow<Boolean>("_xSearch").value = true
        flow<String?>("_buildRepo").value = REPO
        model.openChat(BUILT)

        assertEquals(REPO, model.buildRepo.value)
        assertEquals(null, model.webMode.value)
        assertFalse("X search is a session choice, never carried across chats", model.xSearch.value)

        model.openChat(PLAIN)
        assertEquals(null, model.buildRepo.value)

        flow<String?>("_webMode").value = "search"
        flow<Boolean>("_xSearch").value = true
        flow<String?>("_buildRepo").value = REPO
        val before = model.activeChatId.value
        model.startNewChat()

        assertFalse("a new chat must be a new chat", model.activeChatId.value == before)
        assertNull("a new chat starts with no web mode", model.webMode.value)
        assertFalse("a new chat starts with X search off", model.xSearch.value)
        assertNull("a new chat is not tied to a repository", model.buildRepo.value)
    }

    // ---- 5. web search and X search: both directions of the capability guard ----

    @Test fun webSearchAndXSearchRefuseWithoutCapabilityAndApplyOnceTheServerOffersThem() {
        // Direction 1: the server does not offer search, so the chip refuses and stores nothing.
        // The engine's default model is "Dua", which is why the message names it.
        model.setWebMode("search")
        assertTrue("a refused mode must say why: ${model.error.value}",
            model.error.value.orEmpty().startsWith("Web search needs setup for $MODEL"))
        assertNull("a refused mode must not be stored", model.webMode.value)

        // Direction 2: the same call once the server offers it.
        applyIntegrations(IntegrationState(webSearchConfigured = true))
        model.setWebMode("search")
        assertEquals("search", model.webMode.value)
        assertNull(model.error.value)

        // The composer is locked while a reply streams.
        flow<Boolean>("_isTyping").value = true
        model.setWebMode(null)
        assertEquals("search", model.webMode.value)
        flow<Boolean>("_isTyping").value = false

        // X search is Grok-only and server-gated; both directions. A non-Grok model refuses first.
        model.setXSearch(true)
        assertEquals("X search works with Grok when the server offers it.", model.error.value)
        assertFalse("X search must stay off for a non-Grok model", model.xSearch.value)

        flow<String>("_selectedModel").value = "Grok"
        applyIntegrations(IntegrationState(xSearchAvailable = true))
        model.setXSearch(true)
        assertTrue("X search must apply for Grok once the server offers it", model.xSearch.value)
        assertNull(model.error.value)
    }

    // ---- 6. Build and Run code: both directions, and Build's exclusivity ----

    @Test fun buildAndRunCodeRefuseWithoutCapabilityAndApplyOnceItIsAvailable() {
        // Run code: no server config -> refused with the setup message.
        model.setRunCode(true)
        assertEquals("Run code needs the latest Dua backend. Deploy it, then refresh Feature setup.", model.error.value)
        assertFalse("a refused Run code must stay off", model.runCode.value)

        // Run code: offered for this model -> applies.
        applyIntegrations(IntegrationState(codeExecution = CodeExecutionConfig(enabled = true, providers = setOf(MODEL))))
        model.setRunCode(true)
        assertTrue("Run code must apply once the server offers it", model.runCode.value)
        assertNull(model.error.value)

        // Build: no server config -> refused BEFORE anything is cleared (proves the early return).
        model.setBuildRepository(REPO)
        assertEquals("Build mode needs the latest Dua backend. Deploy it, then refresh Feature setup.", model.error.value)
        assertNull("a refused Build must not be stored", model.buildRepo.value)
        assertTrue("a refused Build must not disturb the other modes", model.runCode.value)

        // Build: offered, with an owner signed in -> applies and takes over every other mode.
        signIn()
        applyIntegrations(buildIntegrations())
        assertNull("Build must be usable in this test", model.buildUnavailableReason())
        flow<String?>("_webMode").value = "search"
        flow<Boolean>("_xSearch").value = true
        flow<Boolean>("_reasoning").value = true
        model.setBuildRepository(REPO)

        assertEquals(REPO, model.buildRepo.value)
        assertNull(model.error.value)
        assertEquals(null, model.webMode.value)
        assertFalse("Build is exclusive: X search must drop", model.xSearch.value)
        assertFalse("Build is exclusive: reasoning must drop", model.reasoning.value)
        assertFalse("Build is exclusive: Run code must drop", model.runCode.value)
    }

    // ---- 7. the modes are session-only; the repository is the chat's ----

    @Test fun theModesAreSessionOnlyWhileTheBuildRepositorySurvivesTheDiskRoundTrip() {
        flow<String>("_activeChatId").value = CHAT
        flow<List<ChatMessage>>("_messages").value = listOf(prompt, original)
        flow<List<SavedChat>>("_savedChats").value = listOf(chat(id = CHAT, buildRepository = REPO))
        flow<String>("_draft").value = ""
        flow<Boolean>("_isTyping").value = false
        flow<String?>("_webMode").value = "search"
        flow<Boolean>("_xSearch").value = true
        flow<String?>("_buildRepo").value = REPO
        // Save AFTER the chips are set, so the assertion is about what encode() drops, not about
        // a snapshot taken before the modes changed.
        saveHistory()

        val raw = rawHistory()
        val (activeId, chats) = ChatHistoryCodec.decode(raw)
        val stored = chats.single()

        assertEquals(CHAT, activeId)
        assertEquals("the repository tied to the chat is saved with it", REPO, stored.buildRepository)
        assertEquals(listOf("Explain coroutines", "Original answer"), stored.messages.map { it.text })

        val json = JSONObject(raw).getJSONArray("chats").getJSONObject(0)
        assertFalse("the web mode is a session choice and must never reach disk", json.has("webMode"))
        assertFalse("X search is a session choice and must never reach disk", json.has("xSearch"))
        assertFalse("the composer's repository is not a per-chat key of its own", json.has("buildRepo"))
    }

    private fun chat(id: String, buildRepository: String? = null) =
        SavedChat(id = id, model = MODEL, draft = "", updatedAt = 1_000L, buildRepository = buildRepository,
            messages = listOf(prompt.copy(id = "$id-prompt"), original.copy(id = "$id-reply")))

    private companion object {
        const val CHAT = "chat-current"
        const val PLAIN = "chat-plain"
        const val BUILT = "chat-built"
        const val PROMPT = "prompt"
        const val REPLY = "reply"
        const val REPO = "KRFbrothers/Dua-ai"
        const val MODEL = "Dua"
        const val OWNER = "00000000-0000-0000-0000-000000000001"
    }
}
