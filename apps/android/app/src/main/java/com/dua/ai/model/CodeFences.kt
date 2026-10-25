package com.dua.ai.model

/** Shared extraction for the Library inventory and opt-in artifact previews. */
object CodeFences {
    fun parse(text: String, includeUnclosed: Boolean = true, preserveTrailingNewlines: Boolean = false): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var marker = ""
        var language = ""
        val body = StringBuilder()
        text.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (marker.isEmpty()) {
                val fence = trimmed.takeWhile { it == '`' || it == '~' }
                if (fence.length >= 3 && fence.all { it == fence[0] }) {
                    marker = fence; language = trimmed.drop(fence.length).trim(); body.clear()
                }
            } else if (trimmed.takeWhile { it == marker[0] }.length >= marker.length && trimmed.trimEnd().all { it == marker[0] }) {
                result += language to body.toString().let { if (preserveTrailingNewlines) it else it.trimEnd('\n') }; marker = ""
            } else body.append(line).append('\n')
        }
        if (includeUnclosed && marker.isNotEmpty() && body.isNotEmpty())
            result += language to body.toString().let { if (preserveTrailingNewlines) it else it.trimEnd('\n') }
        return result
    }
}
