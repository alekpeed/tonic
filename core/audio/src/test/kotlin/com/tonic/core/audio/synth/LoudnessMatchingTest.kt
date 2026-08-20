package com.tonic.core.audio.synth

import com.tonic.core.model.music.TimbreId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/10-TESTING.md §6: "Loudness matching | RMS per timbre, same note |
 * within defined tolerance across all four." docs/06-AUDIO-ENGINE.md §3:
 * "An uncontrolled loudness difference is a confound: it becomes an
 * unintended cue." The mechanism is [Dsp.normalizeToRms] in [SynthEngine.renderNote] —
 * this test is what keeps that mechanism honest.
 */
class LoudnessMatchingTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE

    /** Within 1% relative — tight, because it should hold essentially exactly by construction. */
    private val toleranceFraction = 0.01

    @Test
    fun `RMS matches across all four timbres for the same note`() {
        for (midi in listOf(40, 60, 84)) {
            val rmsByTimbre =
                TimbreId.entries.associateWith { timbre ->
                    val buffer =
                        SynthEngine.renderNote(
                            midi,
                            timbre,
                            durationMs = 600,
                            sampleRate = sampleRate,
                            seed = 1L,
                        )
                    Dsp.rms(buffer.samples)
                }
            val values = rmsByTimbre.values
            val mean = values.average()
            for ((timbre, rms) in rmsByTimbre) {
                val relativeError = abs(rms - mean) / mean
                assertTrue(
                    relativeError < toleranceFraction,
                    "MIDI $midi, $timbre: RMS=$rms vs mean=$mean (${relativeError * 100}% off)",
                )
            }
        }
    }
}
