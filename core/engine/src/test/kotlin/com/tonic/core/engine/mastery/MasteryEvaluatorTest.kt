package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.MasteryCriterion
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 4 acceptance: all five criteria tested, each shown to block on its own -
 * except CONFUSION_CAP, which docs/03-CURRICULUM.md §5.5's own thresholds (90% overall accuracy, 15% of
 * the whole 30-item window) make mathematically incapable of blocking independently of OVERALL_ACCURACY.
 * See the two CONFUSION_CAP tests below for the computation check and the redundancy proof.
 */
class MasteryEvaluatorTest {
    private val activeDegrees = setOf(ScaleDegree(1), ScaleDegree(3), ScaleDegree(5))
    private val masteredAxes = mapOf(DifficultyAxis.CADENCE_FADE to 4)

    /** A clean window that satisfies all five criteria - the baseline every "one criterion fails" test perturbs. */
    private fun perfectWindow(): List<Attempt> =
        (1..30).map { i ->
            val degree = listOf("1", "3", "5")[i % 3]
            attempt(target = degree, response = degree, correct = true)
        }

    private fun attempt(
        target: String,
        response: String?,
        correct: Boolean,
    ) = Attempt(
        skillId = SkillIds.M2_DEG_SET_1,
        sessionId = 1L,
        itemSeed = 1L,
        axisLevels = masteredAxes,
        targetLabel = target,
        responseLabel = response,
        correct = correct,
        latencyMs = 500,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 60,
        timbreId = "PURE",
        cadenceFadeLevel = 4,
        timestamp = Instant.EPOCH,
    )

    @Test
    fun `a clean window meeting every criterion is mastered`() {
        val verdict = MasteryEvaluator.evaluate(perfectWindow(), activeDegrees, masteredAxes)
        assertTrue(verdict.isMastered)
        assertEquals(null, verdict.blockingCriterion)
    }

    @Test
    fun `overall accuracy below 90 percent blocks mastery on its own`() {
        val window = perfectWindow().toMutableList()
        // Flip enough correct answers to incorrect to drop below 90% while keeping the other 4 criteria intact.
        for (i in 0 until 6) {
            window[i] = window[i].copy(correct = false, responseLabel = "different-but-still-a-valid-label")
        }
        val verdict = MasteryEvaluator.evaluate(window, activeDegrees, masteredAxes)
        assertFalse(verdict.isMastered)
        assertEquals(MasteryCriterion.Kind.OVERALL_ACCURACY, verdict.blockingCriterion?.kind)
    }

    @Test
    fun `missing attempts for an active degree blocks on degree coverage alone`() {
        // Only degrees 1 and 3 appear - degree 5 never does, so it fails the >=5-attempts-per-degree criterion.
        val window =
            (1..30).map { i ->
                attempt(
                    target =
                        if (i % 2 ==
                            0
                        ) {
                            "1"
                        } else {
                            "3"
                        },
                    response = if (i % 2 == 0) "1" else "3",
                    correct = true,
                )
            }
        val verdict = MasteryEvaluator.evaluate(window, activeDegrees, masteredAxes)
        assertFalse(verdict.isMastered)
        assertEquals(MasteryCriterion.Kind.DEGREE_COVERAGE, verdict.blockingCriterion?.kind)
    }

    @Test
    fun `one weak degree below 80 percent blocks on its own even with high overall accuracy`() {
        val window = perfectWindow().toMutableList()
        // Degree "5" appears 10 times (indices 2,5,8,...); make 3 of those wrong -> 70% for "5" specifically,
        // while overall accuracy across all 30 stays at 27/30 = 90%, right at the overall-accuracy floor.
        val fiveIndices = window.indices.filter { window[it].targetLabel == "5" }
        for (i in fiveIndices.take(3)) window[i] = window[i].copy(correct = false, responseLabel = "4")
        val verdict = MasteryEvaluator.evaluate(window, activeDegrees, masteredAxes)
        assertFalse(verdict.isMastered)
        assertEquals(MasteryCriterion.Kind.WEAKEST_DEGREE_ACCURACY, verdict.blockingCriterion?.kind)
    }

    @Test
    fun `confusion cap is computed as a share of the whole window, exactly as docs 03-CURRICULUM 5-5 specifies`() {
        // 5 of "1"'s 10 attempts answered "3" -> 5/30 = 16.7% of the *whole window*, above the 15% cap.
        // This necessarily also wrecks overall accuracy (25/30 = 83.3% < 90%) and "1"'s own accuracy
        // (5/10 = 50% < 80%) - see the next test for why CONFUSION_CAP can never be the *sole* blocker.
        val window =
            (1..5).map { attempt(target = "1", response = "1", correct = true) } +
                (1..5).map { attempt(target = "1", response = "3", correct = false) } +
                (1..10).map { attempt(target = "3", response = "3", correct = true) } +
                (1..10).map { attempt(target = "5", response = "5", correct = true) }
        val verdict = MasteryEvaluator.evaluate(window, activeDegrees, masteredAxes)
        val confusionCriterion = verdict.criteria.single { it.kind == MasteryCriterion.Kind.CONFUSION_CAP }
        assertFalse(confusionCriterion.met, "5/30 = 16.7% should exceed the 15% cap")
        assertEquals(5.0 / 30.0, confusionCriterion.measuredValue)
        assertFalse(verdict.isMastered)
    }

    @Test
    fun `confusion cap can never be the sole blocking criterion, given the spec's own 90 and 15 percent thresholds`() {
        // A real, documented property of docs/03-CURRICULUM.md §5.5's literal numbers, not a gap in this
        // implementation: over any 30-item window, criterion 1 (>=90% overall accuracy) already caps
        // total wrong answers at 3 - and no single confusion pair's count can exceed the total wrong
        // count, so a pair can never reach the 4.5 (15% of 30) needed to violate criterion 4. Whenever
        // OVERALL_ACCURACY holds, CONFUSION_CAP is mathematically guaranteed to hold too. Demonstrated
        // here directly: 3 wrong answers, all landing on the same pair (the worst case for concentrating
        // "confusion" into as few cells as possible), still isn't enough to trip it.
        val window = perfectWindow().toMutableList()
        val oneIndices = window.indices.filter { window[it].targetLabel == "1" }
        for (i in oneIndices.take(3)) window[i] = window[i].copy(correct = false, responseLabel = "3")
        val verdict = MasteryEvaluator.evaluate(window, activeDegrees, masteredAxes)
        val overallAccuracy = verdict.criteria.single { it.kind == MasteryCriterion.Kind.OVERALL_ACCURACY }
        val confusionCriterion = verdict.criteria.single { it.kind == MasteryCriterion.Kind.CONFUSION_CAP }
        assertTrue(overallAccuracy.met, "27/30 = 90% should still clear the overall-accuracy floor")
        assertTrue(confusionCriterion.met, "3/30 = 10% should still clear the 15% confusion cap")
    }

    @Test
    fun `cadence fade below L4 blocks mastery on its own - the criterion that matters`() {
        val lowFadeAxes = mapOf(DifficultyAxis.CADENCE_FADE to 2)
        val window = perfectWindow().map { it.copy(axisLevels = lowFadeAxes, cadenceFadeLevel = 2) }
        val verdict = MasteryEvaluator.evaluate(window, activeDegrees, lowFadeAxes)
        assertFalse(verdict.isMastered)
        assertEquals(MasteryCriterion.Kind.CADENCE_FADE_MINIMUM, verdict.blockingCriterion?.kind)
    }

    @Test
    fun `an empty window is unambiguously not mastered`() {
        val verdict = MasteryEvaluator.evaluate(emptyList(), activeDegrees, masteredAxes)
        assertFalse(verdict.isMastered)
    }
}
