package br.com.alarmehardcore

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.*
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button

class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playback: EscalatingAudio
    private lateinit var habits: HabitStore
    private var lastVolume = -1
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlay: Button? = null
    private lateinit var state: AlarmState
    private lateinit var audio: AudioManager
    private var focus: AudioFocusRequest? = null
    private var started = false
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    private val tick = object : Runnable {
        override fun run() {
            if (state.activeSince == 0L) { stopSelf(); return }
            val elapsed = AlarmSession.elapsed(this@AlarmService)
            val frame = AlarmPhases.frame(elapsed)
            habits.observe(elapsed)
            val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
            val step = kotlin.math.ceil(frame.gain * maximum).toInt().coerceIn(1, maximum)
            // Only phase 3 locks the slider; phases 1/2 allow user adjustments between ramp steps.
            if (step != lastVolume || frame.phase == AlarmPhase.PUNISHMENT) {
                runCatching {
                    if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != step)
                        audio.setStreamVolume(AudioManager.STREAM_ALARM, step, 0)
                }
                lastVolume = step
            }
            val smoothGain = (frame.gain * maximum / step).coerceIn(0f, 1f)
            playback.update(frame, state.prefs.getString("activeSound", "builtin:alarm")!!, smoothGain)
            if (frame.phase == AlarmPhase.PUNISHMENT && overlay == null) addOverlay()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate() {
        super.onCreate()
        state = AlarmState(this)
        audio = getSystemService(AudioManager::class.java)
        playback = EscalatingAudio(this)
        habits = HabitStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Despertador", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarme ativo e entrada do PIN"
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(this, 3, Intent(this, AlarmActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hora de levantar")
            .setContentText("Abra o desafio para encerrar o alarme.")
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true).setContentIntent(open).setFullScreenIntent(open, true).build()
        startForeground(1, notification)
        if (state.activeSince == 0L) { stopSelf(); return START_NOT_STICKY }
        if (!started) {
            started = true
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "alarmehardcore:active").apply { acquire() }
            // Os valores persistem se o sistema encerrar e recriar o processo.
            if (!state.prefs.contains("oldAlarm")) {
                state.prefs.edit().putInt("oldAlarm", audio.getStreamVolume(AudioManager.STREAM_ALARM))
                    .commit()
            }
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { }.build()
            runCatching { audio.requestAudioFocus(focus!!) }
            handler.post(tick)
        }
        return START_STICKY
    }

    private fun addOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val button = Button(this).apply {
            text = "Fase 3 • Abra o desafio para encerrar"
            setOnClickListener {
                startActivity(Intent(this@AlarmService, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 48; screenBrightness = 1f }
        runCatching { getSystemService(WindowManager::class.java).addView(button, params); overlay = button }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        playback.close()
        focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        restoreVolume(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val CHANNEL = "alarm"

        fun dismiss(context: Context, code: String): String {
            val state = AlarmState(context)
            if (state.activeSince == 0L) return ""
            if (EscapeRoute.mode(context) != EscapeRoute.HOME) return "invalid"
            val now = System.currentTimeMillis()
            if (now < state.prefs.getLong("retryAfter", 0)) return "wait"
            val valid = runCatching { TimePin.validate(TimePin.decode(state.secret), code, now / 1000) }.getOrDefault(false)
            if (!valid) { state.prefs.edit().putLong("retryAfter", now + 5000).commit(); return "invalid" }
            finishAlarm(context, pcPin = true)
            return ""
        }

        internal fun finishAlarm(context: Context, pcPin: Boolean = false) {
            HabitStore(context).finish(pcPin)
            runCatching { AlarmScheduler.reschedule(context) }
            context.stopService(Intent(context, AlarmService::class.java))
            restoreVolume(context)
        }

        private fun restoreVolume(context: Context) {
            val prefs = AlarmState(context).prefs
            val manager = context.getSystemService(AudioManager::class.java)
            if (prefs.contains("oldAlarm")) {
                runCatching { manager.setStreamVolume(AudioManager.STREAM_ALARM, prefs.getInt("oldAlarm", 1), 0) }
                if (prefs.contains("oldMusic")) runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, prefs.getInt("oldMusic", 1), 0) }
                prefs.edit().remove("oldAlarm").remove("oldMusic").commit()
            }
        }
    }
}
