package br.com.alarmehardcore

import org.junit.Assert.*
import org.junit.Test

class AlarmRulesTest {
    @Test fun disabledOldAlarmCannotConsumeNewLaterAlarm() {
        val saved = listOf(SavedAlarm(id = "old", enabled = false, nextAt = 0), SavedAlarm(id = "new", nextAt = 200))
        assertEquals(200L, AlarmPlan.next(saved))
        assertFalse(AlarmRules.acceptBroadcast(100, 200, "old", "new", 100))
        assertEquals(saved, AlarmPlan.afterFire(saved, 100))
        assertTrue(AlarmRules.acceptBroadcast(200, 200, "new", "new", 200))
        assertFalse(AlarmPlan.afterFire(saved, 200)[1].enabled)
    }
    @Test fun staleGenerationAtSameTimeAndLegacyBroadcastAreRejected() {
        assertFalse(AlarmRules.acceptBroadcast(200, 200, "old", "new", 200))
        assertFalse(AlarmRules.acceptBroadcast(200, 200, null, null, 200))
    }
    @Test fun updatedExtrasCannotAdvanceClockAndFireFutureAlarmEarly() {
        assertFalse(AlarmRules.acceptBroadcast(200, 200, "new", "new", 100))
        assertTrue(AlarmRules.acceptBroadcast(200, 200, "new", "new", 250))
    }
    @Test fun homeRadiusHasExplicitBoundaryAndMissingLocationFallsBack() {
        assertTrue(AlarmRules.atHome(149.99f))
        assertFalse(AlarmRules.atHome(150f))
        assertFalse(AlarmRules.atHome(300f))
        assertFalse(AlarmRules.atHome(null))
        assertFalse(AlarmRules.atHome(Float.NaN))
    }
    @Test fun wrongAnswerResetsProgressAndFiveConsecutiveAnswersAreRequired() {
        var count = 0
        repeat(4) { count = AlarmRules.streak(count, true) }
        assertEquals(4, count)
        count = AlarmRules.streak(count, false)
        assertEquals(0, count)
        repeat(5) { count = AlarmRules.streak(count, true) }
        assertEquals(5, count)
    }
}
