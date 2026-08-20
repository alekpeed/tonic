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
        // has now caught two of them - FOCUS_DEGREE in Stage 2.5 and PREDICT_GAP_MINIMUM in Stage 2.6,
        // both of which were added without this list being updated and both of which failed here until
        // they were named. That is the test working, so it is *extended* rather than loosened: every
        // kind must still be accounted for by name, in one of three groups, with a reason.
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

        assertEquals(
            degreeBased.toSet() + binaryNode + nodeSpecific,
            MasteryCriterion.Kind.entries.toSet(),
            "a mastery criterion exists that is not accounted for above. Adding one is a pedagogical " +
                "decision, not a refactor: name it here, in the group it belongs to, with the section " +
                "of the spec that asks for it.",
        )
    }
}
