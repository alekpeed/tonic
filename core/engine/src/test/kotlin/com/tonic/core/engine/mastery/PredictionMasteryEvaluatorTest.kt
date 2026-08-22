package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.MasteryCriterion
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `M12` mastery — docs/20-PHASE-2-SPEC.md §3 ("d-prime ≥ 2.0 over a rolling 30 at `PREDICT_GAP` ≥ 2,
 * plus overall accuracy ≥ 85%") and §7's simulation 7, the always-`MATCHED` responder.
 */
class PredictionMasteryEvaluatorTest {
    private val matched = AnswerAlphabet.MatchDirection.MATCHED
    private val low = AnswerAlphabet.MatchDirection.TOO_LOW
    private val high = AnswerAlphabet.MatchDirection.TOO_HIGH

    private fun axes(gap: Int) = mapOf(DifficultyAxis.PREDICT_GAP to gap, DifficultyAxis.PREDICT_DEVIATION to 0)

    private fun attempt(
        index: Int,
        target: String,
        response: String,
    ) = Attempt(
        skillId = SkillIds.M12_PREDICT_DIATONIC,
        sessionId = 1L,
        itemSeed = index.toLong(),
        axisLevels = axes(2),
        targetLabel = target,
        responseLabel = response,
        correct = target == response,
        latencyMs = 1_200L,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 64,
        timbreId = "PURE",
        cadenceFadeLevel = 0,
        timestamp = Instant.EPOCH,
    )

    /** 30 items, half matching, answered by [answer] given the truth. */
    private fun window(answer: (String, Int) -> String): List<Attempt> {
        val truths =
            (0 until 30).map {
                if (it % 2 == 0) {
                    matched
                } else if (it % 4 == 1) {
                    low
                } else {
                    high
                }
            }
        return truths.mapIndexed { i, truth -> attempt(i, truth, answer(truth, i)) }
    }

    @Test
    fun `a fluent audiator at a long gap is certified`() {
        val verdict = PredictionMasteryEvaluator.evaluate(window { truth, _ -> truth }, axes(gap = 2))
        assertTrue(verdict.isMastered, "unmet: ${verdict.criteria.filter { !it.met }.map { it.kind }}")
    }

    @Test
    fun `an always-MATCHED responder is refused - the spec's simulation 7`() {
        // §7's required simulation, and the reason §8.1 decision 3 could be decided on grounds other
        // than response bias: d-prime already defeats this. The responder scores 50% by construction,
        // and its hit rate equals its false-alarm rate, so z(hit) - z(fa) is 0.
        val verdict = PredictionMasteryEvaluator.evaluate(window { _, _ -> matched }, axes(gap = 3))
        assertFalse(verdict.isMastered)

        val dPrime = verdict.criteria.first { it.kind == MasteryCriterion.Kind.D_PRIME }
        assertFalse(dPrime.met)
        assertTrue(
            dPrime.measuredValue < 0.5,
            "an always-MATCHED responder measured d-prime ${dPrime.measuredValue}; it should be ~0",
        )
    }

    @Test
    fun `a strategic guesser who answers MATCHED most of the time is also refused`() {
        // The subtler version: 75% MATCHED, 25% split between the directions, no listening at all. Raw
        // accuracy climbs above chance; sensitivity does not.
        val random = Random(42)
        val verdict =
            PredictionMasteryEvaluator.evaluate(
                window { _, _ ->
                    when {
                        random.nextInt(4) < 3 -> matched
                        random.nextBoolean() -> low
                        else -> high
                    }
                },
                axes(gap = 2),
            )
        assertFalse(verdict.isMastered)
        assertFalse(verdict.criteria.first { it.kind == MasteryCriterion.Kind.D_PRIME }.met)
    }

    @Test
    fun `mastery is refused at a short gap however accurate the learner is`() {
        // The criterion that makes this module about audiation rather than about echo. At a 1-second
        // gap the sounded note can be judged against a still-ringing memory of the cadence.
        val verdict = PredictionMasteryEvaluator.evaluate(window { truth, _ -> truth }, axes(gap = 1))
        assertFalse(verdict.isMastered, "a perfect learner at gap level 1 must not be certified")
        assertEquals(
            listOf(MasteryCriterion.Kind.PREDICT_GAP_MINIMUM),
            verdict.criteria.filter { !it.met }.map { it.kind },
            "and the gap must be the only thing blocking, or this test proves nothing about it",
        )
    }

    @Test
    fun `the accuracy bar is 85 percent, not 90`() {
        // docs/20-PHASE-2-SPEC.md §3 states 85% for M12 where M9 is held to 90%. Prediction has an
        // irreducible noise floor - even a fluent audiator misses a 30-cent bend sometimes - and §2.3
        // says the hardest deviation level must not become a mastery gate.
        assertEquals(0.85, PredictionMasteryEvaluator.MIN_ACCURACY)
        assertEquals(0.90, BinaryMasteryEvaluator.MIN_ACCURACY)

        // 4 wrong out of 30 is 86.7%: above M12's bar, below M9's.
        val verdict =
            PredictionMasteryEvaluator.evaluate(
                window { truth, i -> if (i < 4 && truth != matched) matched else truth },
                axes(gap = 2),
            )
        val accuracy = verdict.criteria.first { it.kind == MasteryCriterion.Kind.OVERALL_ACCURACY }
        assertTrue(accuracy.met, "measured ${accuracy.measuredValue} against a bar of ${accuracy.requiredValue}")
    }

    @Test
    fun `naming the direction wrong costs accuracy but not sensitivity`() {
        // The shape of a real intermediate learner, and the reason d-prime is computed on the collapsed
        // answer: someone who reliably hears *that* it was wrong but guesses which way has genuinely
        // good mismatch sensitivity and genuinely poor accuracy, and the verdict should say both.
        val verdict =
            PredictionMasteryEvaluator.evaluate(
                window { truth, _ -> if (truth == matched) matched else low },
                axes(gap = 2),
            )
        val dPrime = verdict.criteria.first { it.kind == MasteryCriterion.Kind.D_PRIME }
        assertTrue(dPrime.met, "every mismatch was detected; d-prime measured ${dPrime.measuredValue}")
        assertFalse(
            verdict.criteria.first { it.kind == MasteryCriterion.Kind.OVERALL_ACCURACY }.met,
            "half the directions were wrong, so accuracy must not pass",
        )
        assertFalse(verdict.isMastered)
    }

    @Test
    fun `a short window cannot certify anybody`() {
        val verdict =
            PredictionMasteryEvaluator.evaluate(
                window { truth, _ -> truth }.take(8),
                axes(gap = 3),
            )
        assertFalse(verdict.isMastered)
        assertFalse(verdict.criteria.first { it.kind == MasteryCriterion.Kind.WINDOW_COVERAGE }.met)
    }
}
