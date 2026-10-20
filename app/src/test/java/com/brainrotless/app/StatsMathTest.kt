package com.brainrotless.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsMathTest {
    private val min = 60_000L
    private val today = LocalDate.of(2026, 10, 20)
    private fun day(back: Long) = today.minusDays(back)

    @Test fun windowMarksDaysBeforeTrackingAsMissing() {
        val history = mapOf(day(2) to DayUsage(30 * min), today to DayUsage(10 * min))
        val w = StatsMath.window(history, today, 5)
        assertEquals(listOf(null, null, DayUsage(30 * min), DayUsage(0), DayUsage(10 * min)), w)
    }

    @Test fun emptyHistoryHasNoDays() {
        assertEquals(List(7) { null }, StatsMath.window(emptyMap(), today, 7))
        assertNull(StatsMath.average(StatsMath.window(emptyMap(), today, 7)))
    }

    @Test fun averageLeavesOutTodayAndUntrackedDays() {
        val history = mapOf(day(3) to DayUsage(60 * min), day(2) to DayUsage(30 * min), today to DayUsage(500 * min))
        // días terminados con datos: 60, 30 y 0 (ayer sin uso) → 30 min
        assertEquals(30 * min, StatsMath.average(StatsMath.window(history, today, 7)))
    }

    @Test fun onlyTodayHasNoAverageYet() {
        assertNull(StatsMath.average(StatsMath.window(mapOf(today to DayUsage(5 * min)), today, 7)))
    }

    @Test fun bestDayIsTheLowestFinishedDay() {
        val history = mapOf(day(3) to DayUsage(60 * min), day(2) to DayUsage(20 * min), day(1) to DayUsage(45 * min), today to DayUsage(0))
        assertEquals(4 to 20 * min, StatsMath.best(StatsMath.window(history, today, 7)))
    }

    @Test fun withinCountsFinishedDaysUnderTheLimit() {
        val history = mapOf(day(3) to DayUsage(60 * min), day(2) to DayUsage(20 * min), day(1) to DayUsage(45 * min), today to DayUsage(0))
        assertEquals(2 to 3, StatsMath.within(StatsMath.window(history, today, 7), 60 * min))
    }

    @Test fun withinOnlyCountsSinceTheCommitmentStarted() {
        val history = mapOf(day(3) to DayUsage(90 * min), day(2) to DayUsage(20 * min), day(1) to DayUsage(70 * min), today to DayUsage(0))
        val w = StatsMath.window(history, today, 7)
        // compromiso desde hace 2 días: cuentan 20 (dentro) y 70 (afuera); el de 90 era de antes
        assertEquals(1 to 2, StatsMath.within(w, 60 * min, StatsMath.indexOf(day(2), today, 7)))
    }

    @Test fun indexOfIsZeroForDaysBeforeTheWindow() {
        assertEquals(6, StatsMath.indexOf(today, today, 7))
        assertEquals(4, StatsMath.indexOf(day(2), today, 7))
        assertEquals(0, StatsMath.indexOf(day(40), today, 7))
    }

    @Test fun percentsAlwaysAddUpTo100() {
        assertEquals(67 to 33, StatsMath.percents(2 * min, 1 * min))
        assertEquals(50 to 50, StatsMath.percents(min, min))
    }

    @Test fun streakCountsFinishedDaysUnderTheGoal() {
        val history = mapOf(
            day(6) to DayUsage(30 * min), day(5) to DayUsage(90 * min), // el de 90 corta
            day(4) to DayUsage(10 * min), day(3) to DayUsage(20 * min), // ayer y anteayer sin datos: 0, suman
            today to DayUsage(500 * min) // hoy no terminó: no cuenta
        )
        assertEquals(4 to 4, StatsMath.streaks(history, today, 60 * min, LocalDate.MIN))
    }

    @Test fun bestStreakSurvivesABadDay() {
        val history = mapOf(day(5) to DayUsage(10 * min), day(4) to DayUsage(10 * min), day(3) to DayUsage(10 * min),
            day(2) to DayUsage(10 * min), day(1) to DayUsage(70 * min))
        assertEquals(0 to 4, StatsMath.streaks(history, today, 60 * min, LocalDate.MIN))
    }

    @Test fun streakStartsWhenTheGoalStarts() {
        val history = mapOf(day(5) to DayUsage(10 * min), day(4) to DayUsage(10 * min), day(3) to DayUsage(10 * min))
        // compromiso desde hace 2 días: solo cuentan anteayer (0) y ayer (0)
        assertEquals(2 to 2, StatsMath.streaks(history, today, 60 * min, day(2)))
        assertEquals(0 to 0, StatsMath.streaks(emptyMap(), today, 60 * min, LocalDate.MIN))
    }

    @Test fun goalMarksShowEachFinishedDay() {
        val history = mapOf(day(3) to DayUsage(70 * min), day(2) to DayUsage(10 * min))
        assertEquals(listOf(null, false, true, true), StatsMath.goalMarks(history, today, 60 * min, LocalDate.MIN, 4))
    }

    @Test fun weekComparesWithTheWeekBeforeOnlyWhenComplete() {
        val full = (1..14L).associate { day(it) to DayUsage(if (it <= 7) 30 * min else 60 * min) } + (today to DayUsage(99 * min))
        assertEquals(StatsMath.Week(210 * min, 7, 420 * min), StatsMath.week(full, today))
        // la semana anterior a medias (Lúcido empezó hace 10 días): sin comparación
        val partial = (1..10L).associate { day(it) to DayUsage(30 * min) }
        assertEquals(StatsMath.Week(210 * min, 7, null), StatsMath.week(partial, today))
    }

    @Test fun weekNeedsAtLeastThreeDays() {
        assertNull(StatsMath.week(mapOf(day(2) to DayUsage(min), day(1) to DayUsage(min)), today))
        assertEquals(3, StatsMath.week(mapOf(day(3) to DayUsage(min)), today)!!.days) // hace 3 días, anteayer y ayer
    }

    @Test fun nextSundayAtEight() {
        val tuesday = LocalDate.of(2026, 10, 20).atTime(15, 0) // martes
        assertEquals(LocalDate.of(2026, 10, 25).atTime(20, 0), StatsMath.nextSunday(tuesday, 20))
        val sundayBefore = LocalDate.of(2026, 10, 25).atTime(19, 59)
        assertEquals(LocalDate.of(2026, 10, 25).atTime(20, 0), StatsMath.nextSunday(sundayBefore, 20))
        val sundayAfter = LocalDate.of(2026, 10, 25).atTime(20, 0)
        assertEquals(LocalDate.of(2026, 11, 1).atTime(20, 0), StatsMath.nextSunday(sundayAfter, 20))
    }

    @Test fun changeIsSignedPercent() {
        assertEquals(-75, StatsMath.change(120 * min, 30 * min))
        assertEquals(50, StatsMath.change(60 * min, 90 * min))
        assertEquals(0, StatsMath.change(60 * min, 60 * min))
    }

    @Test fun recoveredAddsTheDailyDifferenceUntilYesterday() {
        val history = mapOf(day(2) to DayUsage(60 * min), day(1) to DayUsage(90 * min), today to DayUsage(300 * min))
        // antes 2 h por día: 60 + 30 recuperados (hoy no cuenta)
        assertEquals(90 * min, StatsMath.recovered(history, today, 120 * min))
        assertNull(StatsMath.recovered(history, today, 30 * min)) // usa más que antes: nada que festejar
    }

    @Test fun splitSumsOnlySeparatedTime() {
        val w = listOf(null, DayUsage(50 * min), DayUsage(40 * min, ig = 30 * min, yt = 10 * min))
        assertEquals(30 * min to 10 * min, StatsMath.split(w))
    }

    @Test fun gridStepIsRoundAndBelowTheTop() {
        assertEquals(60 * min, StatsMath.gridStep(100 * min))
        assertEquals(15 * min, StatsMath.gridStep(10 * min))
        assertEquals(240 * min, StatsMath.gridStep(300 * min))
    }
}
