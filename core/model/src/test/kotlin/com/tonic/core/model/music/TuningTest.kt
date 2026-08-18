package com.tonic.core.model.music

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** docs/09-BUILD-PLAN.md Stage 1 acceptance. */
class TuningTest {
    @Test
    fun `midi 69 is exactly 440 Hz`() {
        assertEquals(440.0, Tuning.midiToHz(69), 0.0)
    }

    @Test
    fun `midi to hz round trips across the full register`() {
        for (midi in 21..108) {
            val hz = Tuning.midiToHz(midi)
            val roundTripped = Tuning.hzToMidi(hz)
            assertEquals(midi, roundTripped, "MIDI $midi -> ${hz}Hz -> $roundTripped")
        }
    }

    @Test
    fun `each semitone is a factor of 2 to the 1 over 12`() {
        val ratio = Tuning.midiToHz(70) / Tuning.midiToHz(69)
        assertEquals(Math.pow(2.0, 1.0 / 12.0), ratio, 1e-9)
    }

    @Test
    fun `an octave doubles frequency`() {
        assertEquals(Tuning.midiToHz(69) * 2, Tuning.midiToHz(81), 1e-9)
    }

    @Test
    fun `offsetByCents at 1200 cents doubles the frequency`() {
        val base = 220.0
        assertEquals(base * 2, Tuning.offsetByCents(base, 1200.0), 1e-9)
    }

    @Test
    fun `offsetByCents at 0 cents is identity`() {
        assertEquals(220.0, Tuning.offsetByCents(220.0, 0.0), 1e-9)
    }

    @Test
    fun `centsBetween is the inverse of offsetByCents`() {
        val base = 261.6255653006
        for (cents in listOf(-1200.0, -100.0, -10.0, 0.0, 10.0, 100.0, 1200.0)) {
            val shifted = Tuning.offsetByCents(base, cents)
            assertTrue(abs(Tuning.centsBetween(base, shifted) - cents) < 1e-6, "cents=$cents")
        }
    }

    @Test
    fun `a4 setting shifts absolute pitch but not interval ratios`() {
        val a4 = 442.0
        assertEquals(442.0, Tuning.midiToHz(69, a4), 0.0)
        val ratio = Tuning.midiToHz(70, a4) / Tuning.midiToHz(69, a4)
        assertEquals(Math.pow(2.0, 1.0 / 12.0), ratio, 1e-9)
    }
}
