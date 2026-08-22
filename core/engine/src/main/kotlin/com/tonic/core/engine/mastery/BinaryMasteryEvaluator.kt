package com.tonic.core.engine.mastery

import com.tonic.core.engine.staircase.DPrime
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.model.state.MasteryVerdict

/**
 * Mastery for a **binary-answer** node — `M9.*` today, `M12.*` at Stage 2.6. Separate from
 * [MasteryEvaluator] on purpose: docs/20-PHASE-2-SPEC.md §4 says the five-criteria structure is
 * explicitly not changing, and four of those five are about scale degrees, which a two-choice task
 * does not have. Forcing this through the same object would have meant weakening criteria that `M2`
 * depends on.
 *
 * Two criteria, per docs/20-PHASE-2-SPEC.md §3: ≥90% accuracy over a rolling 30, and d-prime ≥ 2.0.
 *
 * Both are load-bearing, but not equally, and it is worth being precise about why rather than
 * repeating "accuracy alone is insufficient" as a slogan. Against a *balanced* item set — which
 * `M9ItemGenerator` enforces — the 90% bar already rejects a pure guesser, who scores 50%. What
 * d-prime adds is protection when a window happens to be lopsided: 20 major items against 10 minor
 * ones lets an always-"major" responder reach 67%, and a strong enough bias combined with partial
 * skill can push raw accuracy up while sensitivity stays poor. d-prime is invariant to that, because
 * it measures the *separation* between hit and false-alarm rates rather than the count of correct
 * answers. It is the cheaper, stricter guard, and it matters more at `M12`, where matched and
 * unmatched items need not be evenly split at all.
 */
object BinaryMasteryEvaluator {
    const val WINDOW_SIZE = 30
    const val MIN_ACCURACY = 0.90
    const val MIN_D_PRIME = 2.0

    /**
     * [window] must already be the last (up to) [WINDOW_SIZE] non-warm-up, non-abandoned attempts for
     * the node — the same filtering contract [MasteryEvaluator] holds the caller to.
     *
     * [signalLabel] names which of the two answers counts as "signal present" for the d-prime
     * computation. The choice is arbitrary and does not affect the result's magnitude: swapping signal
     * for noise flips the sign of both z-scores and leaves their difference unchanged.
     */
    fun evaluate(
        window: List<Attempt>,
        signalLabel: String,
    ): MasteryVerdict {
        val accuracy = if (window.isEmpty()) 0.0 else window.count { it.correct }.toDouble() / window.size

        val signalTrials = window.count { it.targetLabel == signalLabel }
        val noiseTrials = window.size - signalTrials
        val hits = window.count { it.targetLabel == signalLabel && it.responseLabel == signalLabel }
        // A false alarm is answering "signal" when the item was noise. A skipped or abandoned item has
        // a null response and is neither, which is correct: it was never scored.
        val falseAlarms = window.count { it.targetLabel != signalLabel && it.responseLabel == signalLabel }

        val dPrime = DPrime.compute(hits, signalTrials, falseAlarms, noiseTrials)

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
                    kind = MasteryCriterion.Kind.D_PRIME,
                    met = dPrime >= MIN_D_PRIME,
                    measuredValue = dPrime,
                    requiredValue = MIN_D_PRIME,
                ),
            ),
        )
    }
}
