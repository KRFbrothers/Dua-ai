package com.dua.ai.account

import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CloudSnapshot(val chats: List<SavedChat>, val memory: List<String>)
data class CloudReview(val revision: Long, val snapshot: CloudSnapshot, val ownerId: String? = null)
data class CloudMergeResult(val chats: List<SavedChat>, val added: Int, val conflicts: Int)
object CloudSnapshotCodec {
    fun encode(value: CloudSnapshot): JSONObject {
        require(value.memory.size <= 10 && value.memory.all { it.isNotBlank() && it.length <= 200 }) { "Cloud memory limit is 10 notes of 200 characters." }
        val json = JSONObject(ChatHistoryCodec.export(value.chats, includeServerFields = true)).put("format", "dua-cloud-snapshot").put("memory", JSONArray(value.memory)).apply { remove("exportedAt") }
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
        return CloudSnapshot(ChatHistoryCodec.parseImport(copy.toString(), includeServerFields = true), memory)
    }
    private fun same(a: SavedChat, b: SavedChat): Boolean {
        fun normalized(chat: SavedChat): String {
            val root = JSONObject(ChatHistoryCodec.export(listOf(chat), exportedAt = 0, includeServerFields = true))
            val item = root.getJSONArray("chats").getJSONObject(0)
            item.remove("id")
            val messages = item.getJSONArray("messages")
            for (index in 0 until messages.length()) messages.getJSONObject(index).remove("id")
            return root.toString()
        }
        return normalized(a) == normalized(b)
    }
    fun merge(local: List<SavedChat>, remote: List<SavedChat>, makeId: () -> String = { UUID.randomUUID().toString() }): CloudMergeResult {
        val merged = local.toMutableList(); var added = 0; var conflicts = 0
        remote.forEach { chat ->
            if (local.any { same(it, chat) }) return@forEach
            var copy = chat
            if (local.any { it.id == chat.id }) { var id: String; do { id = makeId() } while (merged.any { it.id == id }); copy = chat.copy(id = id); conflicts++ }
            merged += copy; added++
        }
        return CloudMergeResult(merged, added, conflicts)
    }
}
