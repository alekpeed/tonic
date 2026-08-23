package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.rhythm.RhythmFigure
import com.tonic.core.model.state.MasteryCriterion
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §5.3 and §8 — rhythm mastery, with the rhythmic figure substituted for the
 * scale degree.
 *
 * The substitution is the decision under test. §5.3's "use the existing five criteria unchanged"
 * cannot be taken literally, since three of those five are about degrees and a fourth is about
 * `CADENCE_FADE`; what is preserved is the *shape*, and §8 supplies the unit.
 */
class RhythmMasteryEvaluatorTest {
    private val figures = setOf("ta", "ta-ka-di-mi")

    private fun attempt(
        target: String,
        correct: Boolean,
        response: String = if (correct) target else other(target),
        index: Int = 0,
    ) = Attempt(
        skillId = SkillIds.M3_SUBDIV_RECOG,
        sessionId = 1L,
        itemSeed = index.toLong(),
        axisLevels = emptyMap(),
        targetLabel = target,
        responseLabel = response,
        correct = correct,
        latencyMs = 900L,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 0,
        timbreId = "PURE",
        cadenceFadeLevel = 0,
        timestamp = Instant.EPOCH.plusSeconds(index.toLong()),
    )

    private fun other(target: String) = figures.first { it != target }

    /** A window alternating the two figures, [correctCount] of them right. */
    private fun window(
        size: Int = RhythmMasteryEvaluator.WINDOW_SIZE,
        correctCount: Int = size,
    ) = (0 until size).map { i ->
        attempt(figures.elementAt(i % figures.size), correct = i < correctCount, index = i)
    }

    private fun axes(fade: Int) = mapOf(DifficultyAxis.METRONOME_FADE to fade)

    private fun evaluate(
        window: List<Attempt>,
        fade: Int = MetronomeFadeLevel.MASTERY_MINIMUM.level,
        activeFigures: Set<String> = figures,
    ) = RhythmMasteryEvaluator.evaluate(window, activeFigures, axes(fade))

    private fun met(
        window: List<Attempt>,
        kind: MasteryCriterion.Kind,
        fade: Int = MetronomeFadeLevel.MASTERY_MINIMUM.level,
        activeFigures: Set<String> = figures,
    ) = evaluate(window, fade, activeFigures).criteria.first { it.kind == kind }.met

    @Test
    fun `a clean window at a faded metronome masters`() {
        assertTrue(evaluate(window()).isMastered)
    }

    @Test
    fun `the five criteria keep their shape, with figures where degrees were`() {
        // The mapping §5.3 asks for, asserted as a set rather than described in a comment.
        val kinds = evaluate(window()).criteria.map { it.kind }.toSet()
        assertEquals(
            setOf(
                MasteryCriterion.Kind.WINDOW_COVERAGE,
                MasteryCriterion.Kind.OVERALL_ACCURACY,
                MasteryCriterion.Kind.FIGURE_COVERAGE,
                MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY,
                MasteryCriterion.Kind.CONFUSION_CAP,
                MasteryCriterion.Kind.METRONOME_FADE_MINIMUM,
            ),
            kinds,
        )
        assertTrue(
            kinds.none {
                it == MasteryCriterion.Kind.DEGREE_COVERAGE || it == MasteryCriterion.Kind.CADENCE_FADE_MINIMUM
            },
            "a rhythm node has no degrees and no cadence",
        )
    }

    @Test
    fun `a learner who has never kept time unaided cannot master`() {
        // §5.3 criterion 4, and the reason it exists: without it a learner masters rhythm having never
        // once produced without the metronome under them. The exact counterpart of CADENCE_FADE >= 4.
        for (fade in 0 until MetronomeFadeLevel.MASTERY_MINIMUM.level) {
            assertFalse(evaluate(window(), fade = fade).isMastered, "fade $fade should not master")
            assertFalse(met(window(), MasteryCriterion.Kind.METRONOME_FADE_MINIMUM, fade = fade))
        }
        assertTrue(met(window(), MasteryCriterion.Kind.METRONOME_FADE_MINIMUM))
    }

    @Test
    fun `a figure the learner has barely met does not count as covered`() {
        // Criterion 2, per figure. Twenty-nine attempts on one figure and one on the other is not
        // evidence about the second, however good the overall accuracy looks.
        val lopsided =
            (0 until RhythmMasteryEvaluator.WINDOW_SIZE).map { i ->
                attempt(if (i == 0) "ta-ka-di-mi" else "ta", correct = true, index = i)
            }
        assertFalse(met(lopsided, MasteryCriterion.Kind.FIGURE_COVERAGE))
        assertFalse(evaluate(lopsided).isMastered)
    }

    @Test
    fun `one weak figure blocks mastery even when the total looks fine`() {
        // Criterion 3, and it takes an *unbalanced* window to isolate it at all. With the two figures
        // evenly split fifteen and fifteen, criterion 1's 90% floor caps the misses at three, and three
        // misses on one figure leaves it at exactly 0.80 - so criterion 3 is mathematically implied by
        // criterion 1 and can never fail on its own. MasteryEvaluator's own comment records the same
        // property for the pitch track; it is a genuine feature of the spec's thresholds, not a bug.
        //
        // Where it bites is a figure that appears rarely: twenty-four confident answers on "ta" carry
        // the total to 93% while the learner is at two-thirds on the figure the node exists to teach.
        val window =
            (0 until RhythmMasteryEvaluator.WINDOW_SIZE).map { i ->
                if (i < 24) {
                    attempt("ta", correct = true, index = i)
                } else {
                    attempt("ta-ka-di-mi", correct = i < 28, index = i)
                }
            }
        val accuracy = window.count { it.correct }.toDouble() / window.size
        assertTrue(accuracy >= RhythmMasteryEvaluator.MIN_ACCURACY, "the point is that the total looks fine")
        assertTrue(met(window, MasteryCriterion.Kind.OVERALL_ACCURACY))
        assertTrue(met(window, MasteryCriterion.Kind.FIGURE_COVERAGE), "and that the rare figure is still covered")
        assertFalse(met(window, MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY))
        assertFalse(evaluate(window).isMastered)
    }

    @Test
    fun `one confusion pair dominating the window blocks mastery`() {
        // Criterion 4, unchanged from the pitch track because the matrix is over strings and does not
        // care what they name. Here the learner hears ta-ka-di-mi as ta, repeatedly.
        val window =
            (0 until RhythmMasteryEvaluator.WINDOW_SIZE).map { i ->
                val target = figures.elementAt(i % figures.size)
                if (target == "ta-ka-di-mi" && i % 2 == 1) {
                    attempt(target, correct = false, response = "ta", index = i)
                } else {
                    attempt(target, correct = true, index = i)
                }
            }
        assertFalse(met(window, MasteryCriterion.Kind.CONFUSION_CAP))
    }

    @Test
    fun `a short window cannot certify anything`() {
        assertFalse(evaluate(window(size = 8)).isMastered)
        assertFalse(met(window(size = 8), MasteryCriterion.Kind.WINDOW_COVERAGE))
    }

    @Test
    fun `a node with no figures to discriminate reports both figure criteria met`() {
        // M3.BEAT_FIND and M3.DOWNBEAT. Not a loophole: they are still held to accuracy, window size
        // and the fade minimum, and neither node has a figure a learner could be weak at.
        val beatFinding =
            (0 until RhythmMasteryEvaluator.WINDOW_SIZE).map {
                attempt(
                    "1",
                    correct = true,
                    response = "1",
                    index = it,
                )
            }
        assertTrue(met(beatFinding, MasteryCriterion.Kind.FIGURE_COVERAGE, activeFigures = emptySet()))
        assertTrue(met(beatFinding, MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY, activeFigures = emptySet()))
        assertTrue(evaluate(beatFinding, activeFigures = emptySet()).isMastered)

        // And a bad run still fails, so the vacuous criteria are not carrying anyone.
        val poor =
            (0 until RhythmMasteryEvaluator.WINDOW_SIZE).map {
                attempt("1", correct = it % 2 == 0, response = "2", index = it)
            }
        assertFalse(evaluate(poor, activeFigures = emptySet()).isMastered)
    }

    @Test
    fun `a silent beat is a figure like any other`() {
        // A rest is something a learner can genuinely mistake for a sounded beat, so it is named and
        // held to the same floors rather than being absent from the matrix.
        val withRests = setOf("ta", RhythmFigure.SILENT)
        val window =
            (0 until RhythmMasteryEvaluator.WINDOW_SIZE).map { i ->
                val target = withRests.elementAt(i % withRests.size)
                attempt(target, correct = target == "ta", response = "ta", index = i)
            }
        assertFalse(
            met(window, MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY, activeFigures = withRests),
            "a learner who never hears the rest must not master",
        )
    }
}
