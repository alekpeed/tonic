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

    /** Mirrors `BalancedSampler.MAX_FREQUENCY_MULTIPLE` — docs/03-CURRICULUM.md §5.4's 1.5x cap. */
    private const val BALANCE_MAX_FREQUENCY_MULTIPLE = 1.5

    /**
     * How many attempts the node's newly-introduced degree needs before its accuracy means anything —
     * docs/20-PHASE-2-SPEC.md §3, which asks for 5.
     *
     * Five is not always reachable, and the reason is a conflict inside the specs rather than a
     * shortfall here. docs/03-CURRICULUM.md §5.4 caps any degree at 1.5x its expected rate to stop
     * sampling clumps from distorting the confusion matrix; at twelve simultaneously active degrees
     * that cap is about two attempts per twenty items, so no degree can reach 5 in a 30-item window no
     * matter how it is weighted. §5.5's own per-degree coverage rule already hit the identical wall and
     * resolved it the same way — scale the requirement to what the window can actually deliver, capped
     * at the documented value.
     *
     * So this is a ceiling, not a constant: nodes with room still require the full 5 (`CHROM_SHARP4`,
     * at eight active degrees, gets 5), and only the widest sets relax. What does not relax is the
     * accuracy bar, which means at three attempts the new degree must be answered perfectly — strict,
     * deliberately, since being wrong about the note a node exists to teach is the one thing this
     * criterion is here to catch.
     */
    const val MIN_FOCUS_DEGREE_ATTEMPTS = 5

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
        /**
         * The degree this node introduces, if it introduces one — `M11`'s chromatic nodes each add
         * exactly one. Null for every node that widens by nothing or by more than one, in which case
         * the criterion is reported as met and the other five decide.
         */
        focusDegree: ScaleDegree? = null,
    ): MasteryVerdict {
        val overallAccuracy = if (window.isEmpty()) 0.0 else window.count { it.correct }.toDouble() / window.size

        val attemptsByDegree = window.groupingBy { it.targetLabel }.eachCount()
        val activeDegreeLabels = activeDegrees.map { it.canonicalLabel }
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

        // Reported as met when the node introduces nothing, so the criterion list stays one shape for
        // every recognition node and the progress UI does not have to special-case its absence.
        val focusAttempts = focusDegree?.let { d -> window.count { it.targetLabel == d.canonicalLabel } }
        val focusAccuracy =
            focusDegree?.let { d ->
                val forDegree = window.filter { it.targetLabel == d.canonicalLabel }
                if (forDegree.isEmpty()) 0.0 else forDegree.count { it.correct }.toDouble() / forDegree.size
            }
        // The most the balance rule can deliver for one degree in this window, capped at the spec's 5 -
        // and additionally capped at whatever is left of the window once every OTHER active degree has
        // taken its own DEGREE_COVERAGE floor. Without that third bound, the two criteria can demand
        // more attempts than a 30-item window holds: at exactly ten active degrees (`M11.CHROM_FLAT6`),
        // the uncapped formula asks for 4 - but DEGREE_COVERAGE already requires 3 from each of the
        // other nine, and 9*3 + 4 = 31 > WINDOW_SIZE. No distribution of 30 attempts across ten degrees
        // can satisfy both at once, so the node was mastery-*unmasterable* for every learner, real or
        // synthetic - a bug this criterion's own math hides, since nothing about a single window looks
        // wrong in isolation. Found by DebugMasterySeederTest, which is the first thing that ever tried
        // to drive every node in the graph to genuine mastery rather than to a handful of items. The
        // extra bound only ever tightens this exact wall: every other active-degree count already had
        // slack (confirmed in MasteryEvaluatorTest), so nothing but CHROM_FLAT6 changes value.
        val requiredFocusAttempts =
            if (activeDegreeLabels.isEmpty()) {
                MIN_FOCUS_DEGREE_ATTEMPTS
            } else {
                val othersBudget = activeDegreeLabels.size - 1
                val remainingAfterOthers = WINDOW_SIZE - othersBudget * requiredAttemptsPerDegree
                minOf(
                    MIN_FOCUS_DEGREE_ATTEMPTS,
                    (WINDOW_SIZE * BALANCE_MAX_FREQUENCY_MULTIPLE / activeDegreeLabels.size).toInt(),
                    remainingAfterOthers,
                ).coerceAtLeast(1)
            }
        val focusMet =
            focusDegree == null ||
                (focusAttempts!! >= requiredFocusAttempts && focusAccuracy!! >= MIN_DEGREE_ACCURACY)

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
                MasteryCriterion(
                    MasteryCriterion.Kind.FOCUS_DEGREE,
                    focusMet,
                    focusAccuracy ?: 1.0,
                    MIN_DEGREE_ACCURACY,
                    subject = focusDegree,
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
