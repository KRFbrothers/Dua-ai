package com.dua.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.IOException

/**
 * Explicit generated-speech playback from memory. No file, URL, cache or autoplay is used.
 * play transfers ownership of bytes until stop/completion; every public operation is on Main.
 */
class NativeGeneratedAudioOutput(context: Context) : GeneratedAudioOutput {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var closed = false
    private var active: Playback? = null

    private class Playback(
        val player: MediaPlayer,
        val source: MemoryAudioDataSource,
        val finished: (Boolean) -> Unit
    ) {
        var focus: AudioFocusRequest? = null
        var focusHeld = false
        var preparationTimeout: Runnable? = null
        var playbackTimeout: Runnable? = null
    }

    override fun play(bytes: ByteArray, onFinished: (Boolean) -> Unit) {
        requireMain()
        stop()
        if (closed || bytes.isEmpty() || bytes.size > MAX_BYTES) {
            bytes.fill(0)
            onFinished(false)
            return
        }
        val source = MemoryAudioDataSource(bytes)
        val player = try { MediaPlayer() } catch (_: Exception) {
            source.close()
            onFinished(false)
            return
        }
        val playback = Playback(player, source, onFinished)
        active = playback
        try {
            player.setAudioAttributes(attributes)
            player.setOnPreparedListener { prepared -> onMain {
                if (active !== playback || closed || prepared !== playback.player) return@onMain
                playback.preparationTimeout?.let(handler::removeCallbacks)
                playback.preparationTimeout = null
                try {
                    if (prepared.duration > MAX_PLAYBACK_MS || !requestFocus(playback)) {
                        finish(playback, false)
                    } else if (active === playback && !closed) prepared.start()
                } catch (_: Exception) { finish(playback, false) }
            } }
            player.setOnCompletionListener { completed -> onMain {
                if (completed === playback.player) finish(playback, true)
            } }
            player.setOnErrorListener { _, _, _ ->
                onMain { finish(playback, false) }
                true
            }
            playback.preparationTimeout = Runnable { finish(playback, false) }.also {
                handler.postDelayed(it, MAX_PREPARATION_MS)
            }
            // Includes preparation time: a stuck prepare/start cannot extend the audio lifetime.
            playback.playbackTimeout = Runnable { finish(playback, false) }.also {
                handler.postDelayed(it, MAX_PLAYBACK_MS)
            }
            player.setDataSource(source)
            player.prepareAsync()
        } catch (_: Exception) { finish(playback, false) }
    }

    private fun requestFocus(playback: Playback): Boolean {
        val manager = audioManager ?: return false
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change <= 0) {
                    onMain { finish(playback, false) }
                }
            }, handler)
            .build()
        playback.focus = focus
        val granted = manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (active !== playback || closed) {
            if (granted) runCatching { manager.abandonAudioFocusRequest(focus) }
            return false
        }
        playback.focusHeld = granted
        return playback.focusHeld
    }

    private fun finish(playback: Playback, completed: Boolean) {
        requireMain()
        if (active !== playback) return
        val released = release(playback)
        if (!closed) playback.finished(completed && released)
    }

    override fun stop() {
        requireMain()
        active?.let(::release)
    }

    fun close() {
        requireMain()
        closed = true
        stop()
    }

    private fun release(playback: Playback): Boolean {
        // Detach the active identity first; queued player/focus callbacks cannot revive stopped audio.
        active = null
        playback.preparationTimeout?.let(handler::removeCallbacks)
        playback.playbackTimeout?.let(handler::removeCallbacks)
        playback.preparationTimeout = null
        playback.playbackTimeout = null
        runCatching { playback.player.setOnPreparedListener(null) }
        runCatching { playback.player.setOnCompletionListener(null) }
        runCatching { playback.player.setOnErrorListener(null) }
        // release is valid in every MediaPlayer state, including asynchronous preparation.
        // The source is wiped only after release returns, so native readers never see wiped live data.
        val released = runCatching { playback.player.release() }.isSuccess
        playback.source.close()
        playback.focusHeld = false
        playback.focus?.let { focus -> runCatching { audioManager?.abandonAudioFocusRequest(focus) } }
        playback.focus = null
        return released
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }

    private fun requireMain() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Generated audio playback must run on the main thread." }
    }

    private class MemoryAudioDataSource(bytes: ByteArray) : MediaDataSource() {
        private var data: ByteArray? = bytes
        private val length = bytes.size.toLong()

        @Synchronized override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            val bytes = data ?: throw IOException("Generated audio source is closed.")
            if (position < 0 || offset < 0 || size < 0 || offset > buffer.size - size) {
                throw IOException("Invalid generated audio read.")
            }
            if (size == 0) return 0
            if (position >= length) return -1
            val start = position.toInt()
            val count = minOf(size, bytes.size - start)
            bytes.copyInto(buffer, destinationOffset = offset, startIndex = start, endIndex = start + count)
            return count
        }

        @Synchronized override fun getSize(): Long {
            if (data == null) throw IOException("Generated audio source is closed.")
            return length
        }

        @Synchronized override fun close() {
            data?.fill(0)
            data = null
        }
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MAX_PREPARATION_MS = 45_000L
        const val MAX_PLAYBACK_MS = 120_000L
    }
}
