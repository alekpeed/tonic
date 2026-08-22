package com.tonic.core.model.rhythm

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §4.3 (the calibration constant is subtracted from every measurement) and
 * §4.4 (scoring is a pure function of recorded values).
 *
 * The case that matters most here is §9 simulation 2 — the learner who perceives correctly and taps
 * uniformly 40 ms late, who "must still master." Calibration is what absorbs that, and this is where
 * the absorbing happens.
 */
class TapTimelineTest {
    private val patternStart = 10_000_000_000L

    private fun tapAtMs(ms: Double) = TapEvent(patternStart + (ms * 1_000_000).toLong())

    @Test
    fun `a tap at the pattern start is time zero`() {
        val relative = TapTimeline.relativeToPatternMs(listOf(TapEvent(patternStart)), patternStart, 0.0)
        assertEquals(listOf(0.0), relative)
    }

    @Test
    fun `taps are expressed in milliseconds from the pattern start`() {
        val taps = listOf(tapAtMs(0.0), tapAtMs(500.0), tapAtMs(1000.0))
        val relative = TapTimeline.relativeToPatternMs(taps, patternStart, 0.0)
        assertEquals(listOf(0.0, 500.0, 1000.0), relative)
    }

    @Test
    fun `a uniformly late tapper lands on the beat once calibrated`() {
        // Simulation 2, in miniature. Four taps, every one 40 ms late, and a calibration constant of
        // exactly that. If this does not come out on the beat, the phase does not work.
        val beats = listOf(0.0, 500.0, 1000.0, 1500.0)
        val taps = beats.map { tapAtMs(it + 40.0) }
        val corrected = TapTimeline.relativeToPatternMs(taps, patternStart, calibrationOffsetMs = 40.0)
        corrected.forEachIndexed { i, ms ->
            assertTrue(abs(ms - beats[i]) < 0.001, "beat $i corrected to $ms, expected ${beats[i]}")
        }
    }

    @Test
    fun `a uniformly early tapper is corrected in the other direction`() {
        // The constant is median(tap - event), so it goes negative for someone who anticipates. Nothing
        // in the arithmetic should care which sign it is; asserted because a sign convention that is
        // only written down in a KDoc is a sign convention waiting to be inverted.
        val taps = listOf(tapAtMs(-25.0), tapAtMs(475.0))
        val corrected = TapTimeline.relativeToPatternMs(taps, patternStart, calibrationOffsetMs = -25.0)
        assertTrue(abs(corrected[0] - 0.0) < 0.001, "expected 0.0, got ${corrected[0]}")
        assertTrue(abs(corrected[1] - 500.0) < 0.001, "expected 500.0, got ${corrected[1]}")
    }

    @Test
    fun `a tap before the pattern started is kept, negative`() {
        // §6.2 counts extra taps as an error of its own kind. Dropping them here would hide a learner
        // who tapped through the count-in from the part of the system whose job is to notice.
        val relative = TapTimeline.relativeToPatternMs(listOf(tapAtMs(-120.0)), patternStart, 0.0)
        assertEquals(1, relative.size)
        assertTrue(relative[0] < 0.0, "expected a negative time, got ${relative[0]}")
    }

    @Test
    fun `output is ascending regardless of input order`() {
        val taps = listOf(tapAtMs(750.0), tapAtMs(250.0), tapAtMs(1000.0), tapAtMs(0.0))
        val relative = TapTimeline.relativeToPatternMs(taps, patternStart, 0.0)
        assertEquals(relative.sorted(), relative)
        assertEquals(listOf(0.0, 250.0, 750.0, 1000.0), relative)
    }

    @Test
    fun `no taps produces no times`() {
        assertEquals(emptyList(), TapTimeline.relativeToPatternMs(emptyList(), patternStart, 12.5))
    }

    @Test
    fun `the same recorded taps produce the same result every time`() {
        // §4.4's replay requirement, at this layer: no clock is read in here, so two calls with the
        // same inputs are the same call. The scoring pipeline in Stage 4.3 inherits this or loses it.
        val taps = listOf(tapAtMs(0.0), tapAtMs(333.0), tapAtMs(667.0))
        assertEquals(
            TapTimeline.relativeToPatternMs(taps, patternStart, 17.25),
            TapTimeline.relativeToPatternMs(taps, patternStart, 17.25),
        )
    }
}
