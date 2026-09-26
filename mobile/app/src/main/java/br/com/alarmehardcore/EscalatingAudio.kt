package br.com.alarmehardcore

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** All MediaPlayer calls and callbacks live on one Looper, never the UI thread. */
class EscalatingAudio(context: Context) {
    private val app = context.applicationContext
    private val thread = HandlerThread("alarm-audio").apply { start() }
    private val handler = Handler(thread.looper)
    private data class Target(val phase: AlarmPhase, val source: String, val gain: Float)
    @Volatile private var target: Target? = null
    @Volatile private var closed = false
    private val queued = AtomicBoolean(false)
    private var player: MediaPlayer? = null
    private var phase: AlarmPhase? = null
    private var ready = false
    private var fallback = false
    private var retryAt = 0L
    private var preparingSince = 0L

    fun update(frame: PhaseFrame, source: String, gain: Float) {
        if (closed) return
        target = Target(frame.phase, source, gain)
        // Collapse pending updates if a document provider takes time opening custom audio.
        if (queued.compareAndSet(false, true)) handler.post {
            queued.set(false)
            if (!closed) render()
        }
    }
    private fun release() {
        player?.setOnPreparedListener(null)
        player?.setOnErrorListener(null)
        player?.release(); player = null; ready = false
    }
    private fun failed() {
        release(); fallback = true; retryAt = SystemClock.elapsedRealtime() + 1000
    }
    private fun render() {
        val desired = target ?: return
        if (phase != desired.phase) {
            release(); phase = desired.phase; fallback = false; retryAt = 0
        }
        if (player != null) {
            if (ready) runCatching { player?.setVolume(desired.gain, desired.gain) }.onFailure { failed() }
            else if (SystemClock.elapsedRealtime() - preparingSince >= 5000) failed()
            return
        }
        if (SystemClock.elapsedRealtime() < retryAt) return
        val source = when (desired.phase) {
            AlarmPhase.ZEN -> "builtin:zen"
            AlarmPhase.HARDCORE -> if (fallback) "builtin:alarm" else desired.source
            AlarmPhase.PUNISHMENT -> "builtin:siren"
        }
        try {
            val next = AlarmAudio.create(app, source)
            player = next; preparingSince = SystemClock.elapsedRealtime()
            next.setOnPreparedListener { mp ->
                val current = target
                if (!closed && mp === player && current?.phase == phase) {
                    runCatching {
                        mp.setVolume(current!!.gain, current.gain)
                        mp.start(); ready = true
                    }.onFailure { failed() }
                }
            }
            next.setOnErrorListener { mp, _, _ ->
                if (!closed && mp === player) failed()
                true
            }
            next.prepareAsync()
        } catch (_: Exception) { failed() }
    }
    fun close() {
        closed = true; target = null
        handler.post { release(); thread.quitSafely() }
    }
}
