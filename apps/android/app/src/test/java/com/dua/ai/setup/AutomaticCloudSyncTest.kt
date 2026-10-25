package com.dua.ai.setup

import com.dua.ai.account.AccountException
import com.dua.ai.account.CloudReview
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AutomaticCloudSyncTest {
    private val ownerA = "11111111-1111-4111-8111-111111111111"
    private val ownerB = "22222222-2222-4222-8222-222222222222"
    private fun chat(id: String = "one", text: String = "Hi", draft: String = "", updated: Long = 1) = SavedChat(id, "Dua", draft, listOf(ChatMessage(text = text, isUser = true)), updated)
    private fun snapshot(chats: List<SavedChat> = emptyList(), memory: List<String> = emptyList()) = CloudSnapshot(chats, memory)

    private inner class Fixture(initialLocal: CloudSnapshot = snapshot(), initialCloud: CloudSnapshot = snapshot(), initialRecord: AutomationSyncRecord? = null) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var here = initialLocal
        var remote = initialCloud
        var revision = 0L
        var owner: String? = ownerA
        var configured = true
        var stored = initialRecord
        var loadCalls = 0; var cloudCalls = 0; var uploadCalls = 0; var commits = 0; var writes = 0; var clears = 0
        var loadHook: suspend () -> Unit = {}
        var cloudHook: suspend () -> Unit = {}
        var uploadHook: (suspend (CloudSnapshot) -> Long)? = null
        var commitFails = false; var readFails = false; var writeFails = false; var storeReadFails = false
        val waits = mutableListOf<Long>()
        val timerGates = mutableListOf<Pair<CompletableDeferred<Unit>, Job>>()
        val account = object : AutomaticCloudSync.AccountPort {
            override fun currentAccountId() = owner
            override suspend fun load(): AutomaticSyncAccount { loadCalls++; loadHook(); return AutomaticSyncAccount(configured, owner) }
            override suspend fun cloud(expectedAccountId: String): CloudReview {
                cloudCalls++; val reply = CloudReview(revision, remote, owner); cloudHook(); return reply
            }
            override suspend fun upload(expectedAccountId: String, revision: Long, snapshot: CloudSnapshot): Long {
                uploadCalls++; assertEquals(owner, expectedAccountId)
                uploadHook?.let { return it(snapshot) }
                assertEquals(this@Fixture.revision, revision); remote = snapshot; return ++this@Fixture.revision
            }
        }
        val local = object : AutomaticCloudSync.LocalPort {
            override fun read(): CloudSnapshot { if (readFails) error("Damaged local data"); return here }
            override fun commit(snapshot: CloudSnapshot) { commits++; if (commitFails) error("Local storage full"); here = snapshot }
        }
        val store = object : AutomaticCloudSync.StorePort {
            override fun read(): AutomationSyncRecord? { if (storeReadFails) error("Corrupt consent"); return stored }
            override fun write(value: AutomationSyncRecord) { writes++; if (writeFails) error("Full"); stored = value }
            override fun clear() { clears++; stored = null; storeReadFails = false; writeFails = false }
        }
        fun controller(foreground: Boolean = true) = AutomaticCloudSync(scope, account, local, store, sleep = { waits += it; val gate = CompletableDeferred<Unit>(); timerGates += gate to currentCoroutineContext().job; gate.await() }).also { it.setForeground(foreground) }
        fun fireTimer() { val timer = timerGates.first { it.second.isActive }; timer.first.complete(Unit) }
        fun close(sync: AutomaticCloudSync) { sync.close(); scope.cancel() }
    }

    @Test fun `sync is off by default and sign-in alone never uploads`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); val sync = p.controller()
        sync.restore()?.join(); sync.localChanged(); sync.syncNow()?.join()
        assertFalse(sync.state.value.enabled); assertEquals(0, p.loadCalls); assertEquals(0, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `explicit enable binds owner and unchanged snapshots never upload twice`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); val sync = p.controller(); sync.enable()?.join()
        assertEquals(1, p.uploadCalls); assertEquals(ownerA, p.stored?.accountId); assertEquals("synced", sync.state.value.phase)
        sync.syncNow()?.join(); assertEquals(1, p.uploadCalls); assertEquals(30_000L, p.waits.last())
        p.close(sync)
    }

    @Test fun `disable during first authentication prevents late consent and upload`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); val gate = CompletableDeferred<Unit>()
        p.loadHook = { withContext(NonCancellable) { gate.await() } }
        val sync = p.controller(); val job = sync.enable(); assertEquals("checking", sync.state.value.phase)
        sync.disable(); assertEquals("disabled", sync.state.value.phase); gate.complete(Unit); job?.join()
        assertFalse(sync.state.value.enabled); assertEquals(0, p.cloudCalls); assertEquals(0, p.uploadCalls); assertEquals(false, p.stored?.enabled)
        p.close(sync)
    }

    @Test fun `first enable cannot transfer its consent to an account changed during authentication`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); val gate = CompletableDeferred<Unit>(); p.loadHook = { withContext(NonCancellable) { gate.await() } }
        val sync = p.controller(); val job = sync.enable(); p.owner = ownerB; sync.accountChanged(); gate.complete(Unit); job?.join()
        assertFalse(sync.state.value.enabled); assertTrue(sync.state.value.reviewRequired); assertNotEquals(ownerB, p.stored?.accountId); assertEquals(0, p.cloudCalls); assertEquals(0, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `foreground busy and online transitions ignore noncancellable late cloud reads`() = runBlocking {
        for (transition in listOf<(AutomaticCloudSync) -> Unit>({ it.setForeground(false) }, { it.setBusy(true) }, { it.setOnline(false) })) {
            val p = Fixture(snapshot(listOf(chat())), snapshot(listOf(chat("remote", "Other prompt")))); val gate = CompletableDeferred<Unit>()
            p.cloudHook = { withContext(NonCancellable) { gate.await() } }
            val sync = p.controller(); val job = sync.enable(); transition(sync); gate.complete(Unit); job?.join()
            assertEquals(0, p.commits); assertEquals(0, p.uploadCalls)
            p.cloudHook = {}; sync.setForeground(true); sync.setBusy(false); sync.setOnline(true); sync.retry()?.join()
            assertEquals(1, p.uploadCalls)
            p.close(sync)
        }
    }

    @Test fun `draft model and new messages edited during cloud read survive upload`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); val gate = CompletableDeferred<Unit>(); p.cloudHook = { gate.await() }
        val sync = p.controller(); val job = sync.enable()
        p.here = snapshot(listOf(chat(draft = "Unsent edit", updated = 2).copy(model = "Gemini", messages = listOf(ChatMessage(text = "New prompt", isUser = true)))))
        gate.complete(Unit); job?.join()
        assertEquals("Unsent edit", p.remote.chats.single().draft); assertEquals("Gemini", p.remote.chats.single().model); assertEquals("New prompt", p.remote.chats.single().messages.single().text)
        p.close(sync)
    }

    @Test fun `local deletion while cloud read waits pauses before merge and upload`() = runBlocking {
        val original = snapshot(listOf(chat()), listOf("Prefer Hindi")); val p = Fixture(initialLocal = original); val sync = p.controller(); sync.enable()?.join()
        val gate = CompletableDeferred<Unit>(); p.cloudHook = { gate.await() }; val job = sync.syncNow(); p.here = snapshot(); gate.complete(Unit); job?.join()
        assertTrue(sync.state.value.reviewRequired); assertEquals(1, p.uploadCalls); assertEquals(0, p.commits); assertTrue(p.here.chats.isEmpty())
        p.close(sync)
    }

    @Test fun `first sync keeps edits during account and cloud checks under separate ids`() = runBlocking {
        for (duringAccountCheck in listOf(true, false)) {
            val original = chat(draft = "Cached draft")
            val server = chat(text = "Server question", draft = "Server draft", updated = 3)
            val p = Fixture(snapshot(listOf(original), listOf("Cached note")), snapshot(listOf(server), listOf("Server note")))
            val gate = CompletableDeferred<Unit>()
            if (duringAccountCheck) p.loadHook = { gate.await() } else p.cloudHook = { gate.await() }
            val sync = p.controller(); val job = sync.enable()
            val edited = original.copy(draft = "Typed while checking", model = "Gemini", updatedAt = 2)
            p.here = snapshot(listOf(edited), listOf("Changed cached note"))
            gate.complete(Unit); job?.join()
            assertEquals("synced", sync.state.value.phase)
            assertEquals(server, p.here.chats.single { it.id == original.id })
            val copy = p.here.chats.single { it.id.startsWith("unsynced-") }
            assertEquals(edited.copy(id = copy.id), copy)
            assertEquals(listOf("Server note"), p.here.memory)
            assertEquals(1, p.uploadCalls)
            assertEquals(p.here, p.remote)
            sync.syncNow()?.join()
            assertEquals(1, p.uploadCalls); assertEquals(2, p.here.chats.size)
            p.close(sync)
        }
    }

    @Test fun `first sync edit matching the server creates no unnecessary copy`() = runBlocking {
        val original = chat(draft = "Old draft")
        val server = original.copy(draft = "Same draft", updatedAt = 2)
        val p = Fixture(snapshot(listOf(original)), snapshot(listOf(server)))
        val gate = CompletableDeferred<Unit>(); p.cloudHook = { gate.await() }
        val sync = p.controller(); val job = sync.enable()
        p.here = snapshot(listOf(server)); gate.complete(Unit); job?.join()
        assertEquals(listOf(server), p.here.chats); assertEquals(0, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `first sync retains live edit protection when the initial cloud request fails`() = runBlocking {
        val original = chat(draft = "Cached draft")
        val server = original.copy(draft = "Server draft", updatedAt = 3)
        val p = Fixture(snapshot(listOf(original)), snapshot(listOf(server)))
        val gate = CompletableDeferred<Unit>()
        p.cloudHook = { gate.await(); error("Connection lost") }
        val sync = p.controller(); val job = sync.enable()
        p.here = snapshot(listOf(original.copy(draft = "Typed before failure", updatedAt = 2)))
        gate.complete(Unit); job?.join()
        assertEquals("retrying", sync.state.value.phase); assertEquals(0, p.commits)
        p.cloudHook = {}; sync.retry()?.join()
        assertEquals("synced", sync.state.value.phase)
        assertEquals(server, p.here.chats.single { it.id == original.id })
        assertEquals("Typed before failure", p.here.chats.single { it.id.startsWith("unsynced-") }.draft)
        assertEquals(1, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `first sync recovery exceeding snapshot limits keeps the typed cache for review`() = runBlocking {
        val original = chat()
        val remote = snapshot(listOf(original.copy(draft = "Server draft")) + (1..99).map { chat("server-$it") })
        val p = Fixture(snapshot(listOf(original)), remote)
        val gate = CompletableDeferred<Unit>(); p.cloudHook = { gate.await() }
        val sync = p.controller(); val job = sync.enable()
        val edited = snapshot(listOf(original.copy(draft = "Keep this typed draft")))
        p.here = edited; gate.complete(Unit); job?.join()
        assertTrue(sync.state.value.reviewRequired); assertEquals(edited, p.here)
        assertEquals(0, p.commits); assertEquals(0, p.uploadCalls); assertEquals(remote, p.remote)
        p.close(sync)
    }

    @Test fun `unreadable first cache requires review before account or cloud checks`() = runBlocking {
        val p = Fixture(); p.readFails = true; val sync = p.controller(); sync.enable()?.join()
        assertTrue(sync.state.value.reviewRequired); assertEquals(0, p.loadCalls)
        assertEquals(0, p.cloudCalls); assertEquals(0, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `server memory deletion is applied to the phone and never uploaded back`() = runBlocking {
        val original = snapshot(listOf(chat()), listOf("Prefer Hindi")); val record = AutomationSyncRecord(true, ownerA, 2, AutomationSyncPolicy.baseline(original))
        val p = Fixture(original, snapshot(listOf(chat())), record); p.revision = 3; val sync = p.controller(); sync.restore()?.join()
        assertFalse(sync.state.value.reviewRequired); assertEquals(0, p.uploadCalls); assertEquals(1, p.commits); assertTrue(p.here.memory.isEmpty())
        p.close(sync)
    }

    @Test fun `same owner startup emission does not invalidate restored consent`() = runBlocking {
        val original = snapshot(listOf(chat())); val record = AutomationSyncRecord(true, ownerA, 2, AutomationSyncPolicy.baseline(original))
        val p = Fixture(original, original, record); p.revision = 2; val sync = p.controller(); sync.accountChanged(); sync.restore()?.join()
        assertFalse(sync.state.value.reviewRequired); assertEquals("synced", sync.state.value.phase); assertEquals(0, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `restored consent pauses for unavailable account setup without repeated background checks`() = runBlocking {
        val p = Fixture(initialRecord = AutomationSyncRecord(true, ownerA)); p.configured = false; val sync = p.controller(); sync.restore()?.join()
        assertTrue(sync.state.value.reviewRequired); assertEquals(1, p.loadCalls); assertEquals(0, p.cloudCalls); assertTrue(p.waits.isEmpty())
        sync.setForeground(false); sync.setForeground(true); assertEquals(1, p.loadCalls)
        p.close(sync)
    }

    @Test fun `disabled old account baseline cannot be transferred to another account without review`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); val sync = p.controller(); sync.enable()?.join(); sync.disable(); p.owner = ownerB
        sync.enable()?.join(); assertTrue(sync.state.value.reviewRequired); assertEquals(ownerA, p.stored?.accountId); assertEquals(1, p.uploadCalls)
        sync.resumeAfterReview()?.join(); assertEquals(ownerB, p.stored?.accountId); assertFalse(sync.state.value.reviewRequired)
        p.close(sync)
    }

    @Test fun `stored review stays paused in background and account switches invalidate active work`() = runBlocking {
        val record = AutomationSyncRecord(true, ownerA, reviewRequired = true); val p = Fixture(initialRecord = record); val sync = p.controller(false)
        sync.restore()?.join(); sync.resumeAfterReview()?.join(); assertEquals(0, p.loadCalls); assertTrue(sync.state.value.reviewRequired)
        sync.setForeground(true); val gate = CompletableDeferred<Unit>(); p.cloudHook = { withContext(NonCancellable) { gate.await() } }; val job = sync.resumeAfterReview()
        p.owner = ownerB; sync.accountChanged(); gate.complete(Unit); job?.join()
        assertTrue(sync.state.value.reviewRequired); assertEquals(0, p.commits); assertEquals(0, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `server copy replaces a differing phone cache without review`() = runBlocking {
        val p = Fixture(snapshot(listOf(chat(text = "Local"))), snapshot(listOf(chat(text = "Remote")))); val sync = p.controller(); sync.enable()?.join()
        assertFalse(sync.state.value.reviewRequired); assertEquals(1, p.here.chats.size); assertEquals(0, p.uploadCalls)
        assertEquals(AutomationSyncPolicy.signature(p.remote.chats.single()), AutomationSyncPolicy.signature(p.here.chats.single()))
        p.close(sync)
    }

    @Test fun `CAS 409 merges safe additions then requires review without automatic retries`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); val sync = p.controller(); sync.enable()?.join(); p.here = snapshot(listOf(chat(), chat("local", "New local")))
        p.uploadHook = { p.remote = snapshot(listOf(chat(), chat("other", "Other device"))); p.revision++; throw AccountException(409, "Conflict") }
        sync.syncNow()?.join(); assertTrue(sync.state.value.reviewRequired); assertEquals(3, p.here.chats.size); val count = p.uploadCalls
        sync.localChanged(); sync.retry()?.join(); assertEquals(count, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `confirmed conflict with merge failure pauses even when original upload timed out`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); val sync = p.controller(); sync.enable()?.join(); p.here = snapshot(listOf(chat(), chat("local", "New local")))
        p.uploadHook = { p.remote = snapshot(listOf(chat(), chat("other", "Remote edit"))); p.revision++; p.commitFails = true; error("Connection lost") }
        sync.syncNow()?.join(); assertTrue(sync.state.value.reviewRequired); assertEquals(2, p.here.chats.size); val count = p.uploadCalls
        sync.syncNow()?.join(); assertEquals(count, p.uploadCalls)
        p.close(sync)
    }

    @Test fun `local commit failures require review and never upload`() = runBlocking {
        val p = Fixture(snapshot(listOf(chat())), snapshot(listOf(chat("remote", "Remote addition")))); p.commitFails = true
        val sync = p.controller(); sync.enable()?.join(); assertTrue(sync.state.value.reviewRequired); assertEquals(0, p.uploadCalls); assertEquals(1, p.here.chats.size)
        p.close(sync)
    }

    @Test fun `five network failures persist finite budget until explicit retry`() = runBlocking {
        val p = Fixture(); val sync = p.controller(); sync.enable()?.join(); p.cloudHook = { error("Offline") }
        repeat(5) { sync.syncNow()?.join() }; assertEquals("error", sync.state.value.phase); assertEquals(5, p.stored?.failureCount); val count = p.cloudCalls
        sync.localChanged(); sync.syncNow()?.join(); assertEquals(count, p.cloudCalls)
        p.cloudHook = {}; sync.retry()?.join(); assertEquals("synced", sync.state.value.phase); assertEquals(0, p.stored?.failureCount)
        p.close(sync)
    }

    @Test fun `restored consent retries initial authentication and respects the persisted failure budget`() = runBlocking {
        val initial = AutomationSyncRecord(true, ownerA, 0, AutomationSyncPolicy.baseline(snapshot()))
        val p = Fixture(initialRecord = initial); p.loadHook = { error("Account offline") }; val sync = p.controller(); sync.restore()?.join()
        assertEquals(1, p.loadCalls); assertEquals("retrying", sync.state.value.phase)
        repeat(4) { p.fireTimer() }
        assertEquals(5, p.loadCalls); assertEquals(5, p.stored?.failureCount); assertEquals("error", sync.state.value.phase)
        sync.setForeground(false); sync.setForeground(true); sync.setOnline(false); sync.setOnline(true)
        assertEquals(5, p.loadCalls)
        p.loadHook = {}; sync.retry()?.join(); assertEquals("synced", sync.state.value.phase); assertEquals(0, p.stored?.failureCount)
        p.close(sync)
    }

    @Test fun `corrupt and unwritable consent fail closed until explicit reset`() = runBlocking {
        val p = Fixture(initialLocal = snapshot(listOf(chat()))); p.storeReadFails = true; val sync = p.controller()
        sync.enable()?.join(); assertEquals("storage-error", sync.state.value.phase); assertEquals(0, p.uploadCalls)
        sync.reset(); assertEquals(1, p.clears); p.writeFails = true; sync.enable()?.join(); assertFalse(sync.state.value.enabled); assertEquals(0, p.uploadCalls)
        p.close(sync)
    }
}
