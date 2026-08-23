package com.tonic.core.model.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MasteryVerdictTest {
    private fun criterion(
        kind: MasteryCriterion.Kind,
        met: Boolean,
    ) = MasteryCriterion(kind, met, measuredValue = if (met) 1.0 else 0.0, requiredValue = 1.0)

    @Test
    fun `mastered only when all criteria are met`() {
        val allMet =
            MasteryVerdict(MasteryCriterion.Kind.entries.map { criterion(it, met = true) })
        assertTrue(allMet.isMastered)
        assertNull(allMet.blockingCriterion)
    }

    @Test
    fun `one unmet criterion blocks mastery and is reported first`() {
        val kinds = MasteryCriterion.Kind.entries
        val criteria =
            kinds.mapIndexed { index, kind -> criterion(kind, met = index != 2) }
        val verdict = MasteryVerdict(criteria)
        assertFalse(verdict.isMastered)
        assertEquals(kinds[2], verdict.blockingCriterion?.kind)
    }

    @Test
    fun `the five degree-based criteria are intact, and the binary-node ones are additive`() {
        // The guard that makes adding a mastery criterion a decision rather than an accident, and it
        // has now caught three - FOCUS_DEGREE in Stage 2.5, PREDICT_GAP_MINIMUM in Stage 2.6, and
        // rhythm's three in Phase 4, all added without this list being updated and all failing here
        // until they were named. That is the test working, so it is *extended* rather than loosened:
        // every kind must still be accounted for by name, in one of four groups, with a reason.
        //
        // Group 1, docs/03-CURRICULUM.md §5.5's five: unchanged, and unchangeable -
        // docs/20-PHASE-2-SPEC.md §4 forbids altering the M2 mastery structure, which is why every
        // Phase 2 addition is a separate evaluator rather than a loosening of this one.
        //
        // Group 2, the binary-node criteria (M9, M12): those nodes have no scale degrees and so cannot
        // use four of the original five.
        //
        // Group 3, the node-specific additions each spec'd for exactly one module: FOCUS_DEGREE holds
        // M11's newly-introduced chromatic degree to its own sample (§3), and PREDICT_GAP_MINIMUM is
        // M12's counterpart of the cadence-fade rule (§3). Both apply to their own module only and
        // neither touches M2's.
        //
        // Group 4, rhythm's three (docs/40-PHASE-4-SPEC.md §5.3 and §8). §5.3 says M3's recognition
        // nodes use the five existing criteria "unchanged", which cannot be taken literally: three of
        // group 1 are about scale degrees and a fourth is about CADENCE_FADE, and a rhythm node has
        // neither. What is preserved is the shape, with §8's rhythmic figure substituted for the
        // degree one for one - FIGURE_COVERAGE for DEGREE_COVERAGE, WEAKEST_FIGURE_ACCURACY for
        // WEAKEST_DEGREE_ACCURACY, METRONOME_FADE_MINIMUM for CADENCE_FADE_MINIMUM. OVERALL_ACCURACY
        // and CONFUSION_CAP are reused unchanged, because neither mentions a degree: the confusion
        // matrix is over strings and does not care what they name. Group 1 is untouched, which is what
        // docs/20-PHASE-2-SPEC.md §4 requires.
        val degreeBased =
            listOf(
                MasteryCriterion.Kind.OVERALL_ACCURACY,
                MasteryCriterion.Kind.DEGREE_COVERAGE,
                MasteryCriterion.Kind.WEAKEST_DEGREE_ACCURACY,
                MasteryCriterion.Kind.CONFUSION_CAP,
                MasteryCriterion.Kind.CADENCE_FADE_MINIMUM,
            )
        assertEquals(5, degreeBased.size)
        assertTrue(MasteryCriterion.Kind.entries.containsAll(degreeBased))
        assertTrue(MasteryCriterion.Kind.entries.contains(MasteryCriterion.Kind.CADENCE_FADE_MINIMUM))

        val binaryNode = setOf(MasteryCriterion.Kind.WINDOW_COVERAGE, MasteryCriterion.Kind.D_PRIME)
        val nodeSpecific =
            setOf(MasteryCriterion.Kind.FOCUS_DEGREE, MasteryCriterion.Kind.PREDICT_GAP_MINIMUM)
        val rhythm =
            setOf(
                MasteryCriterion.Kind.FIGURE_COVERAGE,
                MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY,
                MasteryCriterion.Kind.METRONOME_FADE_MINIMUM,
            )

        // Rhythm substitutes for the degree criteria rather than joining them: no rhythm criterion may
        // ever appear in group 1, or M2's mastery structure would have been altered by a phase that
        // docs/20-PHASE-2-SPEC.md §4 forbids from altering it.
        assertTrue(rhythm.none { it in degreeBased.toSet() }, "a rhythm criterion leaked into M2's five")

        assertEquals(
            degreeBased.toSet() + binaryNode + nodeSpecific + rhythm,
            MasteryCriterion.Kind.entries.toSet(),
            "a mastery criterion exists that is not accounted for above. Adding one is a pedagogical " +
                "decision, not a refactor: name it here, in the group it belongs to, with the section " +
                "of the spec that asks for it.",
        )
    }
}
