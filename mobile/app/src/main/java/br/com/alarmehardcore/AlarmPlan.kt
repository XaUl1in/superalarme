package br.com.alarmehardcore

object AlarmPlan {
    fun afterFire(alarms: List<SavedAlarm>, now: Long): List<SavedAlarm> = alarms.map { alarm ->
        if (!alarm.enabled || alarm.nextAt !in 1..now) alarm
        else if (alarm.days.isEmpty()) alarm.copy(enabled = false, nextAt = 0)
        else alarm.copy(nextAt = AlarmTime.next(alarm.hour, alarm.minute, alarm.days, now))
    }

    fun next(alarms: List<SavedAlarm>, testAt: Long = 0, recoveryAt: Long = 0): Long? =
        (alarms.filter { it.enabled }.map { it.nextAt } + listOf(testAt, recoveryAt))
            .filter { it > 0 }.minOrNull()
}
