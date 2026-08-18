package com.tonic.core.engine.fsrs

import com.tonic.core.model.state.FsrsGrade
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsSchedulerTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `rating value mapping matches FSRS convention - AGAIN is 1, EASY is 4`() {
        assertEquals(1, FsrsGrade.AGAIN.ratingValue)
        assertEquals(2, FsrsGrade.HARD.ratingValue)
        assertEquals(3, FsrsGrade.GOOD.ratingValue)
        assertEquals(4, FsrsGrade.EASY.ratingValue)
    }

    @Test
    fun `initial stability is higher for a better first grade`() {
        val again = FsrsScheduler.initial(FsrsGrade.AGAIN, t0)
        val hard = FsrsScheduler.initial(FsrsGrade.HARD, t0)
        val good = FsrsScheduler.initial(FsrsGrade.GOOD, t0)
        val easy = FsrsScheduler.initial(FsrsGrade.EASY, t0)
        assertTrue(again.stability < hard.stability)
        assertTrue(hard.stability < good.stability)
        assertTrue(good.stability < easy.stability)
    }

    @Test
    fun `a repeated AGAIN grade decreases stability and increases lapses`() {
        var state = FsrsScheduler.initial(FsrsGrade.GOOD, t0)
        val t1 = t0.plus(state.stability.toLong().coerceAtLeast(1), ChronoUnit.DAYS)
        val afterLapse = FsrsScheduler.review(state, FsrsGrade.AGAIN, t1)
        assertTrue(afterLapse.stability < state.stability, "a lapse should reduce stability")
        assertEquals(1, afterLapse.lapses)
        assertEquals(2, afterLapse.reps)
    }

    @Test
    fun `repeated GOOD reviews on schedule grow stability and the review interval`() {
        var state = FsrsScheduler.initial(FsrsGrade.GOOD, t0)
        var now = t0
        var previousInterval = FsrsScheduler.nextIntervalDays(state.stability)
        repeat(5) {
            now = now.plus(previousInterval.toLong(), ChronoUnit.DAYS)
            state = FsrsScheduler.review(state, FsrsGrade.GOOD, now)
            val nextInterval = FsrsScheduler.nextIntervalDays(state.stability)
            assertTrue(nextInterval >= previousInterval, "reviewing on schedule should not shrink the interval")
            previousInterval = nextInterval
        }
        assertTrue(state.reps == 6)
        assertTrue(state.lapses == 0)
    }

    @Test
    fun `retrievability is 1 at zero elapsed days and decays toward zero over time`() {
        val r0 = FsrsScheduler.retrievability(0, stability = 10.0)
        val r30 = FsrsScheduler.retrievability(30, stability = 10.0)
        val r365 = FsrsScheduler.retrievability(365, stability = 10.0)
        assertTrue(r0 > 0.99, "retrievability right after review should be ~1")
        assertTrue(r30 < r0)
        assertTrue(r365 < r30)
        assertTrue(r365 > 0.0)
    }

    @Test
    fun `higher stability retains longer at the same elapsed time`() {
        val lowStability = FsrsScheduler.retrievability(30, stability = 5.0)
        val highStability = FsrsScheduler.retrievability(30, stability = 50.0)
        assertTrue(highStability > lowStability)
    }

    @Test
    fun `next interval is always at least 1 day`() {
        assertTrue(FsrsScheduler.nextIntervalDays(0.001) >= 1)
    }

    @Test
    fun `difficulty stays within the documented 1 to 10 bounds across many reviews`() {
        var state = FsrsScheduler.initial(FsrsGrade.AGAIN, t0)
        var now = t0
        val grades =
            listOf(FsrsGrade.EASY, FsrsGrade.AGAIN, FsrsGrade.HARD, FsrsGrade.GOOD, FsrsGrade.EASY, FsrsGrade.AGAIN)
        repeat(50) { i ->
            now = now.plus(1, ChronoUnit.DAYS)
            state = FsrsScheduler.review(state, grades[i % grades.size], now)
            assertTrue(state.difficulty in 1.0..10.0, "difficulty out of bounds: ${state.difficulty}")
            assertTrue(state.stability > 0.0)
        }
    }
}
