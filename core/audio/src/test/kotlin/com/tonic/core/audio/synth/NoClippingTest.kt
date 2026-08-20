package com.tonic.core.audio.synth

import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertTrue

/** docs/10-TESTING.md §6: "No clipping | max abs sample | < 1.0", across a corpus of generated buffers. */
class NoClippingTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE

    @Test
    fun `no buffer in a 200-item corpus clips`() {
        var count = 0
        val worstOffenders = mutableListOf<String>()

        for (seed in 0 until 50) {
            for (timbre in TimbreId.entries) {
                val midi = 40 + (seed * 7 + timbre.ordinal * 11) % 56 // spread across the register, deterministically
                val buffer =
                    SynthEngine.renderNote(
                        midi,
                        timbre,
                        durationMs = 300 + (seed % 5) * 100L,
                        sampleRate,
                        seed.toLong(),
                    )
                val peak = Dsp.peakAbs(buffer.samples)
                if (peak >= 1.0f) worstOffenders += "$timbre midi=$midi seed=$seed peak=$peak"
                count++
            }
        }

        assertTrue(count >= 200, "corpus too small: $count")
        assertTrue(worstOffenders.isEmpty(), "Clipping buffers:\n" + worstOffenders.joinToString("\n"))
    }

    @Test
    fun `chords with multiple simultaneous voices do not clip`() {
        val chord =
            com.tonic.core.model.items.ReferenceElement.ChordEvent(
                midiNotes = listOf(60, 64, 67, 72),
                durationMs = 600,
                timbre = TimbreId.SOFT,
                voiceJitterMs = listOf(0, 3, 5, 8),
            )
        val rendered = SynthEngine.renderElement(chord, sampleRate, seed = 1L)
        assertTrue(Dsp.peakAbs(rendered.samples) < 1.0f)
    }
}
