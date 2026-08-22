package com.tonic.core.model.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DifficultyAxisTest {
    @Test
    fun `max levels match docs 03-CURRICULUM section 5-3`() {
        assertEquals(7, DifficultyAxis.CADENCE_FADE.maxLevel)
        assertEquals(4, DifficultyAxis.TIMBRE_VARIETY.maxLevel)
        assertEquals(3, DifficultyAxis.REGISTER_SPREAD.maxLevel)
        assertEquals(2, DifficultyAxis.OCTAVE_DISPLACE.maxLevel)
        assertEquals(3, DifficultyAxis.TEMPO_DENSITY.maxLevel)
        assertEquals(2, DifficultyAxis.KEY_SPREAD.maxLevel)
    }

    @Test
    fun `level range starts at zero`() {
        DifficultyAxis.entries.forEach { assertEquals(0..it.maxLevel, it.levelRange) }
    }

    @Test
    fun `scheduling priority puts cadence fade first and tempo density last`() {
        assertEquals(DifficultyAxis.CADENCE_FADE, DifficultyAxis.SCHEDULING_PRIORITY.first())
        assertEquals(DifficultyAxis.TEMPO_DENSITY, DifficultyAxis.SCHEDULING_PRIORITY.last())
        // Changed in Phase 2 Stage 2.0. Old: SCHEDULING_PRIORITY covered *every* axis, which was true
        // while every axis was a recognition axis. New: it covers exactly the recognition axes, and the
        // prediction axes have their own priority list. Reason: docs/20-PHASE-2-SPEC.md §4 change 3 -
        // M12's axes must never be offered to an M2 node, and the old assertion would have forced them
        // into the very list the scheduler picks from. The invariant is strengthened, not relaxed: both
        // scopes are asserted to be covered, and to be disjoint.
        assertEquals(DifficultyAxis.RECOGNITION_AXES.toSet(), DifficultyAxis.SCHEDULING_PRIORITY.toSet())
        assertEquals(
            DifficultyAxis.PREDICTION_AXES.toSet(),
            DifficultyAxis.PREDICTION_SCHEDULING_PRIORITY.toSet(),
        )
        assertEquals(
            DifficultyAxis.entries.toSet(),
            DifficultyAxis.RECOGNITION_AXES.toSet() + DifficultyAxis.PREDICTION_AXES.toSet(),
            "every axis must belong to exactly one scope - an unscoped axis would be schedulable nowhere",
        )
        assertTrue(
            DifficultyAxis.RECOGNITION_AXES.none { it in DifficultyAxis.PREDICTION_AXES },
            "the scopes must be disjoint",
        )
    }

    @Test
    fun `the six Phase 1 axes are exactly the recognition axes, in their original order`() {
        // Pins Phase 1's axis set against accidental widening: adding an axis to RECOGNITION_AXES
        // changes M2's scheduling and its persisted level maps, so it must be a deliberate, visible act.
        assertEquals(
            listOf(
                DifficultyAxis.CADENCE_FADE,
                DifficultyAxis.TIMBRE_VARIETY,
                DifficultyAxis.REGISTER_SPREAD,
                DifficultyAxis.OCTAVE_DISPLACE,
                DifficultyAxis.TEMPO_DENSITY,
                DifficultyAxis.KEY_SPREAD,
            ),
            DifficultyAxis.RECOGNITION_AXES,
        )
    }
}
