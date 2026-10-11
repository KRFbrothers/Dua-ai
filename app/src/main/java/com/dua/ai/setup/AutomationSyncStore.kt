package com.dua.ai.setup

import android.content.Context
import com.dua.ai.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Durable consent and hashes only. Chat, draft, memory, credential and attachment contents are never stored here. */
interface AutomationSyncStorage {
    fun read(): String?
    /** Must complete a synchronous durable commit; false is treated as an unsafe state. */
    fun commit(value: String?): Boolean
}

class AutomationSyncStore(
    private val storage: AutomationSyncStorage,
    private val backend: String = BuildConfig.DUA_API_BASE_URL
) : AutomaticCloudSync.StorePort {
    constructor(context: Context) : this(PreferenceStorage(context))
    private var damaged = false

    @Synchronized override fun read(): AutomationSyncRecord? {
        check(!damaged) { "Automatic sync storage is unavailable. Disable and review sync before retrying." }
        return try { storage.read()?.let { AutomationSyncRecordCodec.decode(it, backend) } }
        catch (failure: Exception) {
            damaged = true
            throw IllegalStateException("Saved automatic sync settings could not be read. Automatic sync is paused; disable and review sync to reset them.", failure)
        }
    }

    @Synchronized override fun write(record: AutomationSyncRecord) {
        check(!damaged) { "Automatic sync storage is unavailable. Disable and review sync before retrying." }
        val raw = AutomationSyncRecordCodec.encode(record, backend)
        val previous = try { storage.read()?.also { AutomationSyncRecordCodec.decode(it, backend) } }
        catch (failure: Exception) {
            damaged = true
            throw IllegalStateException("Saved automatic sync settings could not be read. Automatic sync is paused.", failure)
        }
        commit(raw, previous)
    }

    /** An explicit disable/reset may remove a damaged record. It never silently enables consent again. */
    @Synchronized override fun clear() {
        val previous = runCatching { storage.read() }.getOrNull()
        commit(null, previous)
        damaged = false
    }

    private fun commit(raw: String?, previous: String?) {
        val saved = try { storage.commit(raw) } catch (failure: Exception) {
            damaged = true
            persistPause(previous)
            throw IllegalStateException("Automatic sync settings could not be saved. Automatic sync is paused.", failure)
        }
        if (!saved) {
            damaged = true
            // A failed SharedPreferences commit may still change the process's in-memory value.
            persistPause(previous)
            throw IllegalStateException("Automatic sync settings could not be saved. Automatic sync is paused.")
        }
    }

    private fun persistPause(previous: String?) {
        // Keep the previous deletion baseline if a second durable commit is possible.
        // If the disk refuses both commits, throwing still prevents any upload in this process.
        val paused = runCatching { previous?.let { value ->
            val record = AutomationSyncRecordCodec.decode(value, backend)
            if (record.accountId == null) null else AutomationSyncRecordCodec.encode(
                record.copy(enabled = false, reviewRequired = true, failureCount = 5), backend)
        } }.getOrNull()
        runCatching { storage.commit(paused) }
    }

    private class PreferenceStorage(context: Context) : AutomationSyncStorage {
        private val preferences = context.getSharedPreferences("dua_auto_sync", Context.MODE_PRIVATE)
        override fun read(): String? = preferences.getString("record", null)
        override fun commit(value: String?): Boolean = preferences.edit().apply {
            if (value == null) remove("record") else putString("record", value)
        }.commit()
    }
}

object AutomationSyncRecordCodec {
    private const val MAX_BYTES = 128 * 1024
    private val rootKeys = setOf("version", "backend", "enabled", "accountId", "revision", "baseline", "reviewRequired", "failureCount")
    private fun keys(value: JSONObject): Set<String> = value.keys().asSequence().toSet()
    private fun requireKeys(value: JSONObject, required: Set<String>) { require(keys(value) == required) { "Invalid automatic sync record." } }
    private fun text(value: JSONObject, key: String): String = (value.get(key) as? String ?: error("Invalid automatic sync text."))
    private fun boolean(value: JSONObject, key: String): Boolean = (value.get(key) as? Boolean ?: error("Invalid automatic sync flag."))
    private fun integer(value: JSONObject, key: String, max: Long): Long {
        val raw = value.get(key)
        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() >= 0 && raw.toDouble() <= max && raw.toDouble() == raw.toLong().toDouble()) { "Invalid automatic sync number." }
        return raw.toLong()
    }
    private fun validateShape(raw: String) {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Automatic sync record is too large." }
        var depth = 0; var quoted = false; var escaped = false
        for (char in raw) {
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 8) { "Automatic sync record is too deeply nested." } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid automatic sync record." } }
            }
        }
        require(!quoted && depth == 0) { "Invalid automatic sync record." }
    }

    fun encode(record: AutomationSyncRecord, backend: String): String {
        AutomationSyncPolicy.validateRecord(record)
        val baseline = record.baseline?.let { value -> JSONObject()
            .put("chats", JSONArray().apply {
                value.chatMessageCounts.toSortedMap().forEach { (id, count) -> put(JSONObject()
                    .put("id", id).put("messageCount", count).put("signature", value.chatSignatures.getValue(id))) }
            }).put("memoryDigests", JSONArray(value.memoryDigests.sorted()))
            .put("projectSignatures", JSONObject(value.projectSignatures)) }
        val raw = JSONObject().put("version", 1).put("backend", backend).put("enabled", record.enabled)
            .put("accountId", record.accountId ?: JSONObject.NULL).put("revision", record.revision ?: JSONObject.NULL)
            .put("baseline", baseline ?: JSONObject.NULL).put("reviewRequired", record.reviewRequired)
            .put("failureCount", record.failureCount).toString()
        validateShape(raw)
        return raw
    }

    fun decode(raw: String, backend: String): AutomationSyncRecord {
        validateShape(raw)
        val tokener = JSONTokener(raw)
        val json = tokener.nextValue() as? JSONObject ?: error("Invalid automatic sync record.")
        require(tokener.nextClean() == '\u0000') { "Invalid trailing automatic sync data." }
        requireKeys(json, rootKeys)
        require(integer(json, "version", 1) == 1L && text(json, "backend") == backend) { "Automatic sync backend or format changed. Review sync before enabling it." }
        val baseline = if (json.isNull("baseline")) null else {
            val data = json.getJSONObject("baseline")
            require(keys(data) == setOf("chats", "memoryDigests") || keys(data) == setOf("chats", "memoryDigests", "projectSignatures")) { "Invalid automatic sync baseline." }
            val chats = data.getJSONArray("chats")
            require(chats.length() <= 100) { "Invalid automatic sync chat baseline." }
            val counts = linkedMapOf<String, Int>(); val signatures = linkedMapOf<String, String>()
            repeat(chats.length()) { index ->
                val item = chats.getJSONObject(index)
                requireKeys(item, setOf("id", "messageCount", "signature"))
                val id = text(item, "id")
                require(!counts.containsKey(id)) { "Duplicate automatic sync chat ID." }
                counts[id] = integer(item, "messageCount", 500).toInt()
                signatures[id] = text(item, "signature")
            }
            val notes = data.getJSONArray("memoryDigests")
            require(notes.length() <= 10) { "Invalid automatic sync memory baseline." }
            val digests = (0 until notes.length()).map { notes.get(it) as? String ?: error("Invalid automatic sync digest.") }
            require(digests.toSet().size == digests.size) { "Duplicate automatic sync memory digest." }
            val projects = data.optJSONObject("projectSignatures")?.let { p -> p.keys().asSequence().associateWith { text(p, it) } } ?: emptyMap()
            require(!data.has("projectSignatures") || data.get("projectSignatures") is JSONObject)
            AutomationBaseline(counts, signatures, digests.toSet(), projects)
        }
        return AutomationSyncRecord(boolean(json, "enabled"), if (json.isNull("accountId")) null else text(json, "accountId"),
            if (json.isNull("revision")) null else integer(json, "revision", 2147483647), baseline,
            boolean(json, "reviewRequired"), integer(json, "failureCount", 5).toInt()).also { AutomationSyncPolicy.validateRecord(it) }
    }
}
