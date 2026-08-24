package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.state.MasteryCriterion
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `M3.INDEPENDENCE_CHECK` — docs/40-PHASE-4-SPEC.md §5.1: "30 production items at `METRONOME_FADE`
 * L6."
 */
class RhythmIndependenceCheckTest {
    private fun attempt(
        correct: Boolean,
        index: Int,
        fade: Int = MetronomeFadeLevel.INDEPENDENCE_CHECK_LEVEL,
    ) = Attempt(
        skillId = SkillIds.M3_INDEPENDENCE_CHECK,
        sessionId = 1L,
        itemSeed = index.toLong(),
        axisLevels = mapOf(DifficultyAxis.METRONOME_FADE to fade),
        targetLabel = "TAPPED",
        responseLabel = "TAPPED",
        correct = correct,
        latencyMs = 0L,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 0,
        timbreId = "PURE",
        cadenceFadeLevel = 0,
        timestamp = Instant.EPOCH.plusSeconds(index.toLong()),
    )

    private fun run(
        size: Int,
        correctCount: Int,
    ) = (0 until size).map { attempt(correct = it < correctCount, index = it) }

    @Test
    fun `thirty items at eighty-five percent passes`() {
        val result = RhythmIndependenceCheck.evaluate(run(size = 30, correctCount = 26))
        assertTrue(result.passed, "26 of 30 is 86.7%, over the threshold")
        assertEquals(30, result.itemCount)
    }

    @Test
    fun `a short run never passes, however clean`() {
        // The count is half of what the check is: a learner cannot pass by keeping time for ten items.
        val result = RhythmIndependenceCheck.evaluate(run(size = 20, correctCount = 20))
        assertFalse(result.passed)
        assertEquals(1.0, result.accuracy)
    }

    @Test
    fun `just under the threshold fails`() {
        val result = RhythmIndependenceCheck.evaluate(run(size = 30, correctCount = 25))
        assertFalse(result.passed, "25 of 30 is 83.3%")
    }

    @Test
    fun `failing lowers the fade by exactly one level, and never below zero`() {
        // docs/07-ADAPTIVE-ENGINE.md §2a for any axis that withdraws support: one level at a time.
        // Skipping a level here does not make an item harder, it makes it unanswerable.
        val lowered =
            RhythmIndependenceCheck.applyFailure(mapOf(DifficultyAxis.METRONOME_FADE to 6))
        assertEquals(5, lowered[DifficultyAxis.METRONOME_FADE])

        val floored =
            RhythmIndependenceCheck.applyFailure(mapOf(DifficultyAxis.METRONOME_FADE to 0))
        assertEquals(0, floored[DifficultyAxis.METRONOME_FADE])
    }

    @Test
    fun `items at the wrong fade level are a caller bug, not a failed check`() {
        // Scoring a check run at L4 as a failure would tell a learner they cannot keep time unaided
        // when they were never asked to.
        val atLevelFour =
            run(size = 30, correctCount = 30).map {
                it.copy(axisLevels = mapOf(DifficultyAxis.METRONOME_FADE to 4))
            }
        val failure = runCatching { RhythmIndependenceCheck.evaluate(atLevelFour) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
    }

    @Test
    fun `the verdict says which of the two things is missing`() {
        val short = RhythmIndependenceCheck.evaluate(run(size = 10, correctCount = 10)).toVerdict()
        assertFalse(short.isMastered)
        assertEquals(MasteryCriterion.Kind.WINDOW_COVERAGE, short.blockingCriterion?.kind)
    }
}
