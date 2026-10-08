package com.dua.ai.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Code the provider ran in its own sandbox for a reply (Run code, Phase 5) and what it printed.
 * [output] is null while the code is still running; [outcome] is "ok", "error" or "timeout" when known.
 */
data class CodeRun(val language: String = "python", val code: String, val output: String? = null, val outcome: String? = null) {
    val running: Boolean get() = output == null && outcome == null
    val failed: Boolean get() = outcome == "error" || outcome == "timeout"
}

/** Wire and history format for [CodeRun]s (`code` events, JSON replies and local chat history). */
object CodeRuns {
    const val MAX_RUNS = 10
    const val MAX_CHARS = 20_000
    private val LANGUAGE = Regex("[A-Za-z0-9+#._-]{1,20}")
    private val OUTCOMES = setOf("ok", "error", "timeout")

    fun language(value: String?): String = value?.takeIf { LANGUAGE.matches(it) }?.lowercase() ?: "python"
    fun outcome(value: String?): String? = value?.takeIf { it in OUTCOMES }

    /** Merges one `code` event into [runs]: a new run at [index] (with code) or the output for an existing one. */
    fun merge(runs: List<CodeRun>, index: Int, language: String?, code: String?, output: String?, outcome: String?): List<CodeRun> {
        if (index < 0 || index >= MAX_RUNS) return runs
        val existing = runs.getOrNull(index)
        return when {
            existing == null && index == runs.size && !code.isNullOrBlank() -> runs + CodeRun(language(language), code.take(MAX_CHARS), output?.take(MAX_CHARS), outcome(outcome))
            existing != null -> runs.toMutableList().also { list ->
                list[index] = existing.copy(
                    code = code?.takeIf { it.isNotBlank() }?.take(MAX_CHARS) ?: existing.code,
                    output = output?.take(MAX_CHARS) ?: existing.output ?: if (outcome != null) "" else null,
                    outcome = outcome(outcome) ?: existing.outcome)
            }
            else -> runs
        }
    }

    fun toJson(runs: List<CodeRun>): JSONArray = JSONArray().apply {
        runs.take(MAX_RUNS).forEach { run ->
            put(JSONObject().put("language", run.language).put("code", run.code.take(MAX_CHARS)).apply {
                run.output?.let { put("output", it.take(MAX_CHARS)) }
                run.outcome?.let { put("outcome", it) }
            })
        }
    }

    /** Lenient: invalid entries are skipped, so a damaged history entry never hides the whole chat. */
    fun fromJson(array: JSONArray?): List<CodeRun> {
        if (array == null) return emptyList()
        return (0 until minOf(array.length(), MAX_RUNS)).mapNotNull { array.optJSONObject(it) }.mapNotNull { item ->
            val code = item.optString("code").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CodeRun(language(item.optString("language")), code.take(MAX_CHARS),
                if (item.has("output")) item.optString("output").take(MAX_CHARS) else null, outcome(item.optString("outcome")))
        }
    }

    /** Plain-text copy for "Copy response": the code and its output after the answer. */
    fun copyText(runs: List<CodeRun>): String = runs.joinToString("") { run ->
        "\n\n```${run.language}\n${run.code.trimEnd()}\n```" + (run.output?.takeIf { it.isNotBlank() }?.let { "\nOutput:\n```\n${it.trimEnd()}\n```" } ?: "")
    }
}
