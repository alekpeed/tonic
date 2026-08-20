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
        // Changed in Phase 2 Stage 2.2. Old: exactly five criterion kinds exist. New: the five that
        // docs/03-CURRICULUM.md §5.5 defines are still exactly those five, and two more exist for
        // binary-answer nodes (M9, M12), which have no scale degrees and so cannot use four of the
        // original five. Reason: docs/20-PHASE-2-SPEC.md §4 forbids changing the M2 mastery structure,
        // so BinaryMasteryEvaluator is a separate evaluator rather than a loosening of this one - the
        // assertion is strengthened to pin the original five by name rather than merely counting them.
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

        val binaryOnly =
            MasteryCriterion.Kind.entries.filterNot {
                it in degreeBased || it == MasteryCriterion.Kind.OVERALL_ACCURACY
            }
        assertEquals(
            setOf(MasteryCriterion.Kind.WINDOW_COVERAGE, MasteryCriterion.Kind.D_PRIME),
            binaryOnly.toSet(),
            "only the two binary-node criteria may be added; anything else needs its own decision",
        )
    }
}
