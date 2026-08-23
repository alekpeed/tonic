package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.model.state.MasteryVerdict
import kotlin.math.abs

/**
 * Mastery for `M3` production nodes — docs/40-PHASE-4-SPEC.md §5.3.
 *
 * Separate from [RhythmMasteryEvaluator] because §5.3 does not adapt the recognition criteria here, it
 * replaces them: "production nodes replace the per-degree criteria with rhythm equivalents", and the
 * five it lists read tap data that a recognition attempt does not have. Sharing one object would have
 * meant branching on whether an attempt happened to carry a tap record, which is how an attempt with a
 * dropped rhythm record comes to be judged by the wrong rules.
 *
 * ### Criterion 2, and why it counts whole attempts
 *
 * §5.3 criterion 2 reads "timing consistency within the current tolerance on ≥ 85% of correct
 * patterns", and taken word for word it is vacuous: a pattern is only *correct* when every tap already
 * landed inside the tolerance, so the share of correct patterns whose taps are inside the tolerance is
 * always 100%. Something else must be meant, and two sections say which.
 *
 * §6.1: timing precision is "secondary, informational at low levels, gating only via
 * `TIMING_TOLERANCE` at higher ones." §6.3: raw asynchrony magnitude "does not enter the staircase or
 * mastery evaluation directly", with drift analysis called out as the single exception. Between them
 * they rule out the other reading — a spread or variance statistic over asynchronies would be exactly
 * the magnitude §6.3 forbids, dressed up. What is left, and what is implemented, is that timing enters
 * through the window and only through the window: criterion 1 asks what share of *events* were struck,
 * criterion 2 asks what share of *patterns* were struck whole at the tolerance the learner faced. The
 * gating §6.1 describes then happens on its own, because raising `TIMING_TOLERANCE` narrows the window
 * and the same performance stops counting.
 *
 * ### Criterion 3, and why the threshold is a slope
 *
 * Drift is held as a fraction of a beat per beat — [Attempt.rhythm]'s `driftSlope`, dimensionless —
 * rather than in milliseconds. Milliseconds would make the bar depend on tempo, so `TEMPO_DEVIATION`
 * would silently become a second timing axis, which is the failure `ToleranceWindows` exists to avoid
 * for the window itself.
 *
 * The window's median is taken, and it is *signed*. "Systematic" is the operative word in §5.3: a
 * learner who rushes one pattern and drags the next is inconsistent, not drifting, and their
 * inconsistency is already paid for by criteria 1 and 2 when it costs them the window. A learner who
 * rushes every pattern has a median that says so. Median rather than mean for the same reason
 * `Calibrator` uses one: a single scrambled attempt should not decide a verdict about thirty.
 */
public object RhythmProductionMasteryEvaluator {
    /**
     * @param window the last [WINDOW_SIZE] non-warmup attempts at the node's current axis levels.
     * @param axes the node's current axis levels — read for `METRONOME_FADE` only.
     */
    public fun evaluate(
        window: List<Attempt>,
        axes: Map<DifficultyAxis, Int>,
    ): MasteryVerdict {
        val fadeLevel = axes[DifficultyAxis.METRONOME_FADE] ?: 0

        // An attempt with no tap record on a production node is a partial write or a dropped rhythm
        // record (see Mappers.rhythmOrNull), not a pitch attempt. Falling back to whether it was
        // marked correct keeps it in the window at the coarsest honest resolution rather than either
        // discarding it - which would let a learner shrink their own window - or crediting it.
        val patternAccuracy =
            if (window.isEmpty()) {
                0.0
            } else {
                window.sumOf { it.rhythm?.patternAccuracy ?: if (it.correct) 1.0 else 0.0 } / window.size
            }

        val wholePatternShare =
            if (window.isEmpty()) 0.0 else window.count { it.correct }.toDouble() / window.size

        // Correct attempts only. An incorrect one fits its trend through whichever events happened to
        // be matched, and a line through the survivors of a scrambled performance describes nothing.
        val driftSlopes = window.filter { it.correct }.mapNotNull { it.rhythm?.driftSlope }.sorted()
        val medianDrift = median(driftSlopes)

        val figureTotals = mutableMapOf<String, Pair<Int, Int>>()
        for (attempt in window) {
            for ((figure, outcome) in attempt.rhythm?.figureOutcomes().orEmpty()) {
                val (produced, seen) = figureTotals[figure] ?: (0 to 0)
                figureTotals[figure] = (produced + outcome.first) to (seen + outcome.second)
            }
        }
        val weakestFigure =
            figureTotals.values
                .filter { it.second > 0 }
                .minOfOrNull { it.first.toDouble() / it.second }

        return MasteryVerdict(
            listOf(
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.WINDOW_COVERAGE,
                    met = window.size >= WINDOW_SIZE,
                    measuredValue = window.size.toDouble(),
                    requiredValue = WINDOW_SIZE.toDouble(),
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.PATTERN_ACCURACY,
                    met = patternAccuracy >= MIN_PATTERN_ACCURACY,
                    measuredValue = patternAccuracy,
                    requiredValue = MIN_PATTERN_ACCURACY,
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.TIMING_CONSISTENCY,
                    met = wholePatternShare >= MIN_WHOLE_PATTERN_SHARE,
                    measuredValue = wholePatternShare,
                    requiredValue = MIN_WHOLE_PATTERN_SHARE,
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.TIMING_DRIFT,
                    // Vacuously met when nothing in the window has two matched events to fit a line
                    // through. Sample size is WINDOW_COVERAGE's job, and duplicating it here would
                    // report the same shortfall twice under two names.
                    met = medianDrift == null || abs(medianDrift) <= MAX_DRIFT_SLOPE,
                    measuredValue = medianDrift?.let { abs(it) } ?: 0.0,
                    requiredValue = MAX_DRIFT_SLOPE,
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.METRONOME_FADE_MINIMUM,
                    met = fadeLevel >= MetronomeFadeLevel.MASTERY_MINIMUM.level,
                    measuredValue = fadeLevel.toDouble(),
                    requiredValue = MetronomeFadeLevel.MASTERY_MINIMUM.level.toDouble(),
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY,
                    // Vacuously met when no figure was seen at all, which is a window with no tap
                    // records in it - the same shape the two figure criteria take for a recognition
                    // node that discriminates nothing.
                    met = weakestFigure == null || weakestFigure >= MIN_FIGURE_ACCURACY,
                    measuredValue = weakestFigure ?: 1.0,
                    requiredValue = MIN_FIGURE_ACCURACY,
                ),
            ),
        )
    }

    private fun median(sorted: List<Double>): Double? =
        when {
            sorted.isEmpty() -> null
            sorted.size % 2 == 1 -> sorted[sorted.size / 2]
            else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }

    /** The rolling window §5.3 asks for, matching every other evaluator in this package. */
    public const val WINDOW_SIZE: Int = 30

    /** §5.3 criterion 1: overall pattern accuracy ≥ 90%, measured per event. */
    public const val MIN_PATTERN_ACCURACY: Double = 0.90

    /** §5.3 criterion 2: whole patterns inside the current tolerance on ≥ 85% of the window. */
    public const val MIN_WHOLE_PATTERN_SHARE: Double = 0.85

    /** §5.3 criterion 5: no single rhythmic figure below 80%. */
    public const val MIN_FIGURE_ACCURACY: Double = 0.80

    /**
     * §5.3 criterion 3: the largest systematic drift a learner may carry, as a fraction of a beat
     * gained or lost per beat.
     *
     * ⚠️ Reasoned, not measured — the same standing caveat `ToleranceWindows.beatFractionFor` carries,
     * and the same measurement would move it.
     *
     * The reasoning starts from where this criterion can do any work at all. Drift steep enough to
     * carry a learner out of the tolerance window already fails criteria 1 and 2, so criterion 3 is
     * only ever the binding one inside the band where a trend stays *within* the window — which is
     * the case worth catching, because it is the learner who is not holding tempo and passes anyway.
     * The widest such trend is one centered on the window: starting half a window early and ending
     * half a window late, every tap inside, nothing about the pattern scored wrong. Over the sixteen
     * beats `PATTERN_LENGTH` L4 produces in common time, at `TIMING_TOLERANCE` L0's quarter-beat
     * window, that is a slope of 0.033 — half a beat of swing spread across fifteen beat intervals.
     * Set below it at 0.02, the criterion catches that learner; on a one-bar pattern the same slope
     * accumulates 6% of a beat, so it does not fail someone whose short pattern simply has a slight
     * lean in it.
     *
     * At tighter tolerances the window is narrower than this bar and does the work itself, which is
     * `TIMING_TOLERANCE` behaving exactly as §6.1 describes.
     */
    public const val MAX_DRIFT_SLOPE: Double = 0.02
}
