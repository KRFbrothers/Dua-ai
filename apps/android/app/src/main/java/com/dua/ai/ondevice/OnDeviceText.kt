package com.dua.ai.ondevice

/**
 * Prompts and output clean-up for the small on-device model. Pure functions, so they are unit-tested without a phone.
 * Model output is never trusted as-is: it is trimmed to one line, stripped of quotes and labels, and length-capped.
 */
object OnDeviceText {
    const val TITLE_INPUT = 1500
    const val TITLE_MAX = 60
    const val POLISH_INPUT = 1000
    const val POLISH_MAX = 600

    fun titlePrompt(user: String, reply: String): String =
        "Write a short title of 2 to 6 words for this chat. Use the language of the user's message. " +
            "Reply with the title only: no quotes, no emoji, no full stop.\n\n" +
            "User: ${user.trim().take(TITLE_INPUT)}\nAssistant: ${reply.trim().take(TITLE_INPUT)}\n\nTitle:"

    fun polishPrompt(draft: String): String =
        "Rewrite this image idea as one clear, vivid prompt for an image generator. Keep the same subject, meaning and language. " +
            "Add helpful detail about style, lighting and composition. Keep it under 60 words. Reply with the prompt only.\n\n" +
            "Idea: ${draft.trim().take(POLISH_INPUT)}\n\nPrompt:"

    /** One clean line without wrapping quotes, markdown or a leading "Title:" style label; null when nothing usable is left. */
    private fun clean(raw: String?, labels: List<String>): String? {
        var line = raw?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: return null
        line = line.trim('*', '#', '`', '_', ' ')
        labels.forEach { label -> if (line.startsWith(label, ignoreCase = true)) line = line.substring(label.length).trimStart(':', ' ', '-') }
        line = line.trim().trim('"', '\'', '“', '”', '‘', '’', '«', '»', '`', '*').trim()
        line = line.filter { it == ' ' || !it.isISOControl() }.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        return line.ifBlank { null }
    }

    fun cleanTitle(raw: String?): String? {
        var title = clean(raw, listOf("title", "chat title")) ?: return null
        title = title.trimEnd('.', '!', ':', ';', ',', '。')
        if (title.length > TITLE_MAX) title = title.take(TITLE_MAX).substringBeforeLast(' ').ifBlank { title.take(TITLE_MAX) }
        title = title.trim()
        return title.takeIf { it.length >= 2 && com.dua.ai.history.ChatTitles.valid(it) }
    }

    /** The polished prompt, or null when the model returned nothing useful or just echoed the draft. */
    fun cleanPolish(raw: String?, draft: String): String? {
        val text = raw?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(" ")
        var prompt = clean(text, listOf("prompt", "image prompt", "polished prompt")) ?: return null
        if (prompt.length > POLISH_MAX) prompt = prompt.take(POLISH_MAX).substringBeforeLast(' ')
        prompt = prompt.trim()
        return prompt.takeIf { it.length >= 3 && !it.equals(draft.trim(), ignoreCase = true) }
    }
}
