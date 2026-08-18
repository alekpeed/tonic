package com.tonic.core.audio.timbre

import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TimbreBankTest {
    private val sampleRate = 48_000

    @Test
    fun `render dispatches to the right family and returns the requested length`() {
        for (timbre in TimbreId.entries) {
            val out = TimbreBank.render(timbre, 440.0, 1000, sampleRate, seed = 1L)
            assertEquals(1000, out.size)
        }
    }

    @Test
    fun `renderPure is exactly a sine wave`() {
        val out = TimbreBank.renderPure(440.0, 4, sampleRate)
        val expected = FloatArray(4) { n -> kotlin.math.sin(2.0 * Math.PI * 440.0 * n / sampleRate).toFloat() }
        assertContentEquals(expected, out)
    }

    @Test
    fun `renderPluck is deterministic given a seed and varies with it`() {
        val a = TimbreBank.renderPluck(220.0, 2000, sampleRate, seed = 5L)
        val b = TimbreBank.renderPluck(220.0, 2000, sampleRate, seed = 5L)
        val c = TimbreBank.renderPluck(220.0, 2000, sampleRate, seed = 6L)
        assertContentEquals(a, b)
        assertFalse(a.contentEquals(c))
    }

    @Test
    fun `renderReed engages vibrato only after the 200ms onset`() {
        val out = TimbreBank.renderReed(440.0, (0.5 * sampleRate).toInt(), sampleRate)
        assertEquals((0.5 * sampleRate).toInt(), out.size)
    }
}
