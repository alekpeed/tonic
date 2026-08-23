package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.model.state.MasteryVerdict

/**
 * Mastery for `M3` recognition nodes — docs/40-PHASE-4-SPEC.md §5.3 and §8.
 *
 * §5.3 says recognition nodes "use the existing five criteria unchanged", and taken literally that is
 * impossible: three of [MasteryEvaluator]'s five are about scale degrees and a fourth is about
 * `CADENCE_FADE`, and a rhythm node has neither. What §5.3 means is the *shape* — an accuracy floor, a
 * coverage requirement, a weakest-unit floor, a confusion cap, and a fade minimum — with rhythm's own
 * unit substituted for the degree. §8 names that unit: "the 'confusion matrix' for rhythm is over
 * *rhythmic figures*, not labels."
 *
 * So the substitution is figure-for-degree, one for one:
 *
 * | Pitch | Rhythm |
 * |---|---|
 * | `OVERALL_ACCURACY` | unchanged — 90% over a rolling 30 |
 * | `DEGREE_COVERAGE` | [MasteryCriterion.Kind.FIGURE_COVERAGE] |
 * | `WEAKEST_DEGREE_ACCURACY` | [MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY] |
 * | `CONFUSION_CAP` | unchanged — the matrix is over strings and does not care what they name |
 * | `CADENCE_FADE_MINIMUM` | [MasteryCriterion.Kind.METRONOME_FADE_MINIMUM] |
 *
 * The unit is a beat's fill rather than a whole pattern, and that is the decision that makes the
 * criteria usable at all. A pattern is very nearly unique — vary one sixteenth and it is a different
 * pattern — so coverage over patterns could never be met by anyone, and a confusion matrix over them
 * would have one cell per item. A beat's fill is a small alphabet that recurs, and it is what
 * §3.1's Takadimi already names, so a confusion view can say "you hear `ta-di` when it was
 * `ta-ka-di-mi`" in the learner's own vocabulary.
 *
 * A node with no figures to discriminate — `M3.BEAT_FIND`, `M3.DOWNBEAT` — reports the two
 * figure criteria as met, so the criteria list stays one shape for every rhythm node rather than each
 * caller carrying a special case. That is the same device [MasteryEvaluator] uses for a node that
 * introduces no degree.
 */
public object RhythmMasteryEvaluator {
    /**
     * @param window the last [WINDOW_SIZE] non-warmup attempts at the node's current axis levels.
     * @param activeFigures the figures this node teaches, from `SkillGraph.activeFiguresFor`. Empty for
     *   a node that discriminates no figures.
     * @param axes the node's current axis levels — read for `METRONOME_FADE` only.
     */
    public fun evaluate(
        window: List<Attempt>,
        activeFigures: Set<String>,
        axes: Map<DifficultyAxis, Int>,
    ): MasteryVerdict {
        val accuracy = if (window.isEmpty()) 0.0 else window.count { it.correct }.toDouble() / window.size
        val fadeLevel = axes[DifficultyAxis.METRONOME_FADE] ?: 0

        val byFigure = window.groupBy { it.targetLabel }
        val coverageShortfall =
            activeFigures.minOfOrNull { figure -> byFigure[figure]?.size ?: 0 } ?: MIN_PER_FIGURE
        val weakestFigure =
            activeFigures
                .mapNotNull { figure ->
                    val attempts = byFigure[figure] ?: return@mapNotNull null
                    if (attempts.isEmpty()) null else attempts.count { it.correct }.toDouble() / attempts.size
                }.minOrNull() ?: 1.0

        val worstPairShare =
            window
                .filter { !it.correct && it.responseLabel != null }
                .groupingBy { it.targetLabel to it.responseLabel }
                .eachCount()
                .values
                .maxOrNull()
                ?.let { if (window.isEmpty()) 0.0 else it.toDouble() / window.size }
                ?: 0.0

        return MasteryVerdict(
            listOf(
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.WINDOW_COVERAGE,
                    met = window.size >= WINDOW_SIZE,
                    measuredValue = window.size.toDouble(),
                    requiredValue = WINDOW_SIZE.toDouble(),
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.OVERALL_ACCURACY,
                    met = accuracy >= MIN_ACCURACY,
                    measuredValue = accuracy,
                    requiredValue = MIN_ACCURACY,
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.FIGURE_COVERAGE,
                    // Vacuously met when the node teaches no figures - see the class KDoc.
                    met = activeFigures.isEmpty() || coverageShortfall >= MIN_PER_FIGURE,
                    measuredValue =
                        if (activeFigures.isEmpty()) {
                            MIN_PER_FIGURE.toDouble()
                        } else {
                            coverageShortfall
                                .toDouble()
                        },
                    requiredValue = MIN_PER_FIGURE.toDouble(),
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY,
                    met = activeFigures.isEmpty() || weakestFigure >= MIN_FIGURE_ACCURACY,
                    measuredValue = if (activeFigures.isEmpty()) 1.0 else weakestFigure,
                    requiredValue = MIN_FIGURE_ACCURACY,
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.CONFUSION_CAP,
                    met = worstPairShare <= MAX_CONFUSION_SHARE,
                    measuredValue = worstPairShare,
                    requiredValue = MAX_CONFUSION_SHARE,
                ),
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.METRONOME_FADE_MINIMUM,
                    met = fadeLevel >= MetronomeFadeLevel.MASTERY_MINIMUM.level,
                    measuredValue = fadeLevel.toDouble(),
                    requiredValue = MetronomeFadeLevel.MASTERY_MINIMUM.level.toDouble(),
                ),
            ),
        )
    }

    /** The rolling window, matching [MasteryEvaluator.WINDOW_SIZE] — docs/03-CURRICULUM.md §5.5. */
    public const val WINDOW_SIZE: Int = 30

    /** docs/03-CURRICULUM.md §5.5 criterion 1, unchanged for rhythm. */
    public const val MIN_ACCURACY: Double = 0.90

    /** Criterion 2's five attempts, per figure rather than per degree. */
    public const val MIN_PER_FIGURE: Int = 5

    /** Criterion 3's 80%, per figure. */
    public const val MIN_FIGURE_ACCURACY: Double = 0.80

    /** Criterion 4's 15%, over figure pairs. */
    public const val MAX_CONFUSION_SHARE: Double = 0.15
}
