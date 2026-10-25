package com.dua.ai.model

/**
 * Search answers cite sources as [1], [2] or [1][2]. GitHub-flavoured markdown can read
 * those as reference links and swallow the numbers, so bare markers are escaped before
 * rendering. Real links ([1](https://…)), reference links ([text][1]), link definitions and code are left alone.
 */
object CitationMarkers {
    private val run = Regex("""(?<![\\\]\w])(?:\[\d{1,3}])+(?![(:\[])""")
    private val marker = Regex("""\[(\d{1,3})]""")
    private val fence = Regex("""^\s*(```|~~~)""")

    fun escape(markdown: String): String {
        if (!markdown.contains('[')) return markdown
        var inFence = false
        return markdown.split('\n').joinToString("\n") { line ->
            if (fence.containsMatchIn(line)) { inFence = !inFence; line }
            else if (inFence || line.startsWith("    ") || line.startsWith("\t")) line
            else line.split('`').mapIndexed { index, part -> if (index % 2 == 1) part else part.replace(run) { found -> found.value.replace(marker) { "\\[" + it.groupValues[1] + "\\]" } } }.joinToString("`")
        }
    }
}
