package com.dua.ai.voice

interface SpeechOutput {
    fun speak(text: String, utteranceId: String): Boolean
    fun stop()
}

data class ReadAloudState(val available: Boolean = false, val activeId: String? = null, val status: String = "")

object SpeechChunks {
    fun split(text: String): List<String> {
        val points = text.trim().codePoints().toArray()
        val result = mutableListOf<String>()
        var start = 0
        while (start < points.size) {
            var end = minOf(start + 300, points.size)
            if (end < points.size) {
                val boundary = (end - 1 downTo maxOf(start + 150, end - 100)).firstOrNull {
                    Character.isWhitespace(points[it]) || points[it] in listOf(46, 33, 63, 2404)
                }
                if (boundary != null) end = boundary + 1
            }
            String(points, start, end - start).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            start = end
        }
        return result
    }
}

/** Small playback state machine; callback IDs prevent stopped/replaced speech from resuming. */
class ReadAloudSession(private val output: SpeechOutput, private val changed: (ReadAloudState) -> Unit) {
    var state = ReadAloudState()
        private set
    private var generation = 0
    private var queue = emptyList<String>()
    private var index = 0
    private var utterance: String? = null

    private fun publish(next: ReadAloudState) { state = next; changed(next) }
    fun availability(available: Boolean, status: String = "") {
        stop()
        publish(ReadAloudState(available = available, status = status))
    }
    fun toggle(id: String, text: String) {
        if (id == state.activeId) { stop(); return }
        if (!state.available) { publish(state.copy(status = "Read aloud unavailable. Check your device speech settings.")); return }
        stop()
        queue = SpeechChunks.split(text)
        if (queue.isEmpty()) { publish(state.copy(status = "No reply text to read.")); return }
        index = 0
        publish(state.copy(activeId = id, status = "Reading reply…"))
        next()
    }
    private fun next() {
        if (index >= queue.size) { utterance = null; output.stop(); publish(state.copy(activeId = null, status = "")); return }
        val id = "${generation}-${index}"
        utterance = id
        if (!output.speak(queue[index++], id)) failed(id)
    }
    fun completed(id: String) { if (id == utterance && state.activeId != null) next() }
    fun failed(id: String) {
        if (id != utterance || state.activeId == null) return
        stop("Read aloud failed. Tap the reply speaker to retry.")
    }
    fun stop(reason: String = "") {
        generation++
        utterance = null
        queue = emptyList()
        output.stop()
        if (state.activeId != null) publish(state.copy(activeId = null, status = reason))
    }
}
