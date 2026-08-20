package com.tonic.core.engine.staircase

import com.tonic.core.model.state.Direction
import com.tonic.core.model.state.StaircaseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** docs/09-BUILD-PLAN.md Stage 4 acceptance: step-size halving, reversal counting, bounds clamping. */
class StaircaseTest {
    private val bounds = 0..7

    @Test
    fun `two correct in a row moves up one step, a single correct does not`() {
        val start = StaircaseState(level = 3)
        val afterOne = Staircase.update(start, correct = true, bounds)
        assertEquals(3, afterOne.level)
        assertEquals(1, afterOne.consecutiveCorrect)

        val afterTwo = Staircase.update(afterOne, correct = true, bounds)
        assertEquals(5, afterTwo.level) // step size starts at 2
        assertEquals(0, afterTwo.consecutiveCorrect)
    }

    @Test
    fun `one incorrect moves down one step immediately`() {
        val start = StaircaseState(level = 3)
        val after = Staircase.update(start, correct = false, bounds)
        assertEquals(1, after.level)
    }

    @Test
    fun `level clamps to axis bounds in both directions`() {
        var state = StaircaseState(level = 0)
        state = Staircase.update(state, correct = false, bounds)
        assertEquals(0, state.level, "must not go below the lower bound")

        state = StaircaseState(level = 7)
        state = Staircase.update(state, correct = true, bounds) // 1st correct, no move
        state = Staircase.update(state, correct = true, bounds) // 2nd correct, would move up
        assertEquals(7, state.level, "must not go above the upper bound")
    }

    @Test
    fun `step size halves after the second reversal and never drops below 1`() {
        var state = StaircaseState(level = 4, stepSize = 2)
        // correct, correct (up, reversal #1 if a down preceded - start with a down first)
        state = Staircase.update(state, correct = false, bounds) // down, level 2
        state = Staircase.update(state, correct = true, bounds) // consecutive=1
        state = Staircase.update(state, correct = true, bounds) // up -> reversal #1 (down->up)
        assertEquals(1, state.reversals.size)
        assertEquals(2, state.stepSize, "step size only halves after the SECOND reversal")

        state = Staircase.update(state, correct = false, bounds) // down -> reversal #2
        assertEquals(2, state.reversals.size)
        assertEquals(1, state.stepSize, "halved from 2 to 1 after the second reversal")

        // Further reversals must not halve below 1.
        state = Staircase.update(state, correct = true, bounds)
        state = Staircase.update(state, correct = true, bounds) // up -> reversal #3
        state = Staircase.update(state, correct = false, bounds) // down -> reversal #4
        assertTrue(state.stepSize >= 1)
    }

    @Test
    fun `convergence is declared after 6 reversals and threshold is the mean of the last 4`() {
        // Force a sequence of reversals by alternating long runs of correct/incorrect.
        var state = StaircaseState(level = 4, stepSize = 1)
        val bigBounds = 0..100
        var direction = Direction.DOWN
        var iterations = 0
        // One incorrect (down), then two correct (up), alternately - each full cycle after the first
        // forces a reversal. The very first move never counts as a reversal (there's no prior direction
        // to differ from yet), so this can take one more cycle than a naive "6 cycles -> 6 reversals"
        // count would suggest; loop on the actual convergence flag rather than a fixed iteration count.
        while (!state.hasConverged && iterations < 20) {
            if (direction == Direction.DOWN) {
                state = Staircase.update(state, correct = false, bigBounds)
            } else {
                state = Staircase.update(state, correct = true, bigBounds)
                state = Staircase.update(state, correct = true, bigBounds)
            }
            direction = if (direction == Direction.DOWN) Direction.UP else Direction.DOWN
            iterations++
        }
        assertTrue(state.reversals.size >= 6, "expected convergence, got ${state.reversals.size} reversals")
        assertTrue(state.hasConverged)
        assertEquals(state.reversals.takeLast(4).average(), state.estimatedThreshold)
    }

    @Test
    fun `not converged and no threshold before 6 reversals`() {
        val state = StaircaseState(level = 3, reversals = listOf(1, 2, 3))
        assertTrue(!state.hasConverged)
        assertNull(state.estimatedThreshold)
    }
}
