package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.ConfusionMatrix
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.model.state.MasteryVerdict

/**
 * The five docs/03-CURRICULUM.md §5.5 mastery criteria, all evaluated over
 * the *same* rolling window of the last 30 attempts at the node's current
 * axis levels — including the confusion-pair criterion, which is why
 * criteria 3/4 are computed directly from [window] rather than from
 * [com.tonic.core.engine.confusion.ConfusionTracker]'s persistent 100-item
 * window: mastery and confusion tracking are deliberately different rolling
 * windows for different purposes (docs/07-ADAPTIVE-ENGINE.md §1 — "two
 * different problems," kept separate).
 */
object MasteryEvaluator {
    const val WINDOW_SIZE = 30
    const val MIN_OVERALL_ACCURACY = 0.90
    const val MIN_ATTEMPTS_PER_DEGREE = 5
    const val MIN_DEGREE_ACCURACY = 0.80
    const val MAX_CONFUSION_PAIR_SHARE = 0.15

    /**
     * [window] should already be exactly the last (up to) 30 non-warm-up
     * attempts at the node's current axis levels — filtering that is the
     * caller's job (docs/07-ADAPTIVE-ENGINE.md §8: warm-up attempts are
     * recorded but excluded from mastery evaluation).
     */
    fun evaluate(
        window: List<Attempt>,
        activeDegrees: Set<ScaleDegree>,
        axes: Map<DifficultyAxis, Int>,
    ): MasteryVerdict {
        val overallAccuracy = if (window.isEmpty()) 0.0 else window.count { it.correct }.toDouble() / window.size

        val attemptsByDegree = window.groupingBy { it.targetLabel }.eachCount()
        val activeDegreeLabels = activeDegrees.map { it.degree.toString() }
        val minCoverage = activeDegreeLabels.minOfOrNull { attemptsByDegree.getOrDefault(it, 0) } ?: 0

        // docs/03-CURRICULUM.md's literal "at least 5 attempts per active degree in a 30-item window" is
        // mathematically impossible once 7 degrees are simultaneously active (M2_FULL_DIATONIC):
        // 7 * 5 = 35 > WINDOW_SIZE (30). No distribution of 30 items across 7 degrees can give every
        // degree 5 attempts. Resolved by scaling the requirement down to the maximum achievable for the
        // active degree count, capped at the documented value of 5 - so every skill with <=6 active
        // degrees still requires exactly 5, and only the 7-degree case relaxes (to 4).
        val requiredAttemptsPerDegree =
            if (activeDegreeLabels.isEmpty()) {
                MIN_ATTEMPTS_PER_DEGREE
            } else {
                minOf(MIN_ATTEMPTS_PER_DEGREE, WINDOW_SIZE / activeDegreeLabels.size)
            }

        val accuracyByDegree =
            activeDegreeLabels.associateWith { degree ->
                val forDegree = window.filter { it.targetLabel == degree }
                if (forDegree.isEmpty()) null else forDegree.count { it.correct }.toDouble() / forDegree.size
            }
        val weakestAccuracy = accuracyByDegree.values.filterNotNull().minOrNull() ?: 0.0

        // "No single confusion pair ... accounts for more than 15% of TOTAL ATTEMPTS IN THE WINDOW" -
        // docs/03-CURRICULUM.md §5.5 criterion 4, verbatim (the window being the whole 30-item mastery
        // window, not a per-target count). This is deliberately NOT the same denominator as the
        // confusion-*tracking* pair definition in docs/07-ADAPTIVE-ENGINE.md §4 ("exceeding 10% of that
        // target's attempts") - that one is computed over ConfusionTracker's separate, much larger
        // persistent 100-item window (see the class doc above), where a per-target percentage is
        // statistically meaningful. Applied to a 7-degree node's 30-item mastery window, a single target
        // typically has only ~4 attempts, so even one honest slip reads as an artificially huge
        // "confusion share" and made mastery nearly unreachable - confirmed directly by simulation
        // (CadenceDependentLearnerSimulationTest went from occasionally reaching mastery to essentially
        // never, across hundreds of items, once tried against a per-target denominator here).
        //
        // Using the literal window-total denominator instead does mean this criterion is mathematically
        // implied by criterion 1 for these exact thresholds: OVERALL_ACCURACY's 90% floor caps total
        // wrong answers in a 30-item window at 3, and no single confusion pair's count can exceed the
        // total wrong count - so a single pair can never reach the 4.5 (15% of 30) needed to violate this
        // criterion whenever criterion 1 already holds. That's a genuine property of the spec's own
        // chosen numbers (10% vs 15%, window size 30), not an implementation bug; it's implemented here
        // exactly as documented and the redundancy is covered by a regression test in
        // MasteryEvaluatorTest rather than papered over with a different metric.
        val confusionMatrix = matrixFromWindow(window)
        val worstConfusionShare =
            confusionMatrix
                .confusionPairsAbove(0.0)
                .maxOfOrNull { it.windowCount.toDouble() / window.size } ?: 0.0

        val cadenceFadeLevel = axes[DifficultyAxis.CADENCE_FADE] ?: 0

        val criteria =
            listOf(
                MasteryCriterion(
                    MasteryCriterion.Kind.OVERALL_ACCURACY,
                    overallAccuracy >= MIN_OVERALL_ACCURACY,
                    overallAccuracy,
                    MIN_OVERALL_ACCURACY,
                ),
                MasteryCriterion(
                    MasteryCriterion.Kind.DEGREE_COVERAGE,
                    minCoverage >= requiredAttemptsPerDegree,
                    minCoverage.toDouble(),
                    requiredAttemptsPerDegree.toDouble(),
                ),
                MasteryCriterion(
                    MasteryCriterion.Kind.WEAKEST_DEGREE_ACCURACY,
                    weakestAccuracy >= MIN_DEGREE_ACCURACY,
                    weakestAccuracy,
                    MIN_DEGREE_ACCURACY,
                ),
                MasteryCriterion(
                    MasteryCriterion.Kind.CONFUSION_CAP,
                    worstConfusionShare <= MAX_CONFUSION_PAIR_SHARE,
                    worstConfusionShare,
                    MAX_CONFUSION_PAIR_SHARE,
                ),
                // "Criterion 5 is the one that matters. Without it a user can 'master' a node while
                // remaining entirely dependent on the cadence crutch." - docs/03-CURRICULUM.md §5.5.
                MasteryCriterion(
                    MasteryCriterion.Kind.CADENCE_FADE_MINIMUM,
                    cadenceFadeLevel >= CadenceFadeLevel.MASTERY_MINIMUM.level,
                    cadenceFadeLevel.toDouble(),
                    CadenceFadeLevel.MASTERY_MINIMUM.level.toDouble(),
                ),
            )

        return MasteryVerdict(criteria)
    }

    private fun matrixFromWindow(window: List<Attempt>): ConfusionMatrix {
        val bySkill =
            window.firstOrNull()?.skillId
                ?: return ConfusionMatrix(com.tonic.core.model.ids.SkillIds.M2_DEG_SET_1, emptyList())
        val counts =
            window
                .filter { it.responseLabel != null }
                .groupingBy { it.targetLabel to it.responseLabel!! }
                .eachCount()
        val cells =
            counts.map { (pair, count) ->
                com.tonic.core.model.state
                    .ConfusionCell(pair.first, pair.second, count, count, java.time.Instant.EPOCH)
            }
        return ConfusionMatrix(bySkill, cells)
    }
}
