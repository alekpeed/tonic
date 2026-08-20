package com.tonic.core.engine.diagnostic

import com.tonic.core.engine.staircase.Staircase
import com.tonic.core.model.state.StaircaseState
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * `M0.PITCH_DIR`'s difficulty scale — docs/03-CURRICULUM.md §3: "start 12 semitones, converge via
 * 2-down/1-up staircase down to a floor of 1 semitone, then to sub-semitone cent differences (100, 75,
 * 50, 30, 20, 10 cents) if the user is performing at ceiling." Represented as one ordered ladder of
 * cent values rather than two separate mechanisms, so the existing level-index [Staircase] (already
 * built and tested for M2's axes, docs/09-BUILD-PLAN.md Stage 4) drives it directly with no new
 * staircase math: level 0 is the easiest (1200 cents), the last level the hardest (10 cents), and
 * `differenceCents = LEVELS[level]`.
 */
object PitchDifficultyLadder {
    /** Full-semitone steps from 1200 cents down to 100, then the documented sub-semitone tail. */
    val LEVELS_CENTS: List<Int> =
        (12 downTo 1).map { it * 100 } + listOf(75, 50, 30, 20, 10)

    val LEVEL_RANGE: IntRange = LEVELS_CENTS.indices.first..LEVELS_CENTS.indices.last

    /** `differenceCents` for the current [StaircaseState.level]. */
    fun centsFor(level: Int): Int = LEVELS_CENTS[level.coerceIn(LEVEL_RANGE)]

    /**
     * One real response. Wraps [Staircase.update] with this ladder's bounds - kept as a thin, named
     * entry point rather than making every caller pass [LEVEL_RANGE] itself.
     */
    fun update(
        state: StaircaseState,
        correct: Boolean,
    ): StaircaseState = Staircase.update(state, correct, LEVEL_RANGE)

    /**
     * The threshold estimate to report as `pitchDirectionThresholdCents` - docs/03-CURRICULUM.md §3.
     * [StaircaseState.estimatedThreshold] is a mean of reversal *level indices*, generally fractional;
     * linearly interpolated between the two adjacent ladder entries rather than rounded to the nearest
     * level first, since the ladder's spacing is uneven (100-cent steps down to 100, then irregular
     * below it) and rounding-then-lookup would distort the estimate right at that seam. Falls back to
     * the raw current level's cents value if the staircase hasn't converged - docs/03-CURRICULUM.md §3's
     * other termination path, "24 items, whichever first."
     */
    fun estimatedThresholdCents(state: StaircaseState): Int {
        val threshold = state.estimatedThreshold ?: return centsFor(state.level)
        val floorLevel = floor(threshold).toInt().coerceIn(LEVEL_RANGE)
        val ceilLevel = (floorLevel + 1).coerceIn(LEVEL_RANGE)
        if (floorLevel == ceilLevel) return centsFor(floorLevel)
        val fraction = threshold - floorLevel
        val interpolated = LEVELS_CENTS[floorLevel] + fraction * (LEVELS_CENTS[ceilLevel] - LEVELS_CENTS[floorLevel])
        return interpolated.roundToInt()
    }
}
