package com.dua.ai.model

import org.json.JSONObject

/**
 * A write action the Dua Agent asked to do (open a PR, create an issue, comment, save or delete a note).
 * The server stores the exact action; the app only shows this summary and sends back [id] with approve or reject.
 * In memory only in the app: actions expire after 15 minutes; restored chats never show live cards.
 */
data class AgentApproval(
    val id: String,
    val tool: String,
    val title: String,
    val details: List<String> = emptyList(),
    /** Phone clock time (ms) after which the server no longer accepts it. */
    val expiresAt: Long = 0L,
    val status: Status = Status.Pending,
    /** Server answer after a decision (PR link, "Note saved.", error), shown on the card. */
    val result: String? = null,
    /** Bounded, redacted diff of the exact server-stored proposal. In memory only. */
    val diff: String? = null,
    val diffTruncated: Boolean = false,
) {
    enum class Status { Pending, Working, Done, Failed, Rejected, Expired }

    fun expired(now: Long) = status == Status.Pending && expiresAt in 1..now
}

object AgentApprovals {
    const val MAX_PER_REPLY = 3
    const val MAX_TITLE = 120
    const val MAX_DETAIL = 420
    const val MAX_DETAILS = 12
    const val MAX_RESULT = 600
    /** Server limit is 15 minutes; never trust a longer value from the stream. */
    const val MAX_SECONDS = 900
    private val ID = Regex("[A-Za-z0-9_-]{16,64}")
    private val TOOL = Regex("[a-z_]{1,32}")

    fun validId(id: String) = ID.matches(id)

    private fun clean(text: String, limit: Int) = text.replace(Regex("\\s+"), " ").trim().take(limit)

    /** The "approval" stream event, or null when it is malformed. */
    fun fromEvent(json: JSONObject, now: Long = System.currentTimeMillis()): AgentApproval? {
        val id = json.optString("id"); val tool = json.optString("tool")
        val title = clean(json.optString("title"), MAX_TITLE)
        if (!validId(id) || !TOOL.matches(tool) || title.isEmpty()) return null
        val details = json.optJSONArray("details")?.let { array ->
            (0 until minOf(array.length(), MAX_DETAILS)).mapNotNull { array.opt(it) as? String }.map { clean(it, MAX_DETAIL) }.filter { it.isNotEmpty() }
        }.orEmpty()
        val seconds = json.optInt("expiresIn", MAX_SECONDS).coerceIn(0, MAX_SECONDS)
        return AgentApproval(id, tool, title, details, now + seconds * 1000L,
            diff = (json.opt("diff") as? String)?.take(24_000),
            diffTruncated = json.optBoolean("diffTruncated") || (json.opt("diff") as? String)?.length?.let { it > 24_000 } == true)
    }

    /** Adds or replaces [approval] by id (at most [MAX_PER_REPLY] per reply). */
    fun merge(list: List<AgentApproval>, approval: AgentApproval): List<AgentApproval> =
        if (list.any { it.id == approval.id }) list.map { if (it.id == approval.id) approval else it }
        else if (list.size >= MAX_PER_REPLY) list else list + approval

    /** The decision request body for POST /api/agent/action. Only the id travels; the server holds the arguments. */
    fun decisionBody(id: String, approve: Boolean): JSONObject {
        require(validId(id)) { "Invalid approval." }
        return JSONObject().put("id", id).put("decision", if (approve) "approve" else "reject")
    }

    /** Status and text from the server's answer ({"status": "done"|"failed"|"rejected", "text"}). */
    fun outcome(json: JSONObject): Pair<AgentApproval.Status, String> {
        val text = clean(json.optString("text"), MAX_RESULT)
        val status = when (json.optString("status")) {
            "done" -> AgentApproval.Status.Done
            "rejected" -> AgentApproval.Status.Rejected
            else -> AgentApproval.Status.Failed
        }
        return status to text.ifEmpty { if (status == AgentApproval.Status.Failed) "The action failed." else "Done." }
    }

    /**
     * Line added to the reply text after a decision, so the result is saved with the chat and the Agent sees it as
     * context on the next turn (for example that the user declined).
     */
    fun transcriptLine(approval: AgentApproval): String = when (approval.status) {
        AgentApproval.Status.Done -> "\u2705 Approved: ${approval.title}. ${approval.result.orEmpty()}".trim()
        AgentApproval.Status.Rejected -> "\u274C You declined: ${approval.title}. Nothing was changed."
        AgentApproval.Status.Failed -> "\u26A0\uFE0F Approved but it failed: ${approval.title}. ${approval.result.orEmpty()}".trim()
        AgentApproval.Status.Expired -> "\u23F1\uFE0F Not done (approval expired): ${approval.title}."
        else -> ""
    }

    /** [text] with [line] added after the answer body but before a "Sources:" footer (so source chips keep working). */
    fun appendOutcome(text: String, line: String): String {
        if (line.isBlank()) return text
        val parts = ReplySources.split(text)
        if (parts.sources.isEmpty()) return text.trimEnd() + "\n\n" + line
        val at = text.lastIndexOf("\n\nSources:\n")
        return text.substring(0, at).trimEnd() + "\n\n" + line + text.substring(at)
    }
}
