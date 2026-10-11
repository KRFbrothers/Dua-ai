package com.dua.ai.account

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class CloudSnapshot(val chats: List<SavedChat>, val memory: List<String>, val projects: List<com.dua.ai.projects.ChatProject> = com.dua.ai.projects.ProjectCodec.defaults)
data class CloudReview(val revision: Long, val snapshot: CloudSnapshot, val ownerId: String? = null)
data class CloudDownloadResult(val snapshot: CloudSnapshot, val added: Int, val conflicts: Int)
object CloudSnapshotCodec {
    fun encode(value: CloudSnapshot): JSONObject {
        require(value.memory.size <= 10 && value.memory.all { it.isNotBlank() && it.length <= 200 }) { "Cloud memory limit is 10 notes of 200 characters." }
        val json = JSONObject(ChatHistoryCodec.export(value.chats, includeServerFields = true, projects = value.projects)).put("format", "dua-cloud-snapshot").put("memory", JSONArray(value.memory)).apply { remove("exportedAt") }
        require(json.toString().toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024) { "Cloud snapshot limit is 4 MiB." }
        decode(json)
        return json
    }
    fun decode(json: JSONObject): CloudSnapshot {
        require(json.toString().toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024 && json.get("format") == "dua-cloud-snapshot" && json.get("version") == 1) { "Invalid cloud snapshot." }
        val notes = json.getJSONArray("memory")
        val memory = (0 until notes.length()).map { notes.get(it) as? String ?: error("Invalid memory note") }
        require(memory.size <= 10 && memory.all { it.isNotBlank() && it.length <= 200 }) { "Invalid cloud memory." }
        val copy = JSONObject(json.toString()).put("format", "dua-chat-export").put("exportedAt", 0)
        // The backend validates cloud metadata; retain the existing lenient wire decoders here.
        return CloudSnapshot(ChatHistoryCodec.parseImport(copy.toString(), includeServerFields = true, strictMetadata = false), memory,
            com.dua.ai.projects.ProjectCodec.fromJson(if (json.has("projects")) json.getJSONArray("projects") else null))
    }
    private fun content(chat: SavedChat): String {
        val root = JSONObject(ChatHistoryCodec.export(listOf(chat), exportedAt = 0, includeServerFields = true))
        val item = root.getJSONArray("chats").getJSONObject(0)
        item.remove("id")
        return item.toString()
    }

    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Reviewed downloads keep server IDs authoritative and preserve differing phone versions as copies. */
    fun mergeDownload(local: CloudSnapshot, remote: CloudSnapshot): CloudDownloadResult {
        encode(local)
        encode(remote)
        val serverById = remote.chats.associateBy { it.id }
        val localIds = local.chats.map { it.id }.toSet()
        val added = remote.chats.filter { it.id !in localIds }
        val merged = (local.chats.map { serverById[it.id] ?: it } + added).toMutableList()
        val usedIds = merged.map { it.id }.toMutableSet()
        val preserved = merged.filter { it.id.startsWith("unsynced-") }.map { digest(content(it)) }.toMutableSet()
        var conflicts = 0
        local.chats.forEach { here ->
            val server = serverById[here.id] ?: return@forEach
            val original = digest(content(here))
            if (original == digest(content(server))) return@forEach
            // Keep message IDs and all metadata; only the chat ID changes. Repeated reviews reuse copies.
            if (original in preserved) return@forEach
            val stem = "unsynced-" + digest(here.id + ":" + original).take(16)
            var id = stem
            var suffix = 0
            while (id in usedIds) id = "$stem-${++suffix}"
            usedIds += id
            preserved += original
            merged += here.copy(id = id)
            conflicts++
        }
        val projects = remote.projects + local.projects.filter { here -> remote.projects.none { it.id == here.id } }
        val snapshot = CloudSnapshot(merged, remote.memory.distinct(), projects)
        // The union can exceed limits even when each input fits. Fail before changing cache or memory.
        encode(snapshot)
        return CloudDownloadResult(snapshot, added.size, conflicts)
    }
}
