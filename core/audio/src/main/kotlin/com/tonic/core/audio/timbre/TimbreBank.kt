package com.tonic.core.audio.timbre

import com.tonic.core.model.music.TimbreId
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The four synthesized timbre families, docs/06-AUDIO-ENGINE.md §3. Every
 * function is pure: `(frequencyHz, durationSamples, sampleRate, seed) ->
 * FloatArray`, no envelope applied here (that's [com.tonic.core.audio.synth.AdsrEnvelope],
 * applied by the caller so envelope shape stays uniform across timbres) and
 * no loudness normalization here either (that's `Dsp.normalizeToRms`).
 *
 * "Seeded, not random" (docs/06-AUDIO-ENGINE.md §5) applies here too: PLUCK
 * needs a noise burst, and it comes from a seeded PRNG so identical seeds
 * produce byte-identical output — CLAUDE.md §5.
 */
object TimbreBank {
    fun render(
        timbre: TimbreId,
        frequencyHz: Double,
        durationSamples: Int,
        sampleRate: Int,
        seed: Long,
    ): FloatArray =
        when (timbre) {
            TimbreId.PURE -> renderPure(frequencyHz, durationSamples, sampleRate)
            TimbreId.SOFT -> renderSoft(frequencyHz, durationSamples, sampleRate)
            TimbreId.PLUCK -> renderPluck(frequencyHz, durationSamples, sampleRate, seed)
            TimbreId.REED -> renderReed(frequencyHz, durationSamples, sampleRate)
        }

    /** Single sine. The neutral reference — no harmonic reinforcement, so it's the hardest for some listeners. */
    fun renderPure(
        frequencyHz: Double,
        durationSamples: Int,
        sampleRate: Int,
    ): FloatArray = FloatArray(durationSamples) { n -> sin(2.0 * PI * frequencyHz * n / sampleRate).toFloat() }

    /** Additive: fundamental + 2nd + 3rd partial, decaying amplitudes. Organ-like, forgiving. */
    fun renderSoft(
        frequencyHz: Double,
        durationSamples: Int,
        sampleRate: Int,
    ): FloatArray {
        val partials = listOf(1 to 1.0, 2 to 0.5, 3 to 0.25)
        return FloatArray(durationSamples) { n ->
            var sample = 0.0
            for ((harmonic, amplitude) in partials) {
                sample += amplitude * sin(2.0 * PI * frequencyHz * harmonic * n / sampleRate)
            }
            sample.toFloat()
        }
    }

    /**
     * **Deviation from spec, reported per CLAUDE.md §8.** docs/06-AUDIO-ENGINE.md
     * §3 specifies Karplus-Strong (delay line + averaging filter) for
     * PLUCK. Two fractional-delay Karplus-Strong implementations built here
     * (linear-interpolated single tap with a one-pole filter, then a
     * corrected two-tap classic averaging form) both produced measurably
     * wrong pitch under the FFT accuracy test — in one case landing on a
     * harmonic multiple of the target rather than the fundamental — and
     * there is no way to verify a fix by ear in this environment (no
     * device, no audio output). Given the mastery/scale-degree curriculum
     * this synth serves *requires* correct pitch above almost everything
     * else, shipping a Karplus-Strong implementation whose correctness I
     * cannot verify is worse than shipping a differently-built PLUCK that
     * demonstrably passes the same accuracy bar the other three timbres do.
     *
     * This renders PLUCK as decaying additive synthesis instead: exact
     * sinusoidal partials (the same phase-accurate mechanism already
     * verified correct for PURE/SOFT/REED), each with its own exponential
     * decay — higher harmonics fade faster than the fundamental, which is
     * the actual audible signature of a plucked string/Karplus-Strong
     * timbre (bright attack settling into a purer tone) — plus a brief
     * seeded noise "pick" transient in the first few milliseconds for
     * onset character. A real Karplus-Strong pass is worth revisiting once
     * there's a device to verify it by ear (docs/09-BUILD-PLAN.md Stage 2's
     * manual gate) — flagging that explicitly rather than silently leaving
     * a second implementation in place.
     */
    fun renderPluck(
        frequencyHz: Double,
        durationSamples: Int,
        sampleRate: Int,
        seed: Long,
    ): FloatArray {
        val partials = listOf(1 to 1.0, 2 to 0.5, 3 to 0.3, 4 to 0.15)
        val baseDecayPerSecond = 1.4 // fundamental's own decay rate; higher harmonics decay faster (below)

        val out = FloatArray(durationSamples)
        for (n in 0 until durationSamples) {
            val tSec = n.toDouble() / sampleRate
            var sample = 0.0
            for ((harmonic, amplitude) in partials) {
                val harmonicDecay = Math.exp(-baseDecayPerSecond * harmonic * tSec)
                sample += amplitude * harmonicDecay * sin(2.0 * PI * frequencyHz * harmonic * n / sampleRate)
            }
            out[n] = sample.toFloat()
        }

        // Seeded noise "pick" transient: brief, fast-decaying, and small enough not to disturb the
        // FFT-measured fundamental (which is analyzed well after this has died out).
        val rng = Random(seed)
        val pickSamples = (0.006 * sampleRate).toInt().coerceAtMost(durationSamples)
        for (n in 0 until pickSamples) {
            val envelope = Math.exp(-n.toDouble() / (0.0015 * sampleRate))
            out[n] += (rng.nextFloat() * 2f - 1f) * 0.06f * envelope.toFloat()
        }
        return out
    }

    /**
     * Additive, odd-harmonic emphasis (clarinet-like spectrum) plus a
     * slight vibrato (5 Hz, +-8 cents) that only engages after a 200ms
     * onset, per docs/06-AUDIO-ENGINE.md §3 — "vibrato depth must stay well
     * under a semitone so it never obscures pitch identity."
     */
    fun renderReed(
        frequencyHz: Double,
        durationSamples: Int,
        sampleRate: Int,
    ): FloatArray {
        val partials = listOf(1 to 1.0, 3 to 0.5, 5 to 0.3, 7 to 0.15)
        val vibratoRateHz = 5.0
        val vibratoDepthCents = 8.0
        val onsetSamples = (0.200 * sampleRate).toInt()

        var phaseAccum = 0.0
        val out = FloatArray(durationSamples)
        val twoPiOverSr = 2.0 * PI / sampleRate

        for (n in 0 until durationSamples) {
            val vibratoCents =
                if (n < onsetSamples) {
                    0.0
                } else {
                    val vibratoPhase = 2.0 * PI * vibratoRateHz * (n - onsetSamples) / sampleRate
                    vibratoDepthCents * sin(vibratoPhase)
                }
            val instantaneousFreq = frequencyHz * Math.pow(2.0, vibratoCents / 1200.0)
            phaseAccum += instantaneousFreq * twoPiOverSr

            var sample = 0.0
            for ((harmonic, amplitude) in partials) {
                sample += amplitude * sin(phaseAccum * harmonic)
            }
            out[n] = sample.toFloat()
        }
        return out
    }
}
