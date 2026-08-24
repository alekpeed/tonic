package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.MasteryCriterion
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stage 2.2's first acceptance criterion — "d-prime scoring correct" — plus simulations 1 and 2 of
 * docs/20-PHASE-2-SPEC.md: a competent mode-identification learner masters `M9`, and a mode-deaf
 * learner at chance is never certified.
 */
class BinaryMasteryEvaluatorTest {
    private val major = "MAJOR"
    private val minor = "MINOR"

    /**
     * Simulated responder over a balanced window. [sensitivity] is the probability of answering
     * correctly when the learner is actually listening; [biasTowardMajor] is the probability of falling
     * back on "major" instead, which is how a learner who cannot hear the difference behaves.
     */
    private fun window(
        size: Int = BinaryMasteryEvaluator.WINDOW_SIZE,
        sensitivity: Double,
        biasTowardMajor: Double = 0.5,
        seed: Long = 99L,
    ): List<Attempt> {
        val rng = Random(seed)
        return (0 until size).map { i ->
            val target = if (i % 2 == 0) major else minor
            val response =
                if (rng.nextDouble() < sensitivity) {
                    target
                } else if (rng.nextDouble() < biasTowardMajor) {
                    major
                } else {
                    minor
                }
            attempt(target, response)
        }
    }

    private fun attempt(
        target: String,
        response: String?,
    ) = Attempt(
        skillId = SkillIds.M9_MODE_ID_TRIAD,
        sessionId = 1L,
        itemSeed = 0L,
        axisLevels = emptyMap(),
        targetLabel = target,
        responseLabel = response,
        correct = response == target,
        latencyMs = 0L,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 60,
        timbreId = "PURE",
        cadenceFadeLevel = 0,
        timestamp = Instant.EPOCH,
    )

    @Test
    fun `simulation 1 - a competent mode-identification learner is certified`() {
        val verdict = BinaryMasteryEvaluator.evaluate(window(sensitivity = 0.97), signalLabel = minor)

        assertTrue(
            verdict.isMastered,
            "a learner who hears the distinction must pass: ${verdict.criteria.map { it.kind to it.measuredValue }}",
        )
        assertTrue(verdict.criteria.single { it.kind == MasteryCriterion.Kind.D_PRIME }.measuredValue >= 2.0)
    }

    @Test
    fun `simulation 2 - a mode-deaf learner who always answers major is never certified`() {
        // The spec's "biased responder" case. Every item answered MAJOR: half are right by construction,
        // so raw accuracy reads 50% - and d-prime reads *zero*, because the hit rate and the false-alarm
        // rate are identical. Two independent reasons to refuse, which is the point of carrying both.
        val alwaysMajor =
            (0 until BinaryMasteryEvaluator.WINDOW_SIZE).map { i ->
                attempt(if (i % 2 == 0) major else minor, major)
            }

        val verdict = BinaryMasteryEvaluator.evaluate(alwaysMajor, signalLabel = minor)

        assertFalse(verdict.isMastered)
        val dPrime = verdict.criteria.single { it.kind == MasteryCriterion.Kind.D_PRIME }
        assertFalse(dPrime.met)
        assertEquals(0.0, dPrime.measuredValue, absoluteTolerance = 1e-9, message = "pure bias is zero sensitivity")
    }

    @Test
    fun `a coin-flipping guesser is never certified either`() {
        val verdict = BinaryMasteryEvaluator.evaluate(window(sensitivity = 0.0), signalLabel = minor)

        assertFalse(verdict.isMastered)
        assertTrue(
            verdict.criteria.single { it.kind == MasteryCriterion.Kind.D_PRIME }.measuredValue < 1.0,
            "chance responding must not look sensitive",
        )
    }

    @Test
    fun `d-prime does not depend on which answer is called the signal`() {
        // Swapping signal for noise flips the sign of both z-scores and leaves their difference
        // unchanged. Worth pinning, because the choice of signal label is arbitrary and a reader should
        // be able to see that it does not quietly matter.
        val w = window(sensitivity = 0.93)
        val asMinor = BinaryMasteryEvaluator.evaluate(w, signalLabel = minor)
        val asMajor = BinaryMasteryEvaluator.evaluate(w, signalLabel = major)

        assertEquals(
            asMinor.criteria.single { it.kind == MasteryCriterion.Kind.D_PRIME }.measuredValue,
            asMajor.criteria.single { it.kind == MasteryCriterion.Kind.D_PRIME }.measuredValue,
            absoluteTolerance = 1e-9,
        )
    }

    @Test
    fun `a short window is never certified, however clean it looks`() {
        // Five perfect answers is five coin flips away from meaningless. WINDOW_COVERAGE exists so a
        // lucky streak at the start of a node cannot certify anyone.
        val perfectButShort =
            (0 until 5).map { i ->
                attempt(
                    if (i % 2 ==
                        0
                    ) {
                        major
                    } else {
                        minor
                    },
                    if (i % 2 == 0) major else minor,
                )
            }

        val verdict = BinaryMasteryEvaluator.evaluate(perfectButShort, signalLabel = minor)

        assertFalse(verdict.isMastered)
        assertEquals(MasteryCriterion.Kind.WINDOW_COVERAGE, verdict.blockingCriterion?.kind)
    }

    @Test
    fun `a skipped item counts as neither a hit nor a false alarm`() {
        // A null response was never scored (docs/06-AUDIO-ENGINE.md §8), so it must not quietly inflate
        // either rate - it would bias d-prime in whichever direction the skipped items happened to sit.
        val withSkips =
            (0 until BinaryMasteryEvaluator.WINDOW_SIZE).map { i ->
                val target = if (i % 2 == 0) major else minor
                if (i % 10 == 0) attempt(target, null) else attempt(target, target)
            }

        val verdict = BinaryMasteryEvaluator.evaluate(withSkips, signalLabel = minor)
        val dPrime = verdict.criteria.single { it.kind == MasteryCriterion.Kind.D_PRIME }.measuredValue

        assertTrue(dPrime > 2.0, "answers that were given were all correct, so sensitivity is high: $dPrime")
    }
}
