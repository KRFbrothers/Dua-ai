package com.dua.ai.voice.live

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** How a live session ended: its id (to settle minutes on the server) and the captions to save. */
class LiveEnd(val sessionId: String, val captions: List<LiveCaption>, val reason: String, val failed: Boolean)

/**
 * Runs at most one live voice session for the whole app, inside [LiveVoiceService] so it keeps going with the
 * screen off. Everything touching [LiveSession] runs on the main thread. The xAI client secret stays in memory
 * only and is sent as a Bearer header to xAI's realtime endpoint.
 */
object LiveVoiceController {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client by lazy { OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build() }
    private val mutable = MutableStateFlow(LiveState())
    val state = mutable.asStateFlow()

    private var session: LiveSession? = null
    private var grant: LiveGrant? = null
    private var socket: WebSocket? = null
    private var audio: LiveAudio? = null
    private var onEnded: (suspend (LiveEnd) -> Unit)? = null
    private var connectedAt = 0L
    private var appContext: Context? = null
    private val ticker = object : Runnable {
        override fun run() {
            val current = session ?: return
            if (connectedAt > 0) apply(current.tick(((SystemClock.elapsedRealtime() - connectedAt) / 1000).toInt()))
            if (current.state.active) main.postDelayed(this, 1000)
        }
    }

    val active: Boolean get() = session?.state?.active == true

    /** Starts the foreground service, which then connects. Main thread only. */
    fun begin(context: Context, liveGrant: LiveGrant, voice: String, ended: suspend (LiveEnd) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (active) return
        val next = LiveSession(liveGrant.maxSeconds, voice)
        session = next; grant = liveGrant; onEnded = ended; connectedAt = 0L
        appContext = context.applicationContext
        apply(next.connecting())
        ContextCompat.startForegroundService(context, Intent(context, LiveVoiceService::class.java))
    }

    /** Called by the service once it is in the foreground. */
    internal fun connect(context: Context) {
        val current = session ?: return
        val liveGrant = grant ?: return
        if (socket != null || !current.state.active) return
        audio = LiveAudio(context, onMicrophone = { pcm, length -> main.post { session?.let { apply(it.microphone(pcm, length)) } } },
            onPlaybackLevel = { level -> main.post { session?.let { it.outputLevel(level); publish() } } },
            onDrained = { session?.let { it.playbackDrained(); publish() } },
            onFocusLost = { session?.let { apply(it.end("Another app took the audio. Session ended.")) } })
        val request = Request.Builder().url(liveGrant.url.replaceFirst("wss://", "https://")).header("Authorization", "Bearer " + liveGrant.token).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post {
                    if (webSocket !== socket) return@post
                    val live = session ?: return@post
                    apply(live.opened(RealtimeProtocol.sessionUpdate(liveGrant.session)))
                    if (audio?.start() != true) { apply(live.failed("The microphone could not start. Check that no other app is recording.")); return@post }
                    connectedAt = SystemClock.elapsedRealtime()
                    main.removeCallbacks(ticker); main.postDelayed(ticker, 1000)
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = RealtimeProtocol.decode(text)
                main.post { if (webSocket === socket) session?.let { apply(it.server(event)) } }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                main.post { if (webSocket === socket) session?.let { apply(it.end(if (code == 1000) "Session ended." else "Live voice closed the connection ($code).")) } }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val status = response?.code
                main.post {
                    if (webSocket !== socket) return@post
                    session?.let { apply(it.failed(when (status) {
                        401, 403 -> "xAI did not accept the live voice token. Try again."
                        429 -> "xAI live voice is busy (too many sessions). Try again shortly."
                        null -> "Connection lost. Check your internet and start again."
                        else -> "Live voice failed to connect ($status)."
                    })) }
                }
            }
        })
    }

    fun interrupt() { session?.let { apply(it.interrupt()) } }
    fun setMuted(muted: Boolean) { session?.let { apply(it.setMuted(muted)) } }
    fun end(reason: String = "Session ended.") { session?.let { apply(it.end(reason)) } }

    private fun publish() { session?.let { mutable.value = it.state } }

    private fun apply(effects: List<LiveEffect>) {
        for (effect in effects) when (effect) {
            is LiveEffect.Send -> socket?.send(effect.message)
            is LiveEffect.Play -> audio?.play(effect.pcm)
            LiveEffect.FlushPlayback -> audio?.flush()
            is LiveEffect.Close -> teardown(effect.reason)
        }
        publish()
    }

    private fun teardown(reason: String) {
        val current = session ?: return
        val liveGrant = grant
        main.removeCallbacks(ticker)
        socket?.let { runCatching { it.close(1000, "done") } }; socket = null
        audio?.release(); audio = null
        appContext?.let { it.stopService(Intent(it, LiveVoiceService::class.java)) }
        mutable.value = current.state
        val callback = onEnded
        onEnded = null; grant = null
        if (liveGrant != null && callback != null) {
            val end = LiveEnd(liveGrant.sessionId, current.transcript(), reason, current.state.phase == LivePhase.FAILED)
            scope.launch { runCatching { callback(end) } }
        }
    }

    /** Clears an ended session from the screen. */
    fun reset() { if (!active) { session = null; mutable.value = LiveState() } }
}
