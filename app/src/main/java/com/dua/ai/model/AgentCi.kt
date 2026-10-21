package com.dua.ai.model

import org.json.JSONObject

/** Observed CI evidence only. Never merge eligibility, a live subscription or approval arguments. */
data class AgentCi(val number: Int, val repository: String, val head: String, val state: String,
    val complete: Boolean, val headCurrent: Boolean, val checkedAt: Long,
    val details: String, val truncated: Boolean) {
    val url: String get() = "https://github.com/$repository/pull/$number/checks"
    companion object {
        fun fromJson(json: JSONObject): AgentCi? {
            val number = json.opt("number") as? Number ?: return null
            if (number.toDouble() != number.toInt().toDouble() || number.toInt() !in 1..999_999_999) return null
            val repository = json.opt("repository") as? String ?: return null
            val head = json.opt("head") as? String ?: return null
            if (!Regex("[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}").matches(repository) ||
                repository.substringAfter('/') in setOf(".", "..") || !Regex("[0-9a-f]{40}").matches(head)) return null
            val state = json.opt("state") as? String ?: return null
            if (state !in setOf("success", "failure", "pending", "not_run", "none", "unknown", "stale")) return null
            val seconds = (json.opt("checkedAt") as? Number)?.toDouble() ?: return null
            if (!seconds.isFinite() || seconds < 1 || seconds >= 1e11 || seconds % 1.0 != 0.0) return null
            val details = json.opt("details") as? String ?: return null
            val complete = json.opt("complete") as? Boolean ?: return null
            val current = json.opt("headCurrent") as? Boolean ?: return null
            // Even a malformed success event cannot turn missing/stale coverage green.
            val safeState = if (state == "success" && (!complete || !current)) "unknown" else state
            return AgentCi(number.toInt(), repository, head, safeState, complete, current,
                (seconds * 1000).toLong(), details.take(24_000), json.optBoolean("truncated") || details.length > 24_000)
        }
    }
}
