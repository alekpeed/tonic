package com.tonic.core.audio.synth

import com.tonic.core.audio.util.SimpleFft
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.music.Tuning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 2 acceptance / docs/10-TESTING.md §6:
 * "render a note, FFT the buffer, assert the peak bin is within 1 cent of
 * target," for all four timbres across MIDI 40/55/69/84/96 —
 * docs/06-AUDIO-ENGINE.md §3's register-integrity requirement.
 */
class PitchAccuracyTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE
    private val noteDurationMs = 700L

    private fun centsOff(
        measuredHz: Double,
        targetHz: Double,
    ): Double = 1200.0 * (Math.log(measuredHz / targetHz) / Math.log(2.0))

    /**
     * REED's vibrato (docs/06-AUDIO-ENGINE.md §3) only engages after a
     * 200ms onset and deliberately moves pitch by design — a window
     * straddling that onset would measure a vibrato-smeared frequency, not
     * a synth bug. Every other timbre uses a window well past its onset
     * transient with plenty of margin before the note ends.
     */
    private fun analysisWindow(timbre: TimbreId): IntRange =
        if (timbre == TimbreId.REED) {
            val offset = (0.010 * sampleRate).toInt()
            offset until (offset + 8_000)
        } else {
            val offset = (0.05 * sampleRate).toInt()
            offset until (offset + 16_384)
        }

    @Test
    fun `every timbre is within 1 cent of target across the register`() {
        val failures = mutableListOf<String>()
        for (timbre in TimbreId.entries) {
            for (midi in listOf(40, 55, 69, 84, 96)) {
                val buffer = SynthEngine.renderNote(midi, timbre, noteDurationMs, sampleRate, seed = midi.toLong())
                val window = analysisWindow(timbre)
                val segment =
                    buffer.samples.copyOfRange(
                        window.first,
                        window.last.coerceAtMost(buffer.samples.size - 1) + 1,
                    )
                val measured = SimpleFft.dominantFrequency(segment, sampleRate)
                val target = Tuning.midiToHz(midi)
                val cents = centsOff(measured, target)
                if (abs(cents) >= 1.0) {
                    failures += "$timbre @ MIDI $midi: target=${target}Hz measured=${measured}Hz ($cents cents)"
                }
            }
        }
        assertTrue(failures.isEmpty(), "Pitch accuracy failures:\n" + failures.joinToString("\n"))
    }
}
