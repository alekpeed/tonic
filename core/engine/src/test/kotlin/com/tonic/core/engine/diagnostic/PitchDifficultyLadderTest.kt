package com.tonic.core.engine.diagnostic

import com.tonic.core.model.state.StaircaseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitchDifficultyLadderTest {
    @Test
    fun `ladder starts at 1200 cents and ends at 10 cents, strictly descending`() {
        val levels = PitchDifficultyLadder.LEVELS_CENTS
        assertEquals(1200, levels.first())
        assertEquals(10, levels.last())
        assertTrue(levels.zipWithNext().all { (a, b) -> a > b }, "must be strictly descending")
    }

    @Test
    fun `the sub-semitone tail matches docs 03-CURRICULUM 3 exactly`() {
        assertEquals(listOf(100, 75, 50, 30, 20, 10), PitchDifficultyLadder.LEVELS_CENTS.takeLast(6))
    }

    @Test
    fun `centsFor clamps out-of-range levels instead of throwing`() {
        assertEquals(1200, PitchDifficultyLadder.centsFor(-5))
        assertEquals(10, PitchDifficultyLadder.centsFor(999))
    }

    @Test
    fun `estimatedThresholdCents falls back to the raw level when not yet converged`() {
        val state = StaircaseState(level = 3)
        assertEquals(PitchDifficultyLadder.centsFor(3), PitchDifficultyLadder.estimatedThresholdCents(state))
    }

    @Test
    fun `estimatedThresholdCents interpolates between adjacent levels for a fractional threshold`() {
        // Reversals at levels 10 and 11 (100-cent tail levels 200 and 100 cents) average to 10.5.
        val state = StaircaseState(level = 11, reversals = listOf(10, 11, 10, 11, 10, 11))
        val expected = (PitchDifficultyLadder.LEVELS_CENTS[10] + PitchDifficultyLadder.LEVELS_CENTS[11]) / 2
        assertEquals(expected, PitchDifficultyLadder.estimatedThresholdCents(state))
    }

    @Test
    fun `update moves toward finer difficulty when correct and coarser when incorrect, staying within ladder bounds`() {
        var state = StaircaseState(level = 0)
        state = PitchDifficultyLadder.update(state, correct = false)
        assertEquals(0, state.level, "already easiest - a miss must not go out of bounds")

        state = StaircaseState(level = PitchDifficultyLadder.LEVEL_RANGE.last)
        state = PitchDifficultyLadder.update(state, correct = true)
        state = PitchDifficultyLadder.update(state, correct = true)
        assertEquals(
            PitchDifficultyLadder.LEVEL_RANGE.last,
            state.level,
            "already hardest - two corrects must not go out of bounds",
        )
    }
}
