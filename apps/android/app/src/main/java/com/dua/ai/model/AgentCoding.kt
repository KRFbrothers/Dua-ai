package com.dua.ai.model

import org.json.JSONObject

/** Ephemeral UI data: not a source of approval arguments or persisted chat content. */
data class AgentTerminal(val id: String, val command: String = "Command", val state: String,
    val output: String = "", val exitCode: Int? = null, val truncated: Boolean = false, val profile: String? = null) {
    val active: Boolean get() = state in setOf("preparing", "queued", "running")
}
data class AgentPrTarget(val number: Int, val branch: String)
data class AgentWorkspace(val repository: String, val base: String, val paths: List<String>,
    val checkpointId: String?, val revision: Int, val saved: Boolean, val prTarget: AgentPrTarget? = null)
data class AgentCoding(val workspace: AgentWorkspace? = null, val terminals: List<AgentTerminal> = emptyList(),
    val diff: String? = null, val diffTruncated: Boolean = false, val ci: List<AgentCi> = emptyList(), val plan: AgentPlan? = null) {
    fun ended() = copy(terminals = terminals.map { if (it.active) it.copy(state = "interrupted") else it }, plan = plan?.copy(ended = true))
}
data class AgentCodingUpdate(val workspace: AgentWorkspace?, val terminal: AgentTerminal?, val diff: String?,
    val diffTruncated: Boolean, val resetDiff: Boolean, val hasCommand: Boolean, val ci: AgentCi? = null, val plan: AgentPlan? = null) {
    fun apply(previous: AgentCoding?): AgentCoding {
        val before = previous ?: AgentCoding()
        val runs = terminal?.let { next ->
            val old = before.terminals.find { it.id == next.id }
            // A timed-out worker can finish a blocked SSE enqueue after the
            // final event. Such a live snapshot cannot reopen a finished card.
            if (old != null && ((!old.active && next.active) || (old.state == "running" && next.state == "queued"))) return@let before.terminals
            val merged = if (!hasCommand && old != null) next.copy(command = old.command, profile = next.profile ?: old.profile) else next
            if (old == null) (before.terminals + merged).takeLast(4)
            else before.terminals.map { if (it.id == next.id) merged else it }
        } ?: before.terminals
        return AgentCoding(workspace ?: before.workspace, runs,
            if (resetDiff) null else diff ?: before.diff,
            if (resetDiff) false else if (diff != null) diffTruncated else before.diffTruncated,
            ci?.let { next -> (before.ci.filterNot { it.repository == next.repository && it.number == next.number } + next).takeLast(2) } ?: before.ci,
            plan?.takeIf { before.plan == null || (!before.plan.ended && it.revision > before.plan.revision) } ?: before.plan)
    }
    companion object {
        private val id = Regex("[0-9a-f]{32}")
        private val path = Regex("[A-Za-z0-9._/ \\-]{1,300}")
        fun fromEvent(json: JSONObject): AgentCodingUpdate? {
            val workspace = json.optJSONObject("workspace")?.let { w ->
                val repo = w.optString("repository"); val base = w.optString("base")
                val paths = w.optJSONArray("paths") ?: return null
                if (!Regex("[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}").matches(repo) ||
                    !Regex("[0-9a-f]{40}").matches(base) || paths.length() > 10) return null
                val names = (0 until paths.length()).map { paths.opt(it) as? String ?: return null }
                if (names.any { !path.matches(it) || it.startsWith('/') || it.split('/').any { p -> p in listOf("", ".", "..") } }) return null
                val checkpoint = (w.opt("checkpointId") as? String)?.also { if (!id.matches(it)) return null }
                val revision = w.optInt("revision", 0)
                if (revision < 0 || (checkpoint != null && revision < 1)) return null
                val target = if (w.has("prTarget") && !w.isNull("prTarget")) {
                    val t = w.optJSONObject("prTarget") ?: return null
                    val number = t.opt("number") as? Number ?: return null
                    val branch = t.opt("branch") as? String ?: return null
                    if (number.toDouble() != number.toInt().toDouble() || number.toInt() !in 1..999_999_999 ||
                        !Regex("dua-agent/[a-z0-9](?:[a-z0-9-]{0,78}[a-z0-9])?").matches(branch) || t.optString("head_sha") != base) return null
                    AgentPrTarget(number.toInt(), branch)
                } else null
                AgentWorkspace(repo, base, names, checkpoint, revision, w.optBoolean("saved") && checkpoint != null, target)
            }
            val terminal = json.optJSONObject("terminal")?.let { t ->
                val key = t.optString("id"); val state = t.optString("state")
                if (!Regex("[0-9a-f]{16}").matches(key) || state !in setOf("preparing", "queued", "running", "succeeded", "failed", "timed_out", "cancelled", "error")) return null
                val profile = if (t.has("profile")) {
                    (t.opt("profile") as? String)?.takeIf { it in setOf("python", "jvm", "android") } ?: return null
                } else null
                val output = t.optString("output")
                AgentTerminal(key, t.optString("command", "Command").take(600), state, output.take(32_000),
                    (t.opt("exitCode") as? Number)?.toInt(), t.optBoolean("truncated") || output.length > 32_000, profile)
            }
            val ci = json.optJSONObject("ci")?.let { AgentCi.fromJson(it) ?: return null }
            val plan = if (json.has("plan")) AgentPlan.fromJson(json.optJSONObject("plan") ?: return null) ?: return null else null
            val diff = (json.opt("diff") as? String)
            val reset = json.optBoolean("resetDiff")
            if (workspace == null && terminal == null && diff == null && !reset && ci == null && plan == null) return null
            return AgentCodingUpdate(workspace, terminal, diff?.take(24_000),
                json.optBoolean("diffTruncated") || (diff?.length ?: 0) > 24_000, reset,
                json.optJSONObject("terminal")?.has("command") == true, ci, plan)
        }
    }
}
