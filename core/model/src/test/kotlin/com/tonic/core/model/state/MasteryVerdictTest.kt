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
    fun `cadence fade criterion is the one that matters and is still just one of five`() {
        assertEquals(5, MasteryCriterion.Kind.entries.size)
        assertTrue(MasteryCriterion.Kind.entries.contains(MasteryCriterion.Kind.CADENCE_FADE_MINIMUM))
    }
}
