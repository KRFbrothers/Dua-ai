package com.dua.ai.files

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Records a voice note to app cache as AAC in an MPEG-4 container (.m4a, audio/mp4), a format every
 * Dua transcription provider accepts. Needs the RECORD_AUDIO runtime permission (already declared).
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    var startedAt = 0L
        private set
    val recording: Boolean get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Already recording." }
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, "voice-note-${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(64_000)
            next.setAudioSamplingRate(44_100)
            next.setMaxDuration(MAX_DURATION_MS)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
        } catch (error: Exception) { next.release(); file.delete(); throw IllegalStateException("Microphone is unavailable. Close other recording apps and retry.", error) }
        recorder = next; output = file; startedAt = System.currentTimeMillis()
        return file
    }

    /** Stops and returns the finished file, or null if the recording was too short to keep. */
    fun stop(): File? {
        val current = recorder ?: return null
        val file = output
        recorder = null; output = null
        val ok = runCatching { current.stop() }.isSuccess
        current.release()
        if (!ok || file == null || file.length() == 0L) { file?.delete(); return null }
        return file
    }

    fun cancel() { stop()?.delete() }

    companion object {
        const val DIR = "audio-recordings"
        const val MAX_DURATION_MS = 30 * 60 * 1000
        const val MIME = "audio/mp4"
    }
}
