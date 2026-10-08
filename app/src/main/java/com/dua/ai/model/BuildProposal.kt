package com.dua.ai.model

import org.json.JSONArray
import org.json.JSONObject

/** One file in a Build mode change proposal (Phase 5C). [content] is the complete new file (null for deletes or when dropped from history). */
data class ProposalFile(
    val path: String,
    val action: String,
    val content: String? = null,
    val baseSha: String? = null,
    val diff: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val diffTruncated: Boolean = false,
) {
    val name: String get() = path.substringAfterLast('/')
    /** "Added", "Changed" or "Deleted" (shown as a badge). */
    val label: String get() = when (action) { "create" -> "Added"; "delete" -> "Deleted"; else -> "Changed" }
}

/** The pull request the owner created from a proposal. */
data class BuildPullRequest(val url: String, val number: Int, val draft: Boolean, val branch: String = "", val existing: Boolean = false, val notice: String = "") {
    val label: String get() = (if (draft) "Draft PR" else "PR") + " #$number"
}

/**
 * A change proposal from Build mode: the model's `propose_changes` call, returned by the server for review.
 * Nothing was written to GitHub; only the owner's confirmed Create PR tap sends it to POST /api/build/pr.
 * Saved in local chat history only (not exported or synced), with the resulting [pullRequest] link.
 */
data class BuildProposal(
    val id: String,
    val repository: String,
    val baseBranch: String,
    val branch: String,
    val title: String,
    val summary: String,
    val files: List<ProposalFile>,
    val pullRequest: BuildPullRequest? = null,
) {
    val additions: Int get() = files.sumOf { it.additions }
    val deletions: Int get() = files.sumOf { it.deletions }
    /** Every create/update file still has its content, so a pull request can be created. */
    val complete: Boolean get() = files.all { it.action == "delete" || it.content != null } &&
        files.all { it.action == "create" || it.baseSha != null }
    val filesLabel: String get() = if (files.size == 1) "1 file" else "${files.size} files"
}

object BuildProposals {
    val ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    val BRANCH = Regex("^dua/[a-z0-9](?:[a-z0-9-]{0,70}[a-z0-9])?$")
    private val BASE = Regex("^[A-Za-z0-9._/-]{1,200}$")
    private val SHA = Regex("^[0-9a-f]{40}(?:[0-9a-f]{24})?$")
    private val ACTIONS = setOf("create", "update", "delete")
    const val MAX_FILES = 50
    const val MAX_TITLE = 120
    const val MAX_SUMMARY = 4000
    const val MAX_DIFF_CHARS = 60_000
    /** Diff text kept per file in local history. */
    const val SAVED_DIFF_CHARS = 20_000
    /** File contents are kept in history (to create the PR later) only up to this many characters in total. */
    const val SAVED_CONTENT_CHARS = 512 * 1024

    /** Same rules as the server's path check: relative, no `..`, `.git` or workflow files. */
    fun safePath(path: String): Boolean {
        if (path.isEmpty() || path.length > 400 || path != path.trim() || path.startsWith("/") || path.contains('\\') || path.any { it < ' ' || it == '\u007f' }) return false
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." || it.equals(".git", ignoreCase = true) }) return false
        return !path.lowercase().startsWith(".github/workflows/") && !path.equals(".gitmodules", ignoreCase = true)
    }

    /** Lenient decode of the server's `proposal` event / JSON field, or local history. Returns null when anything is unsafe. */
    fun fromJson(json: JSONObject?): BuildProposal? {
        json ?: return null
        val id = json.optString("id").takeIf { ID.matches(it) } ?: return null
        val repository = json.optString("repository").takeIf { BuildInfos.validRepository(it) } ?: return null
        val branch = json.optString("branch").takeIf { BRANCH.matches(it) } ?: return null
        val base = json.optString("baseBranch").takeIf { BASE.matches(it) } ?: return null
        val title = json.optString("title").trim().takeIf { it.isNotEmpty() && it.length <= MAX_TITLE } ?: return null
        val array = json.optJSONArray("files") ?: return null
        if (array.length() == 0 || array.length() > MAX_FILES) return null
        val files = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: return null
            val path = item.optString("path").takeIf { safePath(it) } ?: return null
            val action = item.optString("action").takeIf { it in ACTIONS } ?: return null
            val content = if (action != "delete" && item.has("content") && !item.isNull("content")) item.optString("content") else null
            val sha = if (action != "create") item.optString("baseSha").takeIf { SHA.matches(it) } else null
            ProposalFile(path, action, content, sha, item.optString("diff").take(MAX_DIFF_CHARS + 200),
                item.optInt("additions", 0).coerceAtLeast(0), item.optInt("deletions", 0).coerceAtLeast(0), item.optBoolean("truncated"))
        }
        if (files.map { it.path }.distinct().size != files.size) return null
        return BuildProposal(id, repository, base, branch, title, json.optString("summary").take(MAX_SUMMARY), files, pullRequest(json.optJSONObject("pullRequest"), repository))
    }

    /** The create-pr response (or saved link). Only https://github.com/<repository>/pull/<n> links are accepted. */
    fun pullRequest(json: JSONObject?, repository: String): BuildPullRequest? {
        json ?: return null
        val url = json.optString("url")
        val number = json.optInt("number", -1)
        if (number <= 0 || !url.equals("https://github.com/$repository/pull/$number", ignoreCase = true)) return null
        return BuildPullRequest(url, number, json.optBoolean("draft"), json.optString("branch").takeIf { BRANCH.matches(it) }.orEmpty(),
            json.optBoolean("existing"), json.optString("notice").take(300))
    }

    /** History form: diffs capped; contents dropped once the PR exists or when they are too large to keep. */
    fun toJson(proposal: BuildProposal): JSONObject {
        val keepContent = proposal.pullRequest == null && proposal.files.sumOf { it.content?.length ?: 0 } <= SAVED_CONTENT_CHARS
        return JSONObject().put("id", proposal.id).put("repository", proposal.repository).put("baseBranch", proposal.baseBranch)
            .put("branch", proposal.branch).put("title", proposal.title).put("summary", proposal.summary)
            .put("files", JSONArray().apply { proposal.files.forEach { file ->
                put(JSONObject().put("path", file.path).put("action", file.action).put("additions", file.additions).put("deletions", file.deletions)
                    .put("diff", file.diff.take(SAVED_DIFF_CHARS))
                    .apply {
                        if (file.diffTruncated || file.diff.length > SAVED_DIFF_CHARS) put("truncated", true)
                        if (keepContent && file.content != null) put("content", file.content)
                        file.baseSha?.let { put("baseSha", it) }
                    })
            } })
            .apply { proposal.pullRequest?.let { pr ->
                put("pullRequest", JSONObject().put("url", pr.url).put("number", pr.number).put("draft", pr.draft).put("branch", pr.branch)
                    .apply { if (pr.notice.isNotBlank()) put("notice", pr.notice) })
            } }
    }

    /** Body of POST /api/build/pr for the owner-approved proposal ([title] may be edited in the confirmation dialog). */
    fun createRequest(proposal: BuildProposal, title: String): JSONObject {
        val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= MAX_TITLE) { "Use a title of 1 to $MAX_TITLE characters." }
        require(proposal.complete) { "This proposal is no longer complete on this device. Ask Dua to propose the change again." }
        return JSONObject().put("repository", proposal.repository).put("proposalId", proposal.id).put("title", cleanTitle)
            .put("summary", proposal.summary).put("branch", proposal.branch)
            .put("files", JSONArray().apply { proposal.files.forEach { file ->
                put(JSONObject().put("path", file.path).put("action", file.action).apply {
                    if (file.action != "delete") put("content", file.content)
                    if (file.action != "create") put("baseSha", file.baseSha)
                })
            } })
    }
}
