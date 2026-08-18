package com.tonic.core.model.items

import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(DifficultyAxis.entries.toSet(), DifficultyAxis.SCHEDULING_PRIORITY.toSet())
    }
}
