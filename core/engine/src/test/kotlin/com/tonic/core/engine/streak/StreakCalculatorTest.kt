package com.tonic.core.engine.streak

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 8, 19)

    @Test
    fun `no practice history at all is a zero streak`() {
        assertEquals(0, StreakCalculator.currentStreak(emptySet(), today))
    }

    @Test
    fun `three consecutive days ending today count as a streak of three`() {
        val dates = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `not having practiced yet today does not break a streak that ended yesterday`() {
        val dates = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `a single missed day is absorbed by the default one-day grace budget`() {
        // Practiced today and the day before yesterday, but missed yesterday.
        val dates = setOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(3, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `two consecutive missed days exceed the default grace budget and stop the count there`() {
        // Practiced today; missed both of the two days before it; practiced further back.
        val dates = setOf(today, today.minusDays(3), today.minusDays(4))
        assertEquals(1, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `an old lapse far in the past does not reset an otherwise-current streak to zero`() {
        val dates = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(400))
        assertEquals(3, StreakCalculator.currentStreak(dates, today))
    }

    @Test
    fun `a larger grace budget forgives more consecutive missed days`() {
        val dates = setOf(today, today.minusDays(4), today.minusDays(5))
        assertEquals(1, StreakCalculator.currentStreak(dates, today, graceDays = 2), "a 3-day gap exceeds 2 days grace")
        assertEquals(3, StreakCalculator.currentStreak(dates, today, graceDays = 3))
    }

    @Test
    fun `zero grace days means any gap breaks the streak immediately`() {
        val dates = setOf(today, today.minusDays(2))
        assertEquals(1, StreakCalculator.currentStreak(dates, today, graceDays = 0))
    }
}
