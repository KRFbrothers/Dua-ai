package com.dua.ai.voice

import kotlinx.coroutines.flow.StateFlow

/** One user-started speech-to-text session: the phone's recognizer ([NativeVoiceInput]) or offline Vosk. */
interface DictationEngine {
    val state: StateFlow<VoiceInputState>
    /** Microphone/activity level 0..1 while listening. */
    val level: StateFlow<Float>
    var language: String
    fun permissionDenied()
    fun start()
    /** Stop listening and keep the words. */
    fun confirm()
    /** Stop listening and drop the session. */
    fun stop()
    fun close()
}
