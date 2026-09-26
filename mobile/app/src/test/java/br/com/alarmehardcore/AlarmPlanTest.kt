package br.com.alarmehardcore

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class AlarmPlanTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun instant(day: Int, hour: Int, minute: Int = 0) = Calendar.getInstance(utc).apply {
        clear(); set(2026, Calendar.SEPTEMBER, day, hour, minute)
    }.timeInMillis

    @Test fun singleAlarmRollsToTomorrowOnlyWhenTimeHasPassed() {
        assertEquals(instant(23, 8), AlarmTime.next(8, 0, emptySet(), instant(23, 7), utc))
        assertEquals(instant(24, 8), AlarmTime.next(8, 0, emptySet(), instant(23, 8), utc))
    }
    @Test fun weekdaysSkipWeekend() {
        assertEquals(instant(28, 8), AlarmTime.next(8, 0, setOf(2, 3, 4, 5, 6), instant(25, 9), utc))
    }
    @Test fun weeklyAlarmAdvancesAFullWeekAtExactTime() {
        assertEquals(instant(30, 8), AlarmTime.next(8, 0, setOf(Calendar.WEDNESDAY), instant(23, 8), utc))
    }
    @Test fun firedAlarmsRemainSavedAndFutureOnesRemainEnabled() {
        val a = SavedAlarm(id = "a", nextAt = 100)
        val b = SavedAlarm(id = "b", nextAt = 100)
        val c = SavedAlarm(id = "c", nextAt = 200)
        val result = AlarmPlan.afterFire(listOf(a, b, c), 100)
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertFalse(result[0].enabled); assertFalse(result[1].enabled)
        assertEquals(c, result[2])
    }
    @Test fun repeatingAlarmRemainsActiveAfterFiring() {
        val alarm = SavedAlarm(days = (1..7).toSet(), nextAt = instant(23, 8))
        val result = AlarmPlan.afterFire(listOf(alarm), instant(23, 8)).single()
        assertTrue(result.enabled)
        assertTrue(result.nextAt > instant(23, 8))
        assertEquals(alarm.id, result.id)
    }
    @Test fun testAlarmDoesNotEraseOrReplaceSavedAlarm() {
        val future = SavedAlarm(nextAt = 200)
        val disabled = SavedAlarm(enabled = false, nextAt = 50)
        val saved = listOf(future, disabled)
        assertEquals(100L, AlarmPlan.next(saved, testAt = 100))
        assertEquals(saved, AlarmPlan.afterFire(saved, 100))
        assertEquals(200L, AlarmPlan.next(saved))
        assertNull(AlarmPlan.next(listOf(disabled)))
    }
    @Test fun nextOccurrenceRetainsLocalHourAcrossDaylightSavingChange() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val before = Calendar.getInstance(zone).apply { clear(); set(2026, Calendar.MARCH, 7, 9, 0) }.timeInMillis
        val next = Calendar.getInstance(zone).apply { timeInMillis = AlarmTime.next(8, 0, emptySet(), before, zone) }
        assertEquals(8, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY))
    }
}
