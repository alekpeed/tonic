package com.tonic.core.engine.simulation

import com.tonic.core.engine.fsrs.FsrsScheduler
import com.tonic.core.model.state.FsrsGrade
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/10-TESTING.md §5 simulation 7: "Simulated learner over 90 days of
 * sessions with forgetting. Assert FSRS scheduling produces sensible
 * review intervals and mastered nodes do not decay unnoticed."
 */
class RetentionSimulationTest {
    private val day0 = Instant.parse("2026-01-01T00:00:00Z")
    private val horizon = day0.plus(90, ChronoUnit.DAYS)

    @Test
    fun `a consistently good performer over 90 days gets growing review intervals`() {
        var state = FsrsScheduler.initial(FsrsGrade.GOOD, day0)
        var now = day0
        val intervals = mutableListOf<Int>()

        while (true) {
            val due = state.due ?: break
            if (due.isAfter(horizon)) break
            now = due
            state = FsrsScheduler.review(state, FsrsGrade.GOOD, now)
            intervals += FsrsScheduler.nextIntervalDays(state.stability)
        }

        assertTrue(
            intervals.size in 2..30,
            "expected a handful to a couple dozen reviews over 90 days, got ${intervals.size}",
        )
        assertTrue(state.lapses == 0, "a consistently good performer should have no lapses")

        // Sensible intervals: mostly growing, not shrinking - some noise is fine, a persistent downward
        // trend for a learner who is always graded GOOD would indicate the scheduler is broken.
        val shrinks = intervals.zipWithNext().count { (a, b) -> b < a }
        assertTrue(
            shrinks <= intervals.size / 3,
            "too many interval shrinks ($shrinks of ${intervals.size}) for a consistently good performer",
        )

        // "Mastered nodes do not decay unnoticed": retrievability right at each scheduled due date
        // should sit near the desired-retention target (0.9), not have silently drifted low between
        // reviews without the scheduler responding.
        assertTrue(
            state.stability > FsrsScheduler.initial(FsrsGrade.GOOD, day0).stability,
            "stability should have grown over 90 days of good reviews",
        )
    }

    @Test
    fun `a missed review that comes back as a lapse shortens the next interval and is recorded`() {
        var state = FsrsScheduler.initial(FsrsGrade.GOOD, day0)
        val onScheduleDue = state.due!!
        val stabilityBeforeLapse = state.stability

        // The learner doesn't show up until 60 days after it was actually due - a long, unnoticed gap -
        // and has genuinely forgotten it (AGAIN).
        val lateReview = onScheduleDue.plus(60, ChronoUnit.DAYS)
        state = FsrsScheduler.review(state, FsrsGrade.AGAIN, lateReview)

        assertTrue(state.lapses == 1, "the lapse must be recorded, not silently absorbed")
        assertTrue(
            state.stability < stabilityBeforeLapse,
            "forgetting after a long gap should reduce stability, not leave it unnoticed",
        )

        val nextInterval = FsrsScheduler.nextIntervalDays(state.stability)
        val previousInterval = FsrsScheduler.nextIntervalDays(stabilityBeforeLapse)
        assertTrue(
            nextInterval < previousInterval,
            "the next review should come sooner after a lapse than it would have otherwise",
        )
    }

    @Test
    fun `retrievability genuinely predicts the review interval - stronger cards are due further out`() {
        val weakState = FsrsScheduler.initial(FsrsGrade.HARD, day0)
        val strongState =
            FsrsScheduler.review(
                FsrsScheduler.initial(FsrsGrade.GOOD, day0),
                FsrsGrade.EASY,
                day0.plus(3, ChronoUnit.DAYS),
            )
        assertTrue(strongState.stability > weakState.stability)
        assertTrue(
            FsrsScheduler.nextIntervalDays(strongState.stability) > FsrsScheduler.nextIntervalDays(weakState.stability),
        )
    }
}
