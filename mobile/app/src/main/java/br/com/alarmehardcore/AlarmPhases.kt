package br.com.alarmehardcore

enum class AlarmPhase { ZEN, HARDCORE, PUNISHMENT }

data class PhaseFrame(val phase: AlarmPhase, val gain: Float, val brightness: Float)

object AlarmPhases {
    const val ZEN_END = 300_000L
    const val PUNISHMENT_START = 480_000L
    fun frame(elapsed: Long): PhaseFrame {
        val progress = (elapsed.coerceAtLeast(0).toFloat() / ZEN_END).coerceIn(0f, 1f)
        val phase = when {
            elapsed >= PUNISHMENT_START -> AlarmPhase.PUNISHMENT
            elapsed >= ZEN_END -> AlarmPhase.HARDCORE
            else -> AlarmPhase.ZEN
        }
        return PhaseFrame(phase, .02f + .98f * progress, progress)
    }
    fun elapsed(startWall: Long, startElapsed: Long, sameBoot: Boolean, nowWall: Long, nowElapsed: Long, floor: Long): Long =
        maxOf(0, floor, if (sameBoot && startElapsed >= 0) nowElapsed - startElapsed else nowWall - startWall)
}

data class HabitProgress(val count: Int = 0, val lastDay: Long = Long.MIN_VALUE,
    val failedDay: Long = Long.MIN_VALUE, val best: Int = 0) {
    fun visibleCount(today: Long) = if (lastDay < today - 1) 0 else count
    fun win(day: Long): HabitProgress {
        if (day <= lastDay || day <= failedDay) return this
        val next = if (lastDay == day - 1) count + 1 else 1
        return copy(count = next, lastDay = day, best = maxOf(best, next))
    }
    fun fail(day: Long) = copy(count = 0, failedDay = maxOf(failedDay, day))
    fun resolve(day: Long, elapsed: Long, pcPin: Boolean, eligible: Boolean) = when {
        !eligible -> this
        elapsed >= AlarmPhases.ZEN_END -> fail(day)
        pcPin -> win(day)
        else -> this
    }
}
