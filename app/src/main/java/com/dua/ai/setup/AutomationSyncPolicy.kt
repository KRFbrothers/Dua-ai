package com.dua.ai.setup

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.history.ChatHistoryCodec
import org.json.JSONObject
import java.security.MessageDigest

/** Only digests/counts are persisted; never duplicate chat or memory plaintext in sync settings. */
data class AutomationBaseline(val chatMessageCounts: Map<String, Int>, val chatSignatures: Map<String, String>, val memoryDigests: Set<String>)
data class AutomationSyncRecord(val enabled: Boolean = false, val accountId: String? = null, val revision: Long? = null,
    val baseline: AutomationBaseline? = null, val reviewRequired: Boolean = false, val failureCount: Int = 0)
data class AutomationMergePlan(val snapshot: CloudSnapshot, val requiresContentReview: Boolean)

object AutomationSyncPolicy {
    private val accountPattern = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    private val digestPattern = Regex("[a-f0-9]{64}")
    fun digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun canonical(chat: SavedChat): String {
        // Hash the complete server-backed representation, but omit IDs so imported copies do not
        // look like content edits. The plaintext exists only transiently for the digest.
        val json = JSONObject(ChatHistoryCodec.export(listOf(chat), exportedAt = 0, includeServerFields = true))
        val item = json.getJSONArray("chats").getJSONObject(0)
        item.remove("id")
        val messages = item.getJSONArray("messages")
        for (index in 0 until messages.length()) messages.getJSONObject(index).remove("id")
        return json.toString()
    }
    fun signature(chat: SavedChat): String = digest(canonical(chat))
    fun validate(snapshot: CloudSnapshot): CloudSnapshot { CloudSnapshotCodec.encode(snapshot); return snapshot }
    fun baseline(snapshot: CloudSnapshot): AutomationBaseline = AutomationBaseline(snapshot.chats.associate { it.id to it.messages.size }, snapshot.chats.associate { it.id to signature(it) }, snapshot.memory.map(::digest).toSet())
    fun same(left: CloudSnapshot, right: CloudSnapshot): Boolean = baseline(left) == baseline(right)

    fun validateRecord(value: AutomationSyncRecord): AutomationSyncRecord {
        require(value.failureCount in 0..5 && (value.accountId == null || accountPattern.matches(value.accountId))) { "Invalid automatic sync settings." }
        require(!value.enabled || value.accountId != null) { "Automatic sync consent must belong to an account." }
        require((value.baseline == null) == (value.revision == null) && (value.revision == null || value.revision in 0..2147483647L)) { "Invalid automatic sync revision." }
        value.baseline?.let { baseline ->
            require(value.accountId != null && baseline.chatMessageCounts.size <= 100 && baseline.chatMessageCounts.keys == baseline.chatSignatures.keys && baseline.memoryDigests.size <= 10)
            require(baseline.chatMessageCounts.all { (id, count) -> id.isNotBlank() && id.length <= 200 && count in 0..500 })
            require(baseline.chatSignatures.values.all(digestPattern::matches) && baseline.memoryDigests.all(digestPattern::matches))
        }
        return value
    }

    fun requiresDeletionReview(previous: AutomationBaseline?, snapshot: CloudSnapshot): Boolean {
        if (previous == null) return false
        val current = baseline(snapshot)
        // Memory deletions are intentional and propagate to the server through merge; only chat loss needs review.
        return previous.chatMessageCounts.any { (id, count) -> current.chatMessageCounts[id]?.let { it < count } ?: true }
    }

    /**
     * Server-first merge: the cloud copy is authoritative. A chat that exists on the server replaces the
     * phone's cached copy with the same id. If the phone copy was edited since the last sync (an offline
     * edit), it is kept as a separate "unsynced-" copy so nothing is lost silently. Before a first sync
     * establishes that baseline, a transient first-read baseline protects edits made during its account
     * or cloud requests. Unchanged pre-existing cache still yields to the server. Chats that exist only
     * on the phone are kept so they can upload. Memory follows the server; notes the user deleted on the
     * phone since the last sync are removed from the server copy too, and notes added on the phone are kept.
     */
    fun merge(local: CloudSnapshot, remote: CloudSnapshot, previous: AutomationBaseline?, firstRead: AutomationBaseline? = null): AutomationMergePlan {
        validate(local); validate(remote)
        val remoteById = remote.chats.associateBy { it.id }
        val usedIds = (local.chats.map { it.id } + remote.chats.map { it.id }).toMutableSet()
        val chats = mutableListOf<SavedChat>()
        val copies = mutableListOf<SavedChat>()
        local.chats.forEach { here ->
            val server = remoteById[here.id]
            if (server == null) { chats += here; return@forEach }
            chats += server
            val hereSignature = signature(here)
            val editedOffline = (previous ?: firstRead)?.chatSignatures?.get(here.id).let { it != null && it != hereSignature }
            if (editedOffline && hereSignature != signature(server)) {
                val stem = "unsynced-" + digest(here.id + ":" + hereSignature).take(16)
                var id = stem; var suffix = 0
                while (id in usedIds) id = "$stem-${++suffix}"
                usedIds += id; copies += here.copy(id = id)
            }
        }
        val localIds = local.chats.map { it.id }.toSet()
        remote.chats.sortedBy { it.id }.filter { it.id !in localIds }.forEach { chats += it }
        chats += copies
        val memory = if (previous == null && remote.memory.isEmpty() && remote.chats.isEmpty()) local.memory
        else if (previous == null) remote.memory
        else {
            val localDigests = local.memory.map(::digest).toSet()
            val kept = remote.memory.filter { digest(it) !in previous.memoryDigests || digest(it) in localDigests }
            (kept + local.memory.filter { digest(it) !in previous.memoryDigests }).distinct()
        }
        return AutomationMergePlan(validate(CloudSnapshot(chats, memory.distinct())), false)
    }

    /** Stop after five failures; retry should require an explicit user action thereafter. */
    fun retryDelayMillis(failureCount: Int): Long? = if (failureCount in 1..4) minOf(60_000L, 1_000L shl failureCount) else null
}
