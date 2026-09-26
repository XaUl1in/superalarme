package br.com.alarmehardcore

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import java.util.UUID

object AlarmScheduler {
    private fun pending(context: Context, time: Long, token: String = "") = PendingIntent.getBroadcast(
        context, 1, Intent(context, AlarmReceiver::class.java).apply {
            if (token.isNotEmpty()) data = Uri.parse("alarmehardcore://schedule/$token")
            putExtra("time", time); putExtra("token", token)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun canSchedule(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun reschedule(context: Context) {
        val state = AlarmState(context)
        val store = AlarmStore(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val oldToken = state.prefs.getString("scheduleToken", "")!!
        val oldTime = state.scheduled
        // Invalidate queued broadcasts before cancelling the system alarm, including legacy versions.
        check(state.prefs.edit().remove("scheduleToken").putLong("scheduled", 0).commit())
        pending(context, oldTime, oldToken).also { manager.cancel(it); it.cancel() }
        pending(context, 0).also { manager.cancel(it); it.cancel() }
        if (!canSchedule(context) || state.secret.isEmpty()) {
            return
        }
        val time = AlarmPlan.next(store.all(), state.prefs.getLong("testAt", 0), state.prefs.getLong("recoveryAt", 0))
        if (time == null) return
        val trigger = maxOf(time, System.currentTimeMillis() + 1000)
        val show = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val token = UUID.randomUUID().toString()
        check(state.prefs.edit().putString("scheduleToken", token).putLong("scheduled", trigger).commit())
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), pending(context, trigger, token))
        } catch (error: Exception) {
            state.prefs.edit().remove("scheduleToken").putLong("scheduled", 0).commit()
            throw error
        }
    }

    fun test(context: Context) {
        AlarmState(context).prefs.edit().putLong("testAt", System.currentTimeMillis() + 10_000).commit()
        reschedule(context)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = AlarmState(context)
        val expected = intent.getLongExtra("time", 0)
        if (!AlarmRules.acceptBroadcast(expected, state.scheduled, intent.getStringExtra("token"),
                state.prefs.getString("scheduleToken", null), System.currentTimeMillis()) || state.secret.isEmpty()) return
        val store = AlarmStore(context)
        val now = System.currentTimeMillis()
        val all = store.all()
        val due = all.filter { it.enabled && it.nextAt in 1..now }
        val test = state.prefs.getLong("testAt", 0) in 1..now
        val recovery = state.prefs.getLong("recoveryAt", 0) in 1..now
        if (due.isEmpty() && !test && !recovery) { AlarmScheduler.reschedule(context); return }
        if (state.activeSince == 0L) {
            val first = due.minByOrNull { it.nextAt }
            state.prefs.edit().putString("activeSound", first?.sound ?: "builtin:alarm")
                .putString("activeLabel", first?.label ?: "Teste do alarme").commit()
            AlarmSession.begin(context, eligible = first != null)
            EscapeRoute.begin(context)
        }
        store.write(AlarmPlan.afterFire(all, now))
        val edit = state.prefs.edit()
        if (test) edit.remove("testAt")
        if (recovery) edit.remove("recoveryAt")
        edit.commit()
        // A failure arming the NEXT occurrence must not prevent the current sound from starting.
        runCatching { AlarmScheduler.reschedule(context) }
        context.startForegroundService(Intent(context, AlarmService::class.java))
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = AlarmState(context)
        val store = AlarmStore(context)
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            store.write(store.all().map {
                if (it.enabled) it.copy(nextAt = AlarmTime.next(it.hour, it.minute, it.days)) else it
            })
        }
        if (state.activeSince > 0) state.prefs.edit().putLong("recoveryAt", System.currentTimeMillis() + 5000).commit()
        // Rearma o alarme: não inicia mediaPlayback diretamente no boot.
        runCatching { AlarmScheduler.reschedule(context) }
    }
}
