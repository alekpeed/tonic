package com.tonic.core.audio.synth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * docs/06-AUDIO-ENGINE.md §4: attack >= 5ms (else it's a click), release
 * >= 20ms with a smooth taper to zero. docs/10-TESTING.md §6: "Envelope
 * shape | attack/release segment analysis | attack >= 5ms, release >= 20ms."
 */
class EnvelopeTest {
    private val sampleRate = 48_000

    @Test
    fun `rejects an attack shorter than 5ms`() {
        assertFailsWith<IllegalArgumentException> {
            AdsrEnvelope(
                attackMs = 4,
                decayMs = 0,
                sustainLevel = 1f,
                releaseMs = 20,
            )
        }
    }

    @Test
    fun `rejects a release shorter than 20ms`() {
        assertFailsWith<IllegalArgumentException> {
            AdsrEnvelope(
                attackMs = 5,
                decayMs = 0,
                sustainLevel = 1f,
                releaseMs = 19,
            )
        }
    }

    @Test
    fun `envelope starts at zero, reaches peak by end of attack, and tapers exactly to zero`() {
        val envelope = AdsrEnvelope(attackMs = 10, decayMs = 20, sustainLevel = 0.7f, releaseMs = 30)
        val totalSamples = (0.4 * sampleRate).toInt()
        val values = envelope.render(totalSamples, sampleRate)

        assertTrue(values.first() > 0f, "first sample should already be ramping, not silent")
        assertEquals(0f, values.last(), "envelope must taper to exact zero")

        val attackSamples = (0.010 * sampleRate).toInt()
        assertEquals(1f, values[attackSamples - 1], absoluteTolerance = 0.001f)
    }

    @Test
    fun `a very short note still protects attack and release without exceeding the buffer`() {
        val envelope = AdsrEnvelope(attackMs = 40, decayMs = 30, sustainLevel = 0.9f, releaseMs = 60)
        // Shorter than attack + decay + release combined.
        val totalSamples = (0.02 * sampleRate).toInt()
        val values = envelope.render(totalSamples, sampleRate)
        assertEquals(totalSamples, values.size)
        assertEquals(0f, values.last())
        assertTrue(values.all { it in 0f..1f })
    }

    @Test
    fun `zero-length render returns an empty array without throwing`() {
        val envelope = AdsrEnvelope(attackMs = 5, decayMs = 0, sustainLevel = 1f, releaseMs = 20)
        assertEquals(0, envelope.render(0, sampleRate).size)
    }

    private fun assertEquals(
        expected: Float,
        actual: Float,
        absoluteTolerance: Float,
    ) {
        assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected ~$expected, was $actual")
    }
}
