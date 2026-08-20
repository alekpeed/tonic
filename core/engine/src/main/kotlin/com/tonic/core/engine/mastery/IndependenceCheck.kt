package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.items.DifficultyAxis

/**
 * `M2.INDEPENDENCE_CHECK` — docs/03-CURRICULUM.md §5.6. Runs once
 * `M2.FULL_DIATONIC` is mastered: 30 items at `CADENCE_FADE` L6 (all other
 * axes at the user's current level). This is the real test of functional
 * hearing versus cadence-crutch dependence — mastery criterion 5 only
 * requires reaching L4, and L4/L5 still provide *some* harmonic reference
 * (a drone, a brief flash); only L6+ provides none. A learner who passes
 * mastery by leaning on that residual reference can still fail here, which
 * is the point: docs/00-README.md calls this axis "the mechanism that
 * separates this app from every competitor that produces learners
 * dependent on a crutch."
 */
object IndependenceCheck {
    const val REQUIRED_ITEMS = 30
    const val PASS_THRESHOLD = 0.85
    private const val CADENCE_FADE_LEVEL_FOR_CHECK = 6

    data class Result(
        val passed: Boolean,
        val accuracy: Double,
        val itemCount: Int,
    )

    /** [items] must all be at `CADENCE_FADE` L6; anything else is a caller bug, not a learner-data problem. */
    fun evaluate(items: List<Attempt>): Result {
        require(items.all { it.cadenceFadeLevel == CADENCE_FADE_LEVEL_FOR_CHECK }) {
            "IndependenceCheck items must all be at CADENCE_FADE L6"
        }
        val accuracy = if (items.isEmpty()) 0.0 else items.count { it.correct }.toDouble() / items.size
        val passed = items.size >= REQUIRED_ITEMS && accuracy >= PASS_THRESHOLD
        return Result(passed, accuracy, items.size)
    }

    /**
     * "Failure is not punitive: it lowers the fade axis and schedules more
     * work." One step down from wherever the node currently sits, never
     * below 0.
     */
    fun applyFailure(currentAxes: Map<DifficultyAxis, Int>): Map<DifficultyAxis, Int> {
        val cadence = currentAxes[DifficultyAxis.CADENCE_FADE] ?: 0
        return currentAxes + (DifficultyAxis.CADENCE_FADE to (cadence - 1).coerceAtLeast(0))
    }
}
