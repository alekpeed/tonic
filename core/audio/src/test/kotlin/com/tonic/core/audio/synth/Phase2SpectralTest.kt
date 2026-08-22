package com.tonic.core.audio.synth

import com.tonic.core.audio.util.SimpleFft
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.music.Tuning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What can be established about Phase 2's audio *without ears* — the pitch content of the material
 * Phase 2 added, measured off the rendered buffer rather than asserted.
 *
 * This does not replace listening and is not offered as a substitute. A spectrum says a note is at the
 * frequency it claims; it says nothing about whether `i–iv–v–i` convincingly establishes a key, or
 * whether a 30-cent bend is perceptible on a phone speaker at a real volume. Those need a human.
 *
 * What it does close is the narrower question that had gone unchecked: Phase 1's
 * [PitchAccuracyTest] verified single notes across the register, and nothing since has verified that
 * minor's cadence is actually minor, that a chromatic degree lands between its neighbors, or that
 * `PREDICT_DEVIATION` level 3 bends by the 30 cents it claims. Those are objective and were being
 * taken on trust.
 */
class Phase2SpectralTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE

    private fun centsOff(
        measuredHz: Double,
        targetHz: Double,
    ): Double = 1200.0 * (Math.log(measuredHz / targetHz) / Math.log(2.0))

    /** Dominant frequency of a buffer, measured well past the onset transient. */
    private fun fundamentalHz(buffer: PcmBuffer): Double {
        val offset = (0.05 * sampleRate).toInt()
        val n = 16_384
        val segment = buffer.samples.copyOfRange(offset, offset + n)
        val real = DoubleArray(n) { segment[it].toDouble() * hann(it, n) }
        val imag = DoubleArray(n)
        SimpleFft.transform(real, imag)

        var peakBin = 1
        var peakMag = 0.0
        for (bin in 1 until n / 2) {
            val mag = real[bin] * real[bin] + imag[bin] * imag[bin]
            if (mag > peakMag) {
                peakMag = mag
                peakBin = bin
            }
        }
        // Parabolic interpolation across the peak - a bin is ~2.9 Hz wide at this length, which is
        // 20+ cents in the low register and would swamp the 30-cent measurement below.
        val magAt = { b: Int -> Math.sqrt(real[b] * real[b] + imag[b] * imag[b]) }
        val alpha = magAt(peakBin - 1)
        val beta = magAt(peakBin)
        val gamma = magAt(peakBin + 1)
        val shift = 0.5 * (alpha - gamma) / (alpha - 2 * beta + gamma)
        return (peakBin + shift) * sampleRate / n
    }

    private fun hann(
        i: Int,
        n: Int,
    ): Double = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1))

    @Test
    fun `a 30-cent detune really is 30 cents`() {
        // docs/20-PHASE-2-SPEC.md §2.3's hardest deviation level, and the one most likely to be wrong
        // silently: PREDICT_DEVIATION 3 renders by frequency rather than by MIDI number, so a mistake
        // here produces a note that is merely slightly off rather than obviously broken.
        for (midi in listOf(55, 60, 67, 72)) {
            val base = Tuning.midiToHz(midi)
            for (cents in listOf(-30.0, 30.0)) {
                val bent = Tuning.offsetByCents(base, cents)
                val buffer = SynthEngine.renderTone(bent, TimbreId.PURE, 900L, sampleRate, seed = 1L)
                val measured = centsOff(fundamentalHz(buffer), base)
                assertTrue(
                    abs(measured - cents) < 3.0,
                    "asked for $cents cents off MIDI $midi, measured ${"%.1f".format(measured)}",
                )
            }
        }
    }

    @Test
    fun `a detuned note is far enough from its neighbors to be a bend rather than a different note`() {
        // The pedagogical claim behind level 3: it is the *right* note, bent. If 30 cents drifted
        // toward 100 it would be a semitone error, which is level 2's job and a different exercise.
        val base = Tuning.midiToHz(60)
        val bent = Tuning.offsetByCents(base, 30.0)
        val measured = fundamentalHz(SynthEngine.renderTone(bent, TimbreId.PURE, 900L, sampleRate, seed = 2L))
        val toNeighbor = abs(centsOff(measured, Tuning.midiToHz(61)))
        assertTrue(toNeighbor > 60.0, "the bend landed ${"%.1f".format(toNeighbor)} cents from the semitone above")
    }

    @Test
    fun `every chromatic degree renders at its own pitch, a semitone from both neighbors`() {
        // Twelve distinct pitches, measured. M11's whole premise is that ♯4 is audibly neither 4 nor 5.
        val tonic = 60
        val measured =
            (0..11).map { semitones ->
                fundamentalHz(
                    SynthEngine.renderNote(
                        tonic + semitones,
                        TimbreId.PURE,
                        700L,
                        sampleRate,
                        seed = semitones.toLong(),
                    ),
                )
            }
        for (semitones in 0..11) {
            val off = centsOff(measured[semitones], Tuning.midiToHz(tonic + semitones))
            assertTrue(abs(off) < 5.0, "chromatic degree $semitones measured ${"%.1f".format(off)} cents off")
        }
        for (i in 0 until 11) {
            // Magnitude: centsOff reads (measured, target), so passing the higher pitch as the target
            // yields a negative number for an ascending step. The distance is what matters.
            val gap = abs(centsOff(measured[i], measured[i + 1]))
            assertTrue(abs(gap - 100.0) < 10.0, "adjacent chromatic pitches are ${"%.0f".format(gap)} cents apart")
        }
    }
}
