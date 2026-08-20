package com.tonic.core.audio.synth

import com.tonic.core.model.items.ReferenceElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** docs/10-TESTING.md §6: "Silence in gaps | RMS over the gap window | below noise floor." */
class SilenceCorrectnessTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE

    @Test
    fun `a Silence element renders to exact zero`() {
        val buffer = SynthEngine.renderElement(ReferenceElement.Silence(durationMs = 500), sampleRate)
        assertEquals(0.0, Dsp.rms(buffer.samples))
        assertTrue(buffer.samples.all { it == 0f })
    }

    @Test
    fun `the gap inside a rendered item is silent`() {
        val plan =
            com.tonic.core.model.items.ReferencePlan(
                cadenceFadeLevel = com.tonic.core.model.items.CadenceFadeLevel.L3,
                elements =
                    listOf(
                        ReferenceElement.ChordEvent(
                            listOf(60, 64, 67),
                            400,
                            com.tonic.core.model.music.TimbreId.SOFT,
                        ),
                    ),
            )
        val gapMs = 300L
        val item =
            SynthEngine.renderItem(
                plan,
                gapMs,
                64,
                com.tonic.core.model.music.TimbreId.SOFT,
                500,
                sampleRate,
                seed = 1L,
            )
        val referenceSamples = PcmBuffer.msToSamples(400, sampleRate)
        val gapSamples = PcmBuffer.msToSamples(gapMs, sampleRate)
        val gapSegment = item.samples.copyOfRange(referenceSamples, referenceSamples + gapSamples)
        assertEquals(0.0, Dsp.rms(gapSegment))
    }
}
