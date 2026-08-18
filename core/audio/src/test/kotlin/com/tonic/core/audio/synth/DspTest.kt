package com.tonic.core.audio.synth

import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DspTest {
    @Test
    fun `dbToLinear and linearToDb are inverses`() {
        for (db in listOf(-24.0, -12.0, -6.0, 0.0, 3.0)) {
            val linear = Dsp.dbToLinear(db)
            assertTrue(abs(Dsp.linearToDb(linear) - db) < 1e-9)
        }
    }

    @Test
    fun `rms of a full-scale sine is about 0-707`() {
        val samples = FloatArray(48_000) { n -> sin(2.0 * Math.PI * 440.0 * n / 48_000).toFloat() }
        assertTrue(abs(Dsp.rms(samples) - 0.70710678) < 0.001)
    }

    @Test
    fun `rms of silence is zero`() {
        assertEquals(0.0, Dsp.rms(FloatArray(100)))
    }

    @Test
    fun `normalizeToRms hits the target`() {
        val samples = FloatArray(1000) { n -> sin(2.0 * Math.PI * 100.0 * n / 48_000).toFloat() * 0.01f }
        val target = 0.15
        val normalized = Dsp.normalizeToRms(samples, target)
        assertTrue(abs(Dsp.rms(normalized) - target) < 1e-6)
    }

    @Test
    fun `normalizeToRms on silence returns the input unchanged rather than dividing by zero`() {
        val silence = FloatArray(10)
        val result = Dsp.normalizeToRms(silence, 0.5)
        assertTrue(result.all { it == 0f })
    }

    @Test
    fun `mix sums voices and zero-pads shorter ones`() {
        val a = floatArrayOf(1f, 1f, 1f)
        val b = floatArrayOf(0.5f, 0.5f)
        val mixed = Dsp.mix(listOf(a, b))
        assertEquals(3, mixed.size)
        assertEquals(1.5f, mixed[0])
        assertEquals(1.5f, mixed[1])
        assertEquals(1.0f, mixed[2])
    }

    @Test
    fun `softLimit never exceeds 1-0 even on strongly overdriven input`() {
        val loud = FloatArray(1000) { n -> sin(2.0 * Math.PI * 200.0 * n / 48_000).toFloat() * 5f }
        val limited = Dsp.softLimit(loud)
        assertTrue(limited.all { abs(it) < 1.0f })
    }

    @Test
    fun `softLimit is near-identity below its threshold`() {
        val quiet = floatArrayOf(0.1f, -0.2f, 0.3f, -0.05f)
        val limited = Dsp.softLimit(quiet, threshold = 0.7f)
        for (i in quiet.indices) assertTrue(abs(limited[i] - quiet[i]) < 1e-6)
    }

    @Test
    fun `peakAbs finds the largest magnitude regardless of sign`() {
        assertEquals(0.9f, Dsp.peakAbs(floatArrayOf(0.1f, -0.9f, 0.3f)))
    }
}
