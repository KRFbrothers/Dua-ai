package com.dua.ai.model

import org.json.JSONArray
import org.json.JSONObject

/** One repository file the server read for a Build mode reply. */
data class BuildFile(val path: String, val bytes: Int = 0, val truncated: Boolean = false) {
    val name: String get() = path.substringAfterLast('/')
}

/** Build mode (Phase 5B, read-only): the repository a reply is about and the files the server read for it. */
data class BuildInfo(val repository: String, val branch: String = "", val files: List<BuildFile> = emptyList()) {
    val shortName: String get() = repository.substringAfter('/')
    /** Read-only link to the file on GitHub (the default branch the server read). */
    fun fileUrl(file: BuildFile): String = "https://github.com/$repository/blob/" +
        java.net.URLEncoder.encode(branch.ifBlank { "HEAD" }, "UTF-8").replace("%2F", "/") + "/" +
        file.path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}

object BuildInfos {
    val REPOSITORY = Regex("^[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_.-]{1,100}$")
    private val BRANCH = Regex("^[A-Za-z0-9._/-]{1,200}$")
    const val MAX_FILES = 100
    fun validRepository(value: String?): Boolean = value != null && REPOSITORY.matches(value)

    /** Lenient: the server's `build` event / JSON, or the server snapshot and phone cache. Invalid entries are dropped. */
    fun fromJson(json: JSONObject?): BuildInfo? {
        val repository = json?.optString("repository")?.takeIf { validRepository(it) } ?: return null
        val branch = json.optString("branch").takeIf { BRANCH.matches(it) }.orEmpty()
        val array = json.optJSONArray("files") ?: JSONArray()
        val files = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull { item ->
            val path = item.optString("path")
            if (path.isBlank() || path.length > 400 || path.startsWith("/") || path.split('/').any { it == ".." }) null
            else BuildFile(path, item.optInt("bytes", 0).coerceAtLeast(0), item.optBoolean("truncated"))
        }.distinctBy { it.path }.take(MAX_FILES)
        return BuildInfo(repository, branch, files)
    }

    fun toJson(info: BuildInfo): JSONObject = JSONObject().put("repository", info.repository).apply { if (info.branch.isNotBlank()) put("branch", info.branch) }
        .put("files", JSONArray().apply { info.files.take(MAX_FILES).forEach { file ->
            put(JSONObject().put("path", file.path).put("bytes", file.bytes).apply { if (file.truncated) put("truncated", true) })
        } })
}
