package com.dua.ai.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.AccountApi
import com.dua.ai.account.AccountRepository
import com.dua.ai.account.AccountSession
import com.dua.ai.account.SessionPersistence
import com.dua.ai.network.DuaApiClient
import com.dua.ai.settings.AppearanceStore
import com.dua.ai.settings.DevicePreferences
import com.dua.ai.settings.DuaPersonality
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.IntegrationState
import kotlinx.coroutines.Job
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CopyOnWriteArrayList

/** Real DataStore -> ViewModel send/retry -> HTTP payload, intercepted before any network. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PersonalityPromptingTest(private val separateEngine: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "separateEngine={0}")
        fun engines() = listOf(arrayOf(false), arrayOf(true))
    }
    private lateinit var model: ChatViewModel
    private lateinit var client: OkHttpClient
    private val store = ViewModelStore()
    private val requests = CopyOnWriteArrayList<JSONObject>()
    private fun field(name: String) = ChatViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private fun <T> flow(name: String) = field(name).get(model) as MutableStateFlow<T>

    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_preferences", "dua_auto_sync", "dua_files", "dua_account_session"))
            app.getSharedPreferences(name, 0).edit().clear().commit()
        runBlocking { AppearanceStore(app).reset() }
        DevicePreferences(app).update(LocalPreferences(memoryEnabled = false, memory = listOf("Private note stays disabled")))
        model = ChatViewModel(app).also { store.put("chat", it) }
        for (name in listOf("engineCheck", "cloudImagineCheck")) (field(name).get(model) as? Job)?.cancel()
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.host == "chat.test" && request.url.encodedPath == "/api/chat") { "Unexpected test HTTP request" }
            requests += JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "text/event-stream")
                .body("data: {\"type\":\"text\",\"delta\":\"Test answer\"}\n\ndata: {\"type\":\"done\",\"truncated\":false}\n\n"
                    .toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        field("api").set(model, DuaApiClient("https://chat.test", client, apiBaseUrl = if (separateEngine) "https://base.test" else "https://chat.test"))
        field("separateChatEngine").set(model, separateEngine)
        field("account").set(model, AccountRepository(AccountApi("https://account.test", client), object : SessionPersistence {
            override fun read(): AccountSession? = null
            override fun write(value: AccountSession?) = Unit
        }))
        flow<IntegrationState>("_integrations").value = IntegrationState(personalityEnabled = true)
        flow<Boolean>("_enginePersonalityAvailable").value = true
        await("initial preferences") { field("appearanceLoaded").getBoolean(model) }
    }
    @After fun close() {
        store.clear()
        client.dispatcher.cancelAll(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
    }
    private fun await(description: String, condition: () -> Boolean) {
        val until = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < until) {
            // Streaming intentionally paces updates on Main. Advance Robolectric's paused clock,
            // rather than waiting for a virtual delay with wall-clock sleep alone.
            shadowOf(Looper.getMainLooper()).idleFor(25, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
        assertTrue("Timed out: $description; requests=${requests.size}, typing=${model.isTyping.value}, error=${model.error.value}", condition())
    }
    private fun choose(preset: DuaPersonality) {
        model.setPersonality(preset)
        await("persisted ${preset.id}") { model.appearance.value.personality == preset }
    }
    private fun completed(count: Int) = await("completed request $count") {
        requests.size == count && !model.isTyping.value && model.messages.value.lastOrNull()?.state == "complete"
    }

    @Test fun sendAndRetryUseThePersistedCurrentPresetWithoutRewritingChatOrEnablingMemory() {
        choose(DuaPersonality.SeniorArchitect)
        model.sendMessage("Review my Kotlin code")
        completed(1)
        assertEquals(DuaPersonality.SeniorArchitect.instruction, requests[0].getString("personality"))
        assertEquals("", requests[0].getString("memory"))
        assertEquals("Review my Kotlin code", model.messages.value.first().text)
        assertEquals("Review my Kotlin code", requests[0].getJSONArray("messages").getJSONObject(0).getString("text"))
        choose(DuaPersonality.CreativeBrainstormer)
        model.retryResponse(model.messages.value.last().id)
        completed(2)
        assertEquals(DuaPersonality.CreativeBrainstormer.instruction, requests[1].getString("personality"))
        assertEquals("", requests[1].getString("memory"))
        assertEquals(1, requests[1].getJSONArray("messages").length())
        assertEquals(2, model.messages.value.size)
        assertFalse(model.localSettings.value.memoryEnabled)
        assertEquals(listOf("Private note stays disabled"), model.localSettings.value.memory)
    }

    @Test fun theOtherBackendsCapabilityCannotEnablePersonalityOrConsumeTheDraft() {
        choose(DuaPersonality.DeepResearcher)
        flow<IntegrationState>("_integrations").value = IntegrationState(personalityEnabled = separateEngine)
        flow<Boolean>("_enginePersonalityAvailable").value = !separateEngine
        model.updateDraft("Keep this draft")
        model.sendMessage("Keep this draft")
        assertTrue(model.error.value.orEmpty().contains("Update the chat backend"))
        assertEquals("Keep this draft", model.draft.value)
        assertTrue(model.messages.value.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test fun noOverrideKeepsTheLegacyRequestEvenWithoutBackendPersonalitySupport() {
        assertNull(model.appearance.value.personality)
        flow<IntegrationState>("_integrations").value = IntegrationState()
        flow<Boolean>("_enginePersonalityAvailable").value = false
        model.sendMessage("Hello")
        completed(1)
        assertFalse(requests.single().has("personality"))
        assertEquals("", requests.single().getString("memory"))
    }
}
