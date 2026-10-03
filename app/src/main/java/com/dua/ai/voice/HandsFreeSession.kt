package com.dua.ai.voice

/** Foreground turn coordinator. Duplicate and late callbacks cannot start another turn. */
class HandsFreeSession(private val actions: Actions) {
    interface Actions {
        fun render(phase: String)
        fun listen()
        fun send(text: String, token: Int)
        fun speak(text: String)
        fun cancel()
        fun notice(text: String)
    }
    var phase = "idle"; private set
    var generation = 0; private set
    private fun set(value: String) { phase = value; actions.render(value) }
    fun start() { if (phase != "idle") return; generation++; set("listening"); actions.listen() }
    fun transcript(text: String) {
        if (phase != "listening") return
        val words = text.trim()
        if (words.isEmpty() || words.length > 8000) { stop("No speech detected or message too long. Start again."); return }
        set("thinking"); actions.send(words, generation)
    }
    fun reply(text: String, token: Int) { if (token != generation || phase != "thinking") return; set("speaking"); actions.speak(text) }
    fun spoken() { if (phase != "speaking") return; set("listening"); actions.listen() }
    fun stop(reason: String = "Stopped.") { generation++; set("idle"); actions.cancel(); actions.notice(reason) }
}
