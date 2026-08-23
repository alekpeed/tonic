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
        // Changed again in Phase 4 Stage 4.2, and the invariant moved rather than weakened. Old: every
        // axis belongs to exactly *one* scope. New: every axis belongs to at least one, the three lists
        // cover all of them, and the only axis in two is TIMBRE_VARIETY - asserted by name below, so a
        // second axis quietly joining two scopes fails here rather than being discovered by a scheduler
        // offering a rhythm axis to an M2 node.
        //
        // TIMBRE_VARIETY is shared because docs/40-PHASE-4-SPEC.md §5.2 reuses it for rhythm on the same
        // generalization argument that put it in the pitch track. Giving rhythm a separate entry for the
        // same idea would split one learner's timbre progress across two identifiers.
        assertEquals(
            DifficultyAxis.entries.toSet(),
            DifficultyAxis.RECOGNITION_AXES.toSet() +
                DifficultyAxis.PREDICTION_AXES.toSet() +
                DifficultyAxis.RHYTHM_AXES.toSet(),
            "every axis must belong to a scope - an unscoped axis would be schedulable nowhere",
        )
        assertEquals(
            DifficultyAxis.RHYTHM_AXES.toSet(),
            DifficultyAxis.RHYTHM_SCHEDULING_PRIORITY.toSet(),
        )
        assertEquals(
            DifficultyAxis.METRONOME_FADE,
            DifficultyAxis.RHYTHM_SCHEDULING_PRIORITY.first(),
            "docs/40-PHASE-4-SPEC.md §5.2: METRONOME_FADE moves first, as CADENCE_FADE does",
        )
        assertEquals(
            setOf(DifficultyAxis.TIMBRE_VARIETY),
            DifficultyAxis.RECOGNITION_AXES.toSet() intersect DifficultyAxis.RHYTHM_AXES.toSet(),
            "only TIMBRE_VARIETY is shared between pitch and rhythm",
        )
        assertTrue(
            DifficultyAxis.RHYTHM_AXES.none { it in DifficultyAxis.PREDICTION_AXES },
            "no axis is both a rhythm and a prediction axis",
        )
        assertEquals(
            1,
            DifficultyAxis.METRONOME_FADE.initialStepSize,
            "docs/40-PHASE-4-SPEC.md §3.2: a skipped rung here makes an item unanswerable, not harder",
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
