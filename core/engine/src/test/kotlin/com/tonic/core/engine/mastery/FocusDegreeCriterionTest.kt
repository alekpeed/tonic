package com.tonic.core.engine.mastery

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.MasteryCriterion
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/20-PHASE-2-SPEC.md §3's sixth criterion, in the words that motivated it: "without this a learner
 * masters `M11.CHROM_FLAT2` while being at chance on `♭2` specifically, carried by seven confident
 * diatonic answers." These tests build exactly that learner and confirm the criterion refuses them.
 */
class FocusDegreeCriterionTest {
    private val masteredAxes =
        DifficultyAxis.RECOGNITION_AXES.associateWith { 0 } +
            (DifficultyAxis.CADENCE_FADE to CadenceFadeLevel.MASTERY_MINIMUM.level)

    private val window30 = MasteryEvaluator.WINDOW_SIZE

    private val chromaticSet = ScaleDegree.ALL_DIATONIC + ScaleDegree(2, -1)
    private val focus = ScaleDegree(2, -1)

    private fun attempt(
        degree: ScaleDegree,
        correct: Boolean,
    ) = Attempt(
        skillId = SkillIds.M11_CHROM_FLAT2,
        sessionId = 1L,
        itemSeed = 1L,
        axisLevels = masteredAxes,
        targetLabel = degree.canonicalLabel,
        responseLabel = if (correct) degree.canonicalLabel else "1",
        correct = correct,
        latencyMs = 900L,
        replayCount = 0,
        keyPitchClass = 0,
        targetMidi = 60 + degree.semitoneOffset(Mode.MAJOR),
        timbreId = "PURE",
        cadenceFadeLevel = CadenceFadeLevel.MASTERY_MINIMUM.level,
        timestamp = Instant.EPOCH,
    )

    /** 30 attempts: [focusCorrect] of [focusAttempts] on the new degree, everything else right. */
    private fun window(
        focusAttempts: Int,
        focusCorrect: Int,
    ): List<Attempt> {
        val focusItems =
            (0 until focusAttempts).map { attempt(focus, correct = it < focusCorrect) }
        val diatonic = ScaleDegree.ALL_DIATONIC.toList()
        val rest =
            (0 until (30 - focusAttempts)).map { attempt(diatonic[it % diatonic.size], correct = true) }
        return focusItems + rest
    }

    private fun verdict(
        focusAttempts: Int,
        focusCorrect: Int,
    ) = MasteryEvaluator.evaluate(
        window(focusAttempts, focusCorrect),
        chromaticSet,
        masteredAxes,
        focusDegree = focus,
    )

    @Test
    fun `a learner at chance on the new degree is refused, however good the rest is`() {
        // The spec's exact scenario: everything else perfect, the new note a coin flip.
        val v = verdict(focusAttempts = 4, focusCorrect = 2)
        assertFalse(v.isMastered)
        assertTrue(
            MasteryCriterion.Kind.FOCUS_DEGREE in v.criteria.filter { !it.met }.map { it.kind },
            "the focus criterion must be among what blocks, not silently satisfied",
        )
        // It is not the *only* thing blocking, and that is expected: docs/03-CURRICULUM.md §5.5's
        // weakest-degree rule holds every active degree to 80%, so a 50% new degree trips that too. The
        // overlap is real and harmless on this scenario. What the focus criterion adds that the weakest-
        // degree rule cannot express is coverage - see the zero-attempts test below, which the weakest-
        // degree rule passes because it ignores degrees with no attempts at all.
        assertTrue(
            MasteryCriterion.Kind.WEAKEST_DEGREE_ACCURACY in v.criteria.filter { !it.met }.map { it.kind },
        )
    }

    @Test
    fun `never being asked the new degree is not evidence of knowing it`() {
        val v = verdict(focusAttempts = 0, focusCorrect = 0)
        assertFalse(v.isMastered, "zero attempts on the node's own subject cannot certify it")
        assertFalse(v.criteria.first { it.kind == MasteryCriterion.Kind.FOCUS_DEGREE }.met)
    }

    @Test
    fun `answering the new degree correctly, often enough, satisfies it`() {
        val v = verdict(focusAttempts = 5, focusCorrect = 5)
        assertTrue(
            v.criteria.first { it.kind == MasteryCriterion.Kind.FOCUS_DEGREE }.met,
            "5 of 5 on the new degree must satisfy the criterion",
        )
    }

    @Test
    fun `a node that introduces nothing reports the criterion met and is judged on the other five`() {
        // CHROM_FULL and every M2/M10 node pass null, and must not be blocked by a criterion that has
        // no subject.
        val v =
            MasteryEvaluator.evaluate(
                window(focusAttempts = 0, focusCorrect = 0),
                chromaticSet,
                masteredAxes,
                focusDegree = null,
            )
        assertTrue(v.criteria.first { it.kind == MasteryCriterion.Kind.FOCUS_DEGREE }.met)
    }

    @Test
    fun `at the full chromatic set it blocks a case no other criterion catches`() {
        // Where this criterion earns its place. §5.5's general coverage rule scales to what the window
        // can hold - at twelve active degrees that is 2 attempts each - and its accuracy rule ignores
        // the new degree entirely when it is answered correctly. So a learner with 2 correct attempts on
        // ♭2 satisfies all five older criteria. Two attempts is not evidence of hearing a note that was
        // introduced ten minutes ago; the focus criterion holds the node's own subject to a higher
        // coverage bar than an already-mastered neighbor, and refuses.
        val full = ScaleDegree.ALL_CHROMATIC
        val others = (full - focus).toList()
        val window =
            List(2) { attempt(focus, correct = true) } +
                List(window30 - 2) { attempt(others[it % others.size], correct = true) }

        val without = MasteryEvaluator.evaluate(window, full, masteredAxes, focusDegree = null)
        assertTrue(
            without.criteria.all { it.met },
            "the older criteria must all pass here, or this test is not isolating anything: " +
                without.criteria.filter { !it.met }.map { it.kind },
        )

        val judged = MasteryEvaluator.evaluate(window, full, masteredAxes, focusDegree = focus)
        assertFalse(judged.isMastered, "2 attempts on the degree the node exists to teach cannot certify it")
        assertEquals(
            listOf(MasteryCriterion.Kind.FOCUS_DEGREE),
            judged.criteria.filter { !it.met }.map { it.kind },
            "and it must be this criterion alone doing it",
        )
    }

    @Test
    fun `the criterion is reported alongside the other five, not folded into them`() {
        // docs/03-CURRICULUM.md §5.5's reason for reporting criteria individually: the progress UI shows
        // what is actually blocking, "far more useful than a percentage bar".
        val kinds = verdict(5, 5).criteria.map { it.kind }
        assertEquals(kinds.size, kinds.toSet().size, "each criterion appears once")
        assertTrue(MasteryCriterion.Kind.FOCUS_DEGREE in kinds)
        assertTrue(MasteryCriterion.Kind.OVERALL_ACCURACY in kinds)
        assertTrue(MasteryCriterion.Kind.CADENCE_FADE_MINIMUM in kinds)
    }
}
