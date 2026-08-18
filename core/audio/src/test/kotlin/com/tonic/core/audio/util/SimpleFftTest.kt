package com.tonic.core.audio.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** Sanity-checks the test-only FFT itself against a known sine, before trusting it to grade the synth. */
class SimpleFftTest {
    @Test
    fun `pure sine at a known frequency is recovered within 0-1 cent`() {
        val sampleRate = 48_000
        val freq = 440.0
        val samples = FloatArray(sampleRate) { n -> sin(2.0 * PI * freq * n / sampleRate).toFloat() }
        val measured = SimpleFft.dominantFrequency(samples, sampleRate)
        val cents = 1200.0 * (Math.log(measured / freq) / Math.log(2.0))
        assertTrue(abs(cents) < 0.1, "expected ~$freq Hz, measured $measured Hz ($cents cents off)")
    }

    @Test
    fun `nextPowerOfTwo rounds up`() {
        assertTrue(SimpleFft.nextPowerOfTwo(1) == 1)
        assertTrue(SimpleFft.nextPowerOfTwo(2) == 2)
        assertTrue(SimpleFft.nextPowerOfTwo(3) == 4)
        assertTrue(SimpleFft.nextPowerOfTwo(1000) == 1024)
    }
}
