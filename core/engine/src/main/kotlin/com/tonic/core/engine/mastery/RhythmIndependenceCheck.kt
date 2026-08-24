package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.model.state.MasteryVerdict

/**
 * `M3.INDEPENDENCE_CHECK` — docs/40-PHASE-4-SPEC.md §5.1 and §5.4: "30 production items at
 * `METRONOME_FADE` L6."
 *
 * The rhythm counterpart of [IndependenceCheck], and it exists for the identical reason. Mastery
 * requires `METRONOME_FADE` to reach L4, which is the first level with no metronome *under* the
 * pattern — but L4 and L5 still hand the learner a full or one-bar count-in, so the pulse they keep is
 * one they were just given. L6 gives two beats and then nothing. A learner who mastered by leaning on
 * a long count-in can still fail here, and that is the whole point: §3.2 calls the fade "the
 * pedagogical centerpiece", and this is the test of whether the internal pulse it trains is actually
 * there.
 *
 * Separate from [IndependenceCheck] rather than parameterized over an axis. The two read different
 * columns of the attempt — one the denormalized `cadenceFadeLevel`, the other the axis map, because
 * rhythm has no denormalized column of its own (`PracticeItems.cadenceFadeLevel` records 0 for a
 * rhythm item, since repurposing a column named for the pitch track's axis would make one integer
 * mean two different fades depending on the row's module).
 */
public object RhythmIndependenceCheck {
    /** Matching [IndependenceCheck.REQUIRED_ITEMS]: §5.1's "30 production items". */
    public const val REQUIRED_ITEMS: Int = 30

    /**
     * The same 85% the pitch check uses.
     *
     * Not lowered for rhythm despite production conflating perception with motor execution (§3.3).
     * The tolerance window is where motor slop is already forgiven — `TIMING_TOLERANCE` exists for
     * exactly that — so an attempt that reaches this check has already been judged generously on
     * timing and strictly on whether the right sounds happened. Discounting the threshold as well
     * would forgive the same thing twice.
     */
    public const val PASS_THRESHOLD: Double = 0.85

    public data class Result(
        public val passed: Boolean,
        public val accuracy: Double,
        public val itemCount: Int,
    ) {
        /**
         * This result in the shape the replay expects.
         *
         * Two criteria rather than six, because a pass-or-fail assessment has two things to report:
         * was there enough of it, and was it good enough. Reported as criteria anyway so the progress
         * screen can say which of the two is missing, which is docs/03-CURRICULUM.md §5.5's reason for
         * reporting criteria individually at all.
         */
        public fun toVerdict(): MasteryVerdict =
            MasteryVerdict(
                listOf(
                    MasteryCriterion(
                        kind = MasteryCriterion.Kind.WINDOW_COVERAGE,
                        met = itemCount >= REQUIRED_ITEMS,
                        measuredValue = itemCount.toDouble(),
                        requiredValue = REQUIRED_ITEMS.toDouble(),
                    ),
                    MasteryCriterion(
                        kind = MasteryCriterion.Kind.OVERALL_ACCURACY,
                        met = accuracy >= PASS_THRESHOLD,
                        measuredValue = accuracy,
                        requiredValue = PASS_THRESHOLD,
                    ),
                ),
            )
    }

    /** [items] must all be at `METRONOME_FADE` L6; anything else is a caller bug, not learner data. */
    public fun evaluate(items: List<Attempt>): Result {
        require(items.all { fadeLevelOf(it) == MetronomeFadeLevel.INDEPENDENCE_CHECK_LEVEL }) {
            "RhythmIndependenceCheck items must all be at METRONOME_FADE L6"
        }
        val accuracy = if (items.isEmpty()) 0.0 else items.count { it.correct }.toDouble() / items.size
        return Result(
            passed = items.size >= REQUIRED_ITEMS && accuracy >= PASS_THRESHOLD,
            accuracy = accuracy,
            itemCount = items.size,
        )
    }

    /**
     * Failure lowers the fade by one and schedules more work — never punitive, and never more than one
     * level, which is docs/07-ADAPTIVE-ENGINE.md §2a's rule for any axis that withdraws support.
     */
    public fun applyFailure(currentAxes: Map<DifficultyAxis, Int>): Map<DifficultyAxis, Int> {
        val fade = currentAxes[DifficultyAxis.METRONOME_FADE] ?: 0
        return currentAxes + (DifficultyAxis.METRONOME_FADE to (fade - 1).coerceAtLeast(0))
    }

    private fun fadeLevelOf(attempt: Attempt): Int = attempt.axisLevels[DifficultyAxis.METRONOME_FADE] ?: 0
}
