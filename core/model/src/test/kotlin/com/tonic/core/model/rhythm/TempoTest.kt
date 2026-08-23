package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §3.5 — the one axis in this project that is not monotonic in what it
 * measures.
 */
class TempoTest {
    @Test
    fun `level zero is the comfortable centre, in both directions`() {
        assertEquals(Tempo.CENTER_BPM to Tempo.CENTER_BPM, Tempo.bpmPairFor(0))
    }

    @Test
    fun `every level offers a slower and a faster tempo`() {
        // The heart of §3.5: both faster and slower are harder. A level that offered only one side would
        // delete half the axis, and the deleted half - slow - is the one that trains internal
        // timekeeping rather than reactive entrainment.
        for (level in 1..Tempo.MAX_LEVEL) {
            val (slow, fast) = Tempo.bpmPairFor(level)
            assertTrue(slow < Tempo.CENTER_BPM, "level $level has no slow side")
            assertTrue(fast > Tempo.CENTER_BPM, "level $level has no fast side")
            assertEquals(Tempo.deviationOf(slow), Tempo.deviationOf(fast), "level $level is asymmetric")
        }
    }

    @Test
    fun `deviation grows with the level`() {
        val deviations = (0..Tempo.MAX_LEVEL).map { Tempo.deviationOf(Tempo.bpmPairFor(it).second) }
        assertEquals(deviations.sorted(), deviations, "deviation should increase: $deviations")
    }

    @Test
    fun `a beat is sixty thousand milliseconds divided by the tempo`() {
        assertEquals(600.0, Tempo.msPerBeat(100), 0.0001)
        assertEquals(1000.0, Tempo.msPerBeat(60), 0.0001)
        assertEquals(Tempo.msPerBeat(100) / Meter.TICKS_PER_BEAT, Tempo.msPerTick(100), 0.0001)
    }

    @Test
    fun `an impossible tempo or level is refused`() {
        assertFailsWith<IllegalArgumentException> { Tempo.msPerBeat(0) }
        assertFailsWith<IllegalArgumentException> { Tempo.msPerBeat(-100) }
        assertFailsWith<IllegalArgumentException> { Tempo.bpmPairFor(-1) }
        assertFailsWith<IllegalArgumentException> { Tempo.bpmPairFor(Tempo.MAX_LEVEL + 1) }
    }
}
