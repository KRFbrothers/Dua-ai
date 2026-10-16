package com.dua.ai.voice

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

/** Hysteresis/onset gate for echo-cancelled 16 kHz PCM. Rejects clicks and never retains audio. */
internal class BargeInDetector {
    private var noise = .004f
    private var onset = 0
    private var elapsed = 0
    fun accept(samples: ShortArray, count: Int): Boolean {
        if (count <= 0) return false
        var sum = 0.0
        for (i in 0 until count) { val value = samples[i].toDouble() / 32768.0; sum += value * value }
        val rms = sqrt(sum / count).toFloat()
        val ms = count * 1000 / 16000
        // Learn the residual room/echo floor before arming, then update only on quiet frames.
        elapsed += ms
        if (elapsed <= 200) { noise += (rms - noise) * .25f; return false }
        val threshold = maxOf(.018f, noise * 3f)
        if (rms >= threshold) onset += ms else { onset = (onset - ms * 2).coerceAtLeast(0); noise += (rms - noise) * .02f }
        return onset >= 120
    }
}

internal interface BargeInMonitor {
    /** False means this device cannot safely distinguish playback echo; keep tap-to-interrupt. */
    fun start(onSpeech: () -> Unit): Boolean
    fun stop()
}

/** Mic is open only during unmuted foreground voice replies. No network, files or extra audio focus. */
internal class NativeBargeInMonitor(context: Context) : BargeInMonitor {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    @Volatile private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @SuppressLint("MissingPermission") // Voice mode starts only after RECORD_AUDIO is granted; revocation is caught below.
    override fun start(onSpeech: () -> Unit): Boolean {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return false
        var echo: AcousticEchoCanceler? = null
        var noise: NoiseSuppressor? = null
        var input: AudioRecord? = null
        try {
            input = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 2560))
            if (input.state != AudioRecord.STATE_INITIALIZED) { input.release(); return false }
            if (AcousticEchoCanceler.isAvailable()) echo = AcousticEchoCanceler.create(input.audioSessionId)?.apply { enabled = true }
            if (echo?.enabled != true) { echo?.release(); input.release(); return false }
            if (NoiseSuppressor.isAvailable()) noise = runCatching { NoiseSuppressor.create(input.audioSessionId)?.apply { enabled = true } }.getOrNull()
            input.startRecording()
            check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        } catch (_: Exception) { runCatching { input?.release() }; runCatching { echo?.release() }; runCatching { noise?.release() }; return false }
        val source = input
        val echoEffect = echo
        val noiseEffect = noise
        val ticket = ++generation
        recorder = source
        worker = Thread({
            val samples = ShortArray(640) // reused 40 ms buffer
            val detector = BargeInDetector()
            var detected = false
            try {
                while (generation == ticket) {
                    val read = source.read(samples, 0, samples.size)
                    if (read <= 0) break
                    if (detector.accept(samples, read)) { detected = true; break }
                }
            } catch (_: Exception) {
                // Permission revocation/device failures must never crash the process; tap remains available.
            } finally {
                samples.fill(0)
                runCatching { source.stop() }; runCatching { source.release() }
                runCatching { echoEffect?.release() }; runCatching { noiseEffect?.release() }
                if (recorder === source) recorder = null
                // Release AudioRecord before asking SpeechRecognizer to acquire the microphone.
                if (detected) main.post { if (generation == ticket) { generation++; onSpeech() } }
            }
        }, "dua-barge-in").apply { start() }
        return true
    }

    override fun stop() {
        generation++
        runCatching { recorder?.stop() } // unblock read before joining
        worker?.let { if (it !== Thread.currentThread()) runCatching { it.join(200) } }
        worker = null
    }

}
