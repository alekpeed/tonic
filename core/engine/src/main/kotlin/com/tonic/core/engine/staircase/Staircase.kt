package com.tonic.core.engine.staircase

import com.tonic.core.model.state.Direction
import com.tonic.core.model.state.StaircaseState

/**
 * Transformed 2-down/1-up staircase — docs/07-ADAPTIVE-ENGINE.md §2. Two
 * consecutive correct responses raise the level by one step; one incorrect
 * response lowers it by one step. This converges on ~70.7% correct, the
 * standard auditory-perceptual-learning target and a reasonable "desirable
 * difficulty" operating point.
 */
object Staircase {
    /**
     * [correct] is ignored — the state is returned unchanged — when the
     * attempt was abandoned; docs/07-ADAPTIVE-ENGINE.md §2: "Abandoned
     * attempts (audio interruption, session kill) do not update the
     * staircase." Model that by simply not calling this function for an
     * abandoned attempt, rather than adding an `abandoned` parameter here —
     * keeps this function's contract to exactly "one real response in, one
     * updated state out."
     */
    fun update(
        state: StaircaseState,
        correct: Boolean,
        bounds: IntRange,
    ): StaircaseState {
        if (correct) {
            val consecutiveCorrect = state.consecutiveCorrect + 1
            if (consecutiveCorrect < 2) {
                return state.copy(consecutiveCorrect = consecutiveCorrect)
            }
            return move(state, Direction.UP, bounds).copy(consecutiveCorrect = 0)
        }
        return move(state, Direction.DOWN, bounds).copy(consecutiveCorrect = 0)
    }

    private fun move(
        state: StaircaseState,
        direction: Direction,
        bounds: IntRange,
    ): StaircaseState {
        val delta = if (direction == Direction.UP) state.stepSize else -state.stepSize
        val newLevel = (state.level + delta).coerceIn(bounds)

        val isReversal = state.lastDirection != null && state.lastDirection != direction
        val newReversals = if (isReversal) state.reversals + state.level else state.reversals

        // Step size starts at 2 for fast initial convergence, halves to 1 after the second reversal,
        // never below 1 — docs/07-ADAPTIVE-ENGINE.md §2.
        val newStepSize =
            if (isReversal && newReversals.size == StaircaseState.STEP_HALVING_AFTER_REVERSAL) {
                (state.stepSize / 2).coerceAtLeast(StaircaseState.MIN_STEP_SIZE)
            } else {
                state.stepSize
            }

        return state.copy(
            level = newLevel,
            reversals = newReversals,
            stepSize = newStepSize,
            lastDirection = direction,
        )
    }
}
