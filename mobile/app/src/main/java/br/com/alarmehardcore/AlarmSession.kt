package br.com.alarmehardcore

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.time.LocalDate

object AlarmSession {
    private fun boot(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun begin(context: Context, eligible: Boolean) {
        AlarmState(context).prefs.edit().putLong("activeSince", System.currentTimeMillis())
            .putLong("sessionElapsedStart", SystemClock.elapsedRealtime()).putInt("sessionBoot", boot(context))
            .putLong("phaseFloor", 0).remove("debugAdvance").putBoolean("habitEligible", eligible)
            .putLong("habitDay", LocalDate.now().toEpochDay()).putBoolean("habitFailed", false).commit()
    }
    fun elapsed(context: Context): Long {
        val state = AlarmState(context)
        if (state.activeSince == 0L) return 0
        val p = state.prefs
        val currentBoot = boot(context)
        return AlarmPhases.elapsed(state.activeSince, p.getLong("sessionElapsedStart", -1),
            currentBoot >= 0 && p.getInt("sessionBoot", -2) == currentBoot,
            System.currentTimeMillis(), SystemClock.elapsedRealtime(), 0).let {
                maxOf(it + p.getLong("debugAdvance", 0), p.getLong("phaseFloor", 0))
            }
    }
}

class HabitStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = AlarmState(app).prefs
    fun progress() = HabitProgress(prefs.getInt("habitCount", 0), prefs.getLong("habitLastDay", Long.MIN_VALUE),
        prefs.getLong("habitFailedDay", Long.MIN_VALUE), prefs.getInt("habitBest", 0))
    fun reset() {
        check(prefs.edit().putInt("habitCount", 0).remove("habitLastDay").remove("habitFailedDay").commit())
        StreakSync.send(app)
    }
    private fun write(value: HabitProgress, edit: android.content.SharedPreferences.Editor) = edit
        .putInt("habitCount", value.count).putLong("habitLastDay", value.lastDay)
        .putLong("habitFailedDay", value.failedDay).putInt("habitBest", value.best)

    /** Called at phase changes; no disk write on ordinary animation ticks. */
    fun observe(elapsed: Long) {
        val floor = when (AlarmPhases.frame(elapsed).phase) {
            AlarmPhase.ZEN -> 0L
            AlarmPhase.HARDCORE -> AlarmPhases.ZEN_END
            AlarmPhase.PUNISHMENT -> AlarmPhases.PUNISHMENT_START
        }
        if (floor <= prefs.getLong("phaseFloor", 0)) return
        val edit = prefs.edit().putLong("phaseFloor", floor)
        if (prefs.getBoolean("habitEligible", false) && !prefs.getBoolean("habitFailed", false)) {
            write(progress().fail(prefs.getLong("habitDay", LocalDate.now().toEpochDay())), edit)
            edit.putBoolean("habitFailed", true)
        }
        edit.commit()
        if (prefs.getBoolean("habitEligible", false)) StreakSync.send(app)
    }

    /** Award/reset and closing the session are a single transaction to prevent duplicate credit. */
    fun finish(pcPin: Boolean) {
        if (prefs.getLong("activeSince", 0) == 0L) return
        val elapsed = AlarmSession.elapsed(app)
        val day = prefs.getLong("habitDay", LocalDate.now().toEpochDay())
        val edit = prefs.edit().putLong("activeSince", 0).remove("retryAfter").remove("recoveryAt")
        if (prefs.getBoolean("habitEligible", false)) {
            write(progress().resolve(day, elapsed, pcPin, eligible = true), edit)
        }
        check(edit.commit()) { "Não foi possível salvar a ofensiva." }
        StreakSync.send(app)
    }
}
