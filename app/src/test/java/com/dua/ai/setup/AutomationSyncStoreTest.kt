package com.dua.ai.setup

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AutomationSyncStoreTest {
    private val backend = "https://dua.example.org"
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val baseline = AutomationBaseline(mapOf("chat-id" to 2), mapOf("chat-id" to "a".repeat(64)), setOf("b".repeat(64)))
    private val record = AutomationSyncRecord(true, owner, 4, baseline, true, 3)
    private class MemoryStorage(var value: String? = null) : AutomationSyncStorage {
        var failNext = false
        var writes = 0
        override fun read() = value
        override fun commit(value: String?): Boolean {
            writes++
            this.value = value // SharedPreferences can update its cache even when disk commit fails.
            if (failNext) { failNext = false; return false }
            return true
        }
    }
    private fun decode(raw: String) = AutomationSyncRecordCodec.decode(raw, backend)
    private fun encoded() = AutomationSyncRecordCodec.encode(record, backend)
    private fun invalid(raw: String) { assertNotNull(runCatching { decode(raw) }.exceptionOrNull()) }

    @Test fun recordRoundTripPersistsOwnerConsentReviewRevisionAndOnlyDigests() {
        val storage = MemoryStorage()
        AutomationSyncStore(storage, backend).write(record)
        assertEquals(record, AutomationSyncStore(storage, backend).read())
        val raw = storage.value!!
        assertTrue(raw.contains(owner)); assertTrue(raw.contains("a".repeat(64)))
        assertFalse(raw.contains("messages")); assertFalse(raw.contains("draft")); assertFalse(raw.contains("password"))
        assertFalse(raw.contains("memoryNotes"))
        val baselineJson = JSONObject(raw).getJSONObject("baseline")
        assertEquals(setOf("chats", "memoryDigests"), baselineJson.keys().asSequence().toSet())
        assertEquals(setOf("id", "messageCount", "signature"), baselineJson.getJSONArray("chats").getJSONObject(0).keys().asSequence().toSet())
    }
    @Test fun newStoreHasNoConsentAndInitialAccountBoundConsentCanBeSaved() {
        val storage = MemoryStorage(); val store = AutomationSyncStore(storage, backend)
        assertNull(store.read())
        val initial = AutomationSyncRecord(enabled = true, accountId = owner)
        store.write(initial)
        assertEquals(initial, AutomationSyncStore(storage, backend).read())
        assertNull(AutomationSyncStore(storage, backend).read()!!.baseline)
    }
    @Test fun corruptionIsNotOverwrittenByEnablingAndRequiresExplicitClear() {
        val storage = MemoryStorage("{broken"); val store = AutomationSyncStore(storage, backend)
        assertNotNull(runCatching { store.read() }.exceptionOrNull())
        assertNotNull(runCatching { store.write(record) }.exceptionOrNull())
        assertEquals(0, storage.writes)
        assertEquals("{broken", storage.value)
        store.clear(); assertNull(store.read()); assertNull(storage.value)
        store.write(AutomationSyncRecord(enabled = true, accountId = owner))
        assertTrue(store.read()!!.enabled)
        val unread = AutomationSyncStore(MemoryStorage("{broken"), backend)
        assertNotNull(runCatching { unread.write(record) }.exceptionOrNull())
    }
    @Test fun failedCommitPausesThisInstanceAndPersistsReviewWithPreviousBaseline() {
        val storage = MemoryStorage(encoded()); val store = AutomationSyncStore(storage, backend)
        storage.failNext = true
        assertNotNull(runCatching { store.write(record.copy(revision = 5)) }.exceptionOrNull())
        assertNotNull(runCatching { store.read() }.exceptionOrNull())
        assertNotNull(runCatching { store.write(record) }.exceptionOrNull())
        val afterRestart = AutomationSyncStore(storage, backend).read()!!
        assertFalse(afterRestart.enabled); assertTrue(afterRestart.reviewRequired)
        assertEquals(5, afterRestart.failureCount)
        assertEquals(4L, afterRestart.revision); assertEquals(baseline, afterRestart.baseline)
        store.clear(); assertNull(store.read())
    }
    @Test fun failedFirstEnableDoesNotLeaveConsentAfterRestart() {
        val storage = MemoryStorage(); storage.failNext = true
        val store = AutomationSyncStore(storage, backend)
        assertNotNull(runCatching { store.write(AutomationSyncRecord(enabled = true, accountId = owner)) }.exceptionOrNull())
        assertNull(AutomationSyncStore(storage, backend).read())
    }
    @Test fun codecRejectsWrongBackendSchemaTypesAndUnknownPayloadFields() {
        invalid(JSONObject(encoded()).put("backend", "https://other.example.org").toString())
        invalid(JSONObject(encoded()).put("version", 2).toString())
        invalid(JSONObject(encoded()).put("enabled", "true").toString())
        invalid(JSONObject(encoded()).put("accountId", "not-an-account").toString())
        invalid(JSONObject(encoded()).put("revision", "4").toString())
        invalid(JSONObject(encoded()).put("revision", 4.5).toString())
        invalid(JSONObject(encoded()).put("revision", 2147483648L).toString())
        invalid(JSONObject(encoded()).put("failureCount", 6).toString())
        invalid(JSONObject(encoded()).put("snapshot", JSONObject().put("memory", "Sensitive note")).toString())
        invalid(encoded() + " trailing")
        invalid("[".repeat(9) + "0" + "]".repeat(9))
        invalid(" ".repeat(128 * 1024 + 1))
    }
    @Test fun codecRejectsBaselineGapsBadDigestsDuplicatesAndLimits() {
        invalid(JSONObject(encoded()).put("baseline", JSONObject.NULL).toString())
        invalid(JSONObject(encoded()).put("revision", JSONObject.NULL).toString())
        val duplicate = JSONObject(encoded())
        val chats = duplicate.getJSONObject("baseline").getJSONArray("chats")
        chats.put(chats.getJSONObject(0)); invalid(duplicate.toString())
        val plain = JSONObject(encoded())
        plain.getJSONObject("baseline").getJSONArray("chats").getJSONObject(0).put("signature", "private message")
        invalid(plain.toString())
        val many = JSONObject(encoded())
        many.getJSONObject("baseline").put("memoryDigests", JSONArray((0..10).map { it.toString().padStart(64, '0') }))
        invalid(many.toString())
        val duplicateMemory = JSONObject(encoded())
        duplicateMemory.getJSONObject("baseline").put("memoryDigests", JSONArray(listOf("b".repeat(64), "b".repeat(64))))
        invalid(duplicateMemory.toString())
        val count = JSONObject(encoded())
        count.getJSONObject("baseline").getJSONArray("chats").getJSONObject(0).put("messageCount", 501)
        invalid(count.toString())
    }
}
