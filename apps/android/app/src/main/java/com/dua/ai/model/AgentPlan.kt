package com.dua.ai.model

import org.json.JSONObject

/** Agent-reported checklist, kept only in the current reply's coding activity. */
data class AgentPlanStep(val text: String, val status: String)
data class AgentPlan(val revision: Int, val steps: List<AgentPlanStep>, val explanation: String,
    val ended: Boolean = false) {
    companion object {
        fun fromJson(json: JSONObject): AgentPlan? {
            val number = json.opt("revision") as? Number ?: return null
            val revision = number.toInt()
            if (number.toDouble() != revision.toDouble() || revision !in 1..12) return null
            val explanation = json.opt("explanation") as? String ?: return null
            if (explanation.length > 500) return null
            val rows = json.optJSONArray("steps") ?: return null
            if (rows.length() !in 1..8) return null
            val steps = (0 until rows.length()).map { index ->
                val row = rows.optJSONObject(index) ?: return null
                val text = row.opt("step") as? String ?: return null
                val status = row.opt("status") as? String ?: return null
                if (text.isBlank() || text.length > 140 || text.any { it.code < 32 } ||
                    status !in setOf("pending", "in_progress", "completed")) return null
                AgentPlanStep(text, status)
            }
            if (steps.count { it.status == "in_progress" } > 1) return null
            return AgentPlan(revision, steps, explanation)
        }
    }
}
