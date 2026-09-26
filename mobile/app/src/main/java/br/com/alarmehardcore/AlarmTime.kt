package br.com.alarmehardcore

import java.util.Calendar
import java.util.TimeZone

object AlarmTime {
    // Calendar.SUNDAY = 1. Um conjunto vazio significa disparo único.
    fun next(hour: Int, minute: Int, days: Set<Int>, now: Long = System.currentTimeMillis(),
             zone: TimeZone = TimeZone.getDefault()): Long {
        require(hour in 0..23 && minute in 0..59)
        require(days.all { it in 1..7 })
        for (offset in 0..7) {
            val candidate = Calendar.getInstance(zone).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis > now && (days.isEmpty() || candidate.get(Calendar.DAY_OF_WEEK) in days))
                return candidate.timeInMillis
        }
        error("Não foi possível encontrar o próximo horário")
    }

    fun daysLabel(days: Set<Int>): String = when {
        days.isEmpty() -> "Uma vez"
        days.size == 7 -> "Todos os dias"
        days == setOf(2, 3, 4, 5, 6) -> "Seg a sex"
        else -> listOf(2 to "Seg", 3 to "Ter", 4 to "Qua", 5 to "Qui", 6 to "Sex", 7 to "Sáb", 1 to "Dom")
            .filter { it.first in days }.joinToString(" · ") { it.second }
    }
}
