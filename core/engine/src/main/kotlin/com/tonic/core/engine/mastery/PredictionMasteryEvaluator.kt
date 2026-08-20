package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.model.state.MasteryVerdict

/**
 * Mastery for `M12.*` — docs/20-PHASE-2-SPEC.md §3: "d-prime ≥ 2.0 over a rolling 30 at
 * `PREDICT_GAP` ≥ 2, plus overall accuracy ≥ 85%."
 *
 * Built on [BinaryMasteryEvaluator] rather than beside it, because the d-prime machinery is identical
 * and duplicating it would let the two drift. What differs is two things the spec states explicitly
 * and neither of which `M9` has:
 *
 * **The accuracy bar is 85%, not 90%.** Prediction is a harder task with an irreducible noise floor —
 * even a fluent audiator will miss a 30-cent bend some of the time — and holding it to `M9`'s bar
 * would make the advanced deviation levels a mastery blocker, which §2.3 explicitly says they must
 * not be.
 *
 * **The gap level is a criterion in its own right.** This is the direct counterpart of
 * docs/03-CURRICULUM.md §5.5's cadence-fade criterion, and it exists for the same reason: without it,
 * a learner masters the node at a 1-second gap, which is short enough to judge the sounded note
 * against a still-ringing memory of the cadence rather than against anything they generated. That is
 * not audiation, and it is the one thing this module exists to train.
 *
 * **d-prime is computed on the collapsed answer.** The three-button layout (§8.1 decision 3) records
 * `MATCHED` / `TOO_LOW` / `TOO_HIGH`, but signal-detection theory needs two categories. Collapsing to
 * matched-versus-not is the honest mapping: the "signal" being detected is a mismatch, and its
 * direction is a separate, finer judgment. A learner who reliably detects mismatches but names the
 * direction wrong half the time has a good d-prime and a poor accuracy, which is exactly the shape
 * their skill actually has.
 */
object PredictionMasteryEvaluator {
    const val WINDOW_SIZE = BinaryMasteryEvaluator.WINDOW_SIZE
    const val MIN_ACCURACY = 0.85
    const val MIN_D_PRIME = BinaryMasteryEvaluator.MIN_D_PRIME
    const val MIN_GAP_LEVEL = 2

    /**
     * [window] must already be the last (up to) [WINDOW_SIZE] non-warm-up, non-abandoned attempts for
     * the node — the same contract the other two evaluators hold the caller to.
     */
    fun evaluate(
        window: List<Attempt>,
        axes: Map<DifficultyAxis, Int>,
    ): MasteryVerdict {
        val accuracy = if (window.isEmpty()) 0.0 else window.count { it.correct }.toDouble() / window.size
        val gapLevel = axes[DifficultyAxis.PREDICT_GAP] ?: 0

        // Collapsed to the binary d-prime needs - see the class doc. MATCHED is the noise condition
        // and any directional answer is "mismatch detected"; which one is called signal does not
        // change the magnitude, only the sign of both z-scores.
        val collapsed =
            window.map { attempt ->
                attempt.copy(
                    targetLabel = collapse(attempt.targetLabel),
                    responseLabel = attempt.responseLabel?.let(::collapse),
                )
            }
        val dPrimeVerdict = BinaryMasteryEvaluator.evaluate(collapsed, signalLabel = MISMATCH)
        val dPrime = dPrimeVerdict.criteria.first { it.kind == MasteryCriterion.Kind.D_PRIME }

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
                dPrime,
                MasteryCriterion(
                    kind = MasteryCriterion.Kind.PREDICT_GAP_MINIMUM,
                    met = gapLevel >= MIN_GAP_LEVEL,
                    measuredValue = gapLevel.toDouble(),
                    requiredValue = MIN_GAP_LEVEL.toDouble(),
                ),
            ),
        )
    }

    /** `TOO_LOW`/`TOO_HIGH` -> [MISMATCH]; `MATCHED` stays itself. */
    private fun collapse(label: String): String =
        if (AnswerAlphabet.MatchDirection.matchedVsNot(label)) AnswerAlphabet.MatchDirection.MATCHED else MISMATCH

    private const val MISMATCH = "MISMATCH"
}
