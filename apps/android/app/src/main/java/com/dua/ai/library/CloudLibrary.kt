package com.dua.ai.library

import com.dua.ai.files.FileRef
import com.dua.ai.files.StoredFileTypes
import org.json.JSONObject

data class CloudLibraryState(val ownerId: String? = null, val files: List<FileRef> = emptyList(), val loading: Boolean = false, val notice: String = "")

/** Ephemeral, owner-scoped server inventory; never serialized into snapshots or sent as chat attachments. */
object CloudLibrary {
    fun files(json: JSONObject, ownerId: String): List<FileRef> {
        val values = json.getJSONArray("files")
        require(values.length() <= 10000) { "Cloud library is too large. Narrow the stored file collection." }
        return (0 until values.length()).mapNotNull { i ->
            val item = values.getJSONObject(i)
            val path = item.getString("path")
            if (!StoredFileTypes.PATH.matches(path) || path.substringBefore('/') != ownerId.lowercase()) return@mapNotNull null
            FileRef(path, item.getString("name").take(200), item.getString("mimeType").take(100), item.getLong("size").coerceAtLeast(0))
        }.distinctBy { it.path }
    }
}
