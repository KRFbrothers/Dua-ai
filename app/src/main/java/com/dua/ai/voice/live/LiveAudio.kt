package com.dua.ai.voice.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Full-duplex audio for live voice: 24 kHz mono PCM16 capture from the voice-communication source (with the
 * platform echo canceller, so Dua does not hear itself on the speaker) and streaming playback that can be
 * flushed instantly for barge-in. Routes to a Bluetooth headset when one is connected and restores the
 * previous audio mode on [release].
 */
class LiveAudio(context: Context, private val onMicrophone: (ByteArray, Int) -> Unit, private val onPlaybackLevel: (Float) -> Unit,
    private val onDrained: () -> Unit, private val onFocusLost: () -> Unit) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val running = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private val queue = LinkedBlockingQueue<Pair<Long, ByteArray>>()
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private var captureThread: Thread? = null
    private var playThread: Thread? = null
    private var focus: AudioFocusRequest? = null
    private var previousMode = AudioManager.MODE_NORMAL
    private var scoStarted = false
    @Volatile private var pending = 0

    /** Starts capture and playback; false if the microphone or speaker could not be opened. */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked before the session starts.
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return true
        previousMode = audioManager.mode
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change -> if (change == AudioManager.AUDIOFOCUS_LOSS) onFocusLost() }, main).build()
        audioManager.requestAudioFocus(focus!!)
        runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION }
        routeToHeadset()
        val rate = RealtimeProtocol.SAMPLE_RATE
        val inMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val outMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (inMin <= 0 || outMin <= 0) { release(); return false }
        try {
            val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(inMin, FRAME_BYTES * 4))
            if (recorder.state != AudioRecord.STATE_INITIALIZED) { recorder.release(); release(); return false }
            record = recorder
            if (AcousticEchoCanceler.isAvailable()) echo = runCatching { AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
            if (NoiseSuppressor.isAvailable()) noise = runCatching { NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
            val player = AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(outMin, FRAME_BYTES * 8)).build()
            track = player
            player.play()
            recorder.startRecording()
        } catch (_: Exception) { release(); return false }
        captureThread = Thread({ captureLoop() }, "dua-live-mic").apply { priority = Thread.MAX_PRIORITY; start() }
        playThread = Thread({ playLoop() }, "dua-live-speaker").apply { priority = Thread.MAX_PRIORITY; start() }
        return true
    }

    fun play(pcm: ByteArray) { if (running.get()) { pending++; queue.offer(generation.get() to pcm) } }

    /** Barge-in: drop everything queued or buffered for playback right away. */
    fun flush() {
        generation.incrementAndGet()
        queue.clear(); pending = 0
        track?.let { runCatching { it.pause(); it.flush(); it.play() } }
    }

    fun release() {
        running.set(false)
        generation.incrementAndGet()
        queue.clear()
        captureThread?.interrupt(); playThread?.interrupt()
        runCatching { captureThread?.join(500) }; runCatching { playThread?.join(500) }
        captureThread = null; playThread = null
        record?.let { runCatching { it.stop() }; runCatching { it.release() } }; record = null
        echo?.let { runCatching { it.release() } }; echo = null
        noise?.let { runCatching { it.release() } }; noise = null
        track?.let { runCatching { it.pause(); it.flush() }; runCatching { it.release() } }; track = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { audioManager.clearCommunicationDevice() }
        else if (scoStarted) { @Suppress("DEPRECATION") runCatching { audioManager.stopBluetoothSco(); audioManager.isBluetoothScoOn = false } }
        scoStarted = false
        runCatching { audioManager.mode = previousMode }
        focus?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }; focus = null
    }

    private fun routeToHeadset() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val headset = runCatching { audioManager.availableCommunicationDevices }.getOrDefault(emptyList()).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (headset != null) runCatching { audioManager.setCommunicationDevice(headset) }
            else runCatching { audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let(audioManager::setCommunicationDevice) }
        } else {
            val bluetooth = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            @Suppress("DEPRECATION")
            if (bluetooth && audioManager.isBluetoothScoAvailableOffCall) runCatching { audioManager.startBluetoothSco(); audioManager.isBluetoothScoOn = true; scoStarted = true }
            else runCatching { audioManager.isSpeakerphoneOn = !audioManager.isWiredHeadsetOn }
        }
    }

    private fun captureLoop() {
        val buffer = ByteArray(FRAME_BYTES)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            val read = record?.read(buffer, 0, buffer.size) ?: break
            if (read > 0) onMicrophone(buffer.copyOf(read), read)
            else if (read < 0) break
        }
    }

    private fun playLoop() {
        var idleSince = 0L
        while (running.get() && !Thread.currentThread().isInterrupted) {
            val item = try { queue.poll(100, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { break }
            if (item == null) {
                if (pending == 0 && idleSince == 0L) idleSince = System.nanoTime()
                // A short quiet gap after the last chunk means the reply finished playing.
                if (idleSince > 0L && System.nanoTime() - idleSince > 350_000_000L) { idleSince = -1L; main.post(onDrained) }
                continue
            }
            idleSince = 0L
            pending = (pending - 1).coerceAtLeast(0)
            val (ticket, pcm) = item
            if (ticket != generation.get()) continue
            onPlaybackLevel(RealtimeProtocol.level(pcm))
            var offset = 0
            while (offset < pcm.size && ticket == generation.get() && running.get()) {
                val written = track?.write(pcm, offset, pcm.size - offset) ?: break
                if (written <= 0) break
                offset += written
            }
        }
    }

    companion object {
        /** 40 ms of 24 kHz mono PCM16. */
        const val FRAME_BYTES = RealtimeProtocol.SAMPLE_RATE / 25 * 2
    }
}
