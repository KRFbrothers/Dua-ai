package com.dua.ai.model

/** One live Agent step ("Searching the web…"). In memory only: shown while the reply streams. */
data class AgentStep(val id: String, val label: String, val done: Boolean = false)

object AgentProgress {
    const val MAX_STEPS = 12
    const val MAX_LABEL = 60
    private val ID = Regex("[a-z_]{1,24}")

    fun validId(id: String) = ID.matches(id)

    /** Upserts [id]. A newly active step finishes every earlier one; a known step only moves forward (never un-done). */
    fun apply(steps: List<AgentStep>, id: String, label: String, done: Boolean): List<AgentStep> {
        val clean = label.replace(Regex("\\s+"), " ").trim().take(MAX_LABEL)
        if (!validId(id) || clean.isEmpty()) return steps
        val index = steps.indexOfFirst { it.id == id }
        if (index >= 0) return steps.mapIndexed { i, step -> when {
            i == index -> step.copy(label = clean, done = step.done || done)
            !done && i < index -> step.copy(done = true)
            else -> step
        } }
        if (steps.size >= MAX_STEPS) return steps
        val finished = if (done) steps else steps.map { it.copy(done = true) }
        return finished + AgentStep(id, clean, done)
    }

    fun finish(steps: List<AgentStep>): List<AgentStep> = if (steps.all { it.done }) steps else steps.map { it.copy(done = true) }
}
