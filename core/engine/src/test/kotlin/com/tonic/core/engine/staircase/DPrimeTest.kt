package com.tonic.core.engine.staircase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** docs/09-BUILD-PLAN.md Stage 4 acceptance: hand-computed values, including the extreme-rate correction. */
class DPrimeTest {
    @Test
    fun `equal hit and false-alarm rates give exactly zero`() {
        // (5+0.5)/(10+1) = (5+0.5)/(10+1) - hitRate equals faRate exactly, so d' = z(x) - z(x) = 0.
        assertEquals(0.0, DPrime.compute(hits = 5, signalTrials = 10, falseAlarms = 5, noiseTrials = 10), 1e-9)
    }

    @Test
    fun `a perfect responder still yields a finite d-prime thanks to the correction`() {
        // Raw hitRate would be 1.0 -> z(1.0) is undefined/infinite without the +0.5/+1 correction.
        val dPrime = DPrime.compute(hits = 10, signalTrials = 10, falseAlarms = 0, noiseTrials = 10)
        assertTrue(dPrime.isFinite())
        assertTrue(dPrime > 2.0, "a perfect responder should show strong sensitivity")
    }

    @Test
    fun `a chance responder yields a d-prime near zero`() {
        // hits proportional to signal trials at 50%, false alarms at 50% -> same rate, d' near zero.
        val dPrime = DPrime.compute(hits = 10, signalTrials = 20, falseAlarms = 10, noiseTrials = 20)
        assertTrue(kotlin.math.abs(dPrime) < 0.1)
    }

    @Test
    fun `higher hit rate than false-alarm rate gives positive sensitivity`() {
        val dPrime = DPrime.compute(hits = 16, signalTrials = 20, falseAlarms = 4, noiseTrials = 20)
        assertTrue(dPrime > 0.0)
    }

    @Test
    fun `an indiscriminate always-different responder is not scored as sensitive`() {
        // Always answers "different": hits = all signal trials, but false alarms = all noise trials too.
        val dPrime = DPrime.compute(hits = 20, signalTrials = 20, falseAlarms = 20, noiseTrials = 20)
        assertTrue(kotlin.math.abs(dPrime) < 0.5, "indiscriminate responding must not look sensitive: d'=$dPrime")
    }
}
