package com.dua.ai.history

import android.content.SharedPreferences
import com.dua.ai.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChatHistoryWriterTest {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "history-test-worker") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val writes: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val encodeThreads: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @After fun tearDown() { scope.cancel(); executor.shutdownNow() }

    // The "encoding" of a save is its active id, so each write is easy to identify.
    private fun writer(encode: (List<SavedChat>, String) -> String = { _, id -> encodeThreads += Thread.currentThread().name; id }) =
        ChatHistoryWriter(scope, dispatcher, encode) { writes += it }
    private fun idle() { executor.submit {}.get(5, TimeUnit.SECONDS) }

    @Test fun saveEncodesAndWritesOffTheCallingThread() {
        val writer = writer()
        writer.save(emptyList(), "one")
        idle()
        assertEquals(listOf("one"), writes)
        assertTrue(encodeThreads.single().startsWith("history-test-worker")) // name may carry a coroutine suffix in debug mode
        assertFalse(writer.hasPendingWrite)
    }

    @Test fun aLateOlderEncodeNeverOverwritesANewerSave() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val writer = writer { _, id -> if (id == "old") { started.countDown(); release.await(5, TimeUnit.SECONDS) }; id }
        writer.save(emptyList(), "old")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        writer.save(emptyList(), "new") // queued while "old" is still encoding
        release.countDown()
        idle(); idle()
        assertEquals(listOf("new"), writes)
    }

    @Test fun flushWritesTheLatestSaveOnTheCallerAndIsNotRepeated() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val writer = writer { _, id -> if (Thread.currentThread().name.startsWith("history-test-worker")) { started.countDown(); release.await(5, TimeUnit.SECONDS) }; id }
        writer.save(emptyList(), "draft")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(writer.hasPendingWrite)
        writer.flush() // e.g. onStop while the worker is still busy
        assertEquals(listOf("draft"), writes)
        assertFalse(writer.hasPendingWrite)
        release.countDown(); idle()
        assertEquals("the worker must not write the same save again", listOf("draft"), writes)
        writer.flush()
        assertEquals(listOf("draft"), writes)
    }

    @Test fun flushWithNothingPendingDoesNothing() {
        writer().flush()
        assertTrue(writes.isEmpty())
    }

    @Test fun directWritesRetireEarlierQueuedSaves() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val writer = writer { _, id -> started.countDown(); release.await(5, TimeUnit.SECONDS); id }
        writer.save(emptyList(), "stale")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(writer.writeDirectly { writes += "import"; true })
        release.countDown(); idle()
        assertEquals(listOf("import"), writes)
        // Later saves still go through.
        writer.save(emptyList(), "after")
        idle()
        assertEquals(listOf("import", "after"), writes)
    }

    @Test fun manyRapidSavesEndWithTheLastOne() {
        val writer = writer()
        repeat(200) { writer.save(emptyList(), "v$it") }
        idle(); idle()
        assertEquals("v199", writes.last())
        assertTrue(writes.size <= 200)
    }

    @Test fun preloadIsUsedOnlyForTheTextStillStored() {
        val chat = SavedChat("one", "Gemini", "draft", listOf(ChatMessage(id = "m", text = "Hi", isUser = true, model = "Gemini")), 10)
        val raw = ChatHistoryCodec.encode(listOf(chat), "one")
        val prefs = preferences(mapOf("history" to raw))
        ChatHistoryPreload.start(prefs)
        assertEquals("one" to listOf(chat), ChatHistoryPreload.take(raw))
        // Consumed once; a later take decodes the given text (here: newer stored text) itself.
        ChatHistoryPreload.start(prefs)
        val newer = ChatHistoryCodec.encode(listOf(chat.copy(draft = "newer")), "one")
        assertEquals("newer", ChatHistoryPreload.take(newer).second.single().draft)
        assertEquals("draft", ChatHistoryPreload.take(raw).second.single().draft)
        // Corrupt text still fails like ChatHistoryCodec.decode, so the ViewModel keeps its backup path.
        ChatHistoryPreload.start(preferences(mapOf("history" to "broken")))
        assertTrue(runCatching { ChatHistoryPreload.take("broken") }.isFailure)
    }

    @Test fun debouncerRunsOnlyTheLastActionAndCanRunItNow() = runBlocking {
        val runs = mutableListOf<String>()
        val debouncer = Debouncer(this, 50)
        debouncer.schedule { runs += "a" }; debouncer.schedule { runs += "b" }
        assertTrue(debouncer.pending)
        delay(150)
        assertEquals(listOf("b"), runs)
        assertFalse(debouncer.pending)
        debouncer.schedule { runs += "c" }
        debouncer.runNow()
        assertEquals(listOf("b", "c"), runs)
        delay(150)
        assertEquals("runNow must not run it a second time", listOf("b", "c"), runs)
        debouncer.schedule { runs += "d" }; debouncer.cancel(); delay(150)
        assertEquals(listOf("b", "c"), runs)
        debouncer.runNow()
        assertEquals(listOf("b", "c"), runs)
    }

    @Test fun debouncerRunNowStillWorksAfterItsScopeIsCancelled() {
        val runs = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val debouncer = Debouncer(scope, 10_000)
        debouncer.schedule { runs += "draft" }
        scope.cancel() // like viewModelScope before onCleared
        debouncer.runNow()
        assertEquals(listOf("draft"), runs)
    }

    private fun preferences(values: Map<String, String>): SharedPreferences =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) { "getString" -> values[args[0] as String] ?: args[1]; else -> throw UnsupportedOperationException(method.name) }
        } as SharedPreferences
}
