package com.dua.ai.ui.chat

/** The ViewModel appends "\nFiles: a.pdf, b.png" to a sent prompt; show those names as chips. */
internal fun splitFileNames(text: String): Pair<String, List<String>> {
    val at = text.lastIndexOf("\nFiles: ")
    if (at < 0) return text to emptyList()
    val names = text.substring(at + "\nFiles: ".length).split(", ").filter { it.isNotBlank() }
    return text.substring(0, at) to names
}
