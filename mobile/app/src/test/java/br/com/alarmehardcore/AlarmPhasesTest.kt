package br.com.alarmehardcore

import org.junit.Assert.*
import org.junit.Test

class AlarmPhasesTest {
    @Test fun phaseBoundariesAreExactIncludingDelayedTicks() {
        assertEquals(AlarmPhase.ZEN, AlarmPhases.frame(299_999).phase)
        assertEquals(AlarmPhase.HARDCORE, AlarmPhases.frame(300_000).phase)
        assertEquals(AlarmPhase.HARDCORE, AlarmPhases.frame(479_999).phase)
        assertEquals(AlarmPhase.PUNISHMENT, AlarmPhases.frame(480_000).phase)
        assertEquals(AlarmPhase.PUNISHMENT, AlarmPhases.frame(900_000).phase)
    }
    @Test fun gainAndBrightnessRiseContinuouslyAndStayBounded() {
        assertEquals(.02f, AlarmPhases.frame(0).gain, .0001f)
        assertEquals(0f, AlarmPhases.frame(0).brightness, .0001f)
        assertEquals(.51f, AlarmPhases.frame(150_000).gain, .0001f)
        assertEquals(.5f, AlarmPhases.frame(150_000).brightness, .0001f)
        assertEquals(1f, AlarmPhases.frame(300_000).gain, .0001f)
        assertEquals(1f, AlarmPhases.frame(800_000).brightness, .0001f)
        assertEquals(0f, AlarmPhases.frame(-100).brightness, .0001f)
    }
    @Test fun wallClockChangesCannotRestartOrAccelerateSameBootSession() {
        assertEquals(310_000L, AlarmPhases.elapsed(1_000_000, 10_000, true, 1, 320_000, 0))
        assertEquals(310_000L, AlarmPhases.elapsed(1_000_000, 10_000, true, 9_000_000, 320_000, 0))
    }
    @Test fun rebootUsesWallClockAndDoesNotReturnToAnEarlierPhase() {
        assertEquals(600_000L, AlarmPhases.elapsed(1_000_000, 10_000, false, 1_600_000, 1000, 300_000))
        assertEquals(480_000L, AlarmPhases.elapsed(1_000_000, 10_000, false, 1, 1000, 480_000))
    }
    @Test fun onlyPcPinBeforeDeadlineAwardsPointEvenIfTickHasNotRun() {
        assertEquals(1, HabitProgress().resolve(10, 299_999, true, true).count)
        assertEquals(0, HabitProgress().resolve(10, 300_000, true, true).count)
        assertEquals(0, HabitProgress().resolve(10, 1000, false, true).count)
    }
    @Test fun sameDayCannotAwardTwice() {
        val first = HabitProgress().win(10)
        assertEquals(first, first.win(10))
        assertEquals(2, first.win(11).count)
    }
    @Test fun missedDayBreaksStreakWithoutLosingBest() {
        val earned = HabitProgress().win(10).win(11)
        assertEquals(2, earned.visibleCount(12))
        assertEquals(0, earned.visibleCount(13))
        assertEquals(1, earned.win(13).count)
        assertEquals(2, earned.win(13).best)
    }
    @Test fun lateAlarmResetsAndPreventsReearningThatDay() {
        val earned = HabitProgress().win(10).win(11)
        val failed = earned.resolve(11, 300_000, false, true)
        assertEquals(0, failed.count)
        assertEquals(0, failed.win(11).count)
        assertEquals(1, failed.win(12).count)
        assertEquals(2, failed.best)
    }
    @Test fun testsNeverAwardOrResetAndFallbackDoesNotAward() {
        val earned = HabitProgress().win(10)
        assertEquals(earned, earned.resolve(11, 100, true, false))
        assertEquals(earned, earned.resolve(11, 900_000, false, false))
        assertEquals(earned, earned.resolve(11, 100, false, true))
        assertEquals(0, earned.resolve(11, 900_000, false, true).count)
    }
    @Test fun goalCanBeCompletedAndContinued() {
        var earned = HabitProgress()
        for (day in 1L..22L) earned = earned.win(day)
        assertEquals(22, earned.count)
        assertEquals(22, earned.best)
    }
    @Test fun clockMovingBackDoesNotAwardOlderDay() {
        val earned = HabitProgress().win(10)
        assertEquals(earned, earned.win(9))
    }
}
