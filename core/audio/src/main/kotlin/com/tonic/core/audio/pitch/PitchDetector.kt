package com.tonic.core.audio.pitch

import kotlin.math.max
import kotlin.math.min

/**
 * Monophonic pitch detection by the McLeod Pitch Method (MPM) — docs/30-PHASE-3-SPEC.md §5.1, Stage 3.0.
 *
 * **Why MPM rather than YIN.** §5.1 permits either, both being autocorrelation-based and cheap enough to
 * run on-device, and rules out neural approaches (CREPE) as unjustified when the answer only needs to
 * resolve to a scale degree. Between the two, MPM's normalized square difference function is markedly
 * more resistant to *octave errors* — reporting a pitch an octave from the truth — and that specific
 * failure is worse here than anywhere else it could occur. §3 makes octave-agnostic scoring a mandatory
 * mitigation: a learner who sings degree 5 in whatever octave is comfortable must be scored correct,
 * because forcing a register tests vocal range rather than hearing. But the scoring pipeline reaches
 * that outcome by *deliberately discarding* octave information at §5.2 step 5. An octave error therefore
 * cannot be caught downstream — it is erased into a confident wrong degree instead of a detectable
 * fault. Everywhere else an octave slip is a visible bug; here it is silent, so it is worth choosing the
 * algorithm that makes it rarer.
 *
 * **Why this is pure.** docs/04-ARCHITECTURE.md §3 requires the rendering side of `:core:audio` to be
 * `FloatArray` in, `FloatArray` out, with only the playback wrapper touching Android. Detection is held
 * to the same rule for the same payoff: it is testable on the JVM against [com.tonic.core.audio.synth]
 * output at a known frequency, with no microphone and no device. That matters more in this phase than in
 * any before it — Stage 3.0 is the first stage in the project whose primary evidence cannot come from the
 * automated suite, so every part of it that *can* be proven without hardware should be.
 *
 * This object detects; it does not capture. Whether samples arrive via `AudioRecord` or Oboe is
 * §9's open question 1 and is deliberately not decided here — the capture backend changes how a window
 * is filled, not what is computed from it.
 */
public object PitchDetector {
    /**
     * Lowest fundamental considered, in Hz. Roughly C2 — below the bottom of an untrained bass range,
     * chosen low rather than tight because a too-high floor turns a correctly-sung low note into a
     * detected harmonic, which is exactly the octave error [PitchDetector] exists to avoid.
     */
    public const val DEFAULT_MIN_HZ: Double = 65.0

    /** Highest fundamental considered, in Hz. Roughly C6, comfortably above an untrained soprano. */
    public const val DEFAULT_MAX_HZ: Double = 1050.0

    /**
     * Fraction of the strongest NSDF peak a candidate must reach to be accepted in its place.
     *
     * This constant is the octave-error guard, and it only works pointing one way. The NSDF has peaks at
     * the true period *and* at its integer multiples, and those higher-lag peaks are often marginally
     * taller. Taking the tallest peak therefore reports a subharmonic — the octave below. MPM's fix is to
     * take the *earliest* peak that clears this fraction of the tallest, which is the shortest period
     * consistent with the evidence. 0.9 is McLeod's own suggested range; lower values start accepting
     * noise peaks that precede the true one, which errs toward the octave *above*.
     */
    public const val PEAK_ACCEPTANCE: Double = 0.9

    /**
     * Estimates the fundamental of one window of mono PCM.
     *
     * The window should hold at least two periods of the lowest pitch to be found, since the NSDF is
     * computed over lags up to half the window and a period longer than that has nowhere to show up. At
     * 48 kHz and [DEFAULT_MIN_HZ] that is about 1,500 samples; 2,048 is a comfortable frame size and
     * ~43 ms, short enough that a sung note yields many frames for §5.2 step 4's median.
     *
     * @param window mono samples, nominally in `-1.0..1.0`.
     * @param sampleRate frames per second of [window].
     * @param minHz lowest fundamental to consider.
     * @param maxHz highest fundamental to consider.
     * @return the estimate, or `null` when the window is too short to carry a period in range or no peak
     *   clears the acceptance threshold. `null` means *unclear*, never *wrong* — see [PitchEstimate].
     */
    public fun detect(
        window: FloatArray,
        sampleRate: Int,
        minHz: Double = DEFAULT_MIN_HZ,
        maxHz: Double = DEFAULT_MAX_HZ,
    ): PitchEstimate? {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(minHz > 0 && maxHz > minHz) { "need 0 < minHz < maxHz, was $minHz..$maxHz" }

        val minLag = max(2, (sampleRate / maxHz).toInt())
        val maxLag = min(window.size / 2, (sampleRate / minHz).toInt() + 1)
        if (maxLag <= minLag) return null

        val nsdf = normalizedSquareDifference(window, maxLag)
        val peakLag = pickPeakLag(nsdf, minLag, maxLag) ?: return null

        // Parabolic interpolation across the peak and its neighbors. Without it the estimate is
        // quantized to whole samples, which at 48 kHz is about 6 cents at A4 and worsens with pitch -
        // tolerable for naming a degree, but it would put a hard floor under any cent-level readout, and
        // §6.4 shows the learner exactly that ("you were about a quarter-tone flat") as non-scoring
        // information. A readout whose error is dominated by the detector's own grid would be misleading.
        val (refinedLag, refinedClarity) = interpolatePeak(nsdf, peakLag)
        if (refinedLag <= 0.0) return null

        return PitchEstimate(
            frequencyHz = sampleRate / refinedLag,
            clarity = refinedClarity.coerceIn(0.0, 1.0),
        )
    }

    /**
     * The NSDF, `n'(tau) = 2 * r'(tau) / m'(tau)`, over `0..maxLag`.
     *
     * The normalization by `m'` is what separates this from plain autocorrelation and is the whole reason
     * [PEAK_ACCEPTANCE] can be a fixed constant: plain autocorrelation falls away as lag grows simply
     * because fewer terms overlap, so "90% of the tallest peak" would mean different things at different
     * lags. Dividing by the summed energy of the same two overlapping segments removes that taper, giving
     * a function bounded in `-1..1` whose peak heights are directly comparable across the range.
     */
    private fun normalizedSquareDifference(
        window: FloatArray,
        maxLag: Int,
    ): DoubleArray {
        val n = window.size
        val nsdf = DoubleArray(maxLag + 1)
        for (lag in 0..maxLag) {
            var correlation = 0.0
            var energy = 0.0
            for (i in 0 until n - lag) {
                val a = window[i].toDouble()
                val b = window[i + lag].toDouble()
                correlation += a * b
                energy += a * a + b * b
            }
            nsdf[lag] = if (energy > 0.0) 2.0 * correlation / energy else 0.0
        }
        return nsdf
    }

    /**
     * Picks the lag of the earliest key maximum clearing [PEAK_ACCEPTANCE] of the tallest.
     *
     * "Key maximum" is MPM's term for the highest point between a positive-going zero crossing and the
     * following negative-going one — at most one candidate per period of the NSDF, which is what keeps
     * the ripple on a rich timbre's peaks from producing a cluster of spurious candidates around the
     * true one.
     */
    private fun pickPeakLag(
        nsdf: DoubleArray,
        minLag: Int,
        maxLag: Int,
    ): Int? {
        val keyMaxima = mutableListOf<Int>()
        var lag = minLag
        while (lag < maxLag) {
            // Advance to a positive-going zero crossing: the start of a candidate region.
            if (nsdf[lag] > 0.0 && nsdf[lag - 1] <= 0.0) {
                var bestLag = lag
                var current = lag
                while (current < maxLag && nsdf[current] > 0.0) {
                    if (nsdf[current] > nsdf[bestLag]) bestLag = current
                    current++
                }
                keyMaxima += bestLag
                lag = current
            } else {
                lag++
            }
        }
        if (keyMaxima.isEmpty()) return null

        val tallest = keyMaxima.maxOf { nsdf[it] }
        if (tallest <= 0.0) return null

        val threshold = PEAK_ACCEPTANCE * tallest
        return keyMaxima.firstOrNull { nsdf[it] >= threshold }
    }

    /**
     * Fits a parabola through the peak and its two neighbors, returning the refined lag and peak height.
     *
     * Falls back to the integer lag at the array edges, where there is no pair of neighbors to fit.
     */
    private fun interpolatePeak(
        nsdf: DoubleArray,
        peakLag: Int,
    ): Pair<Double, Double> {
        if (peakLag <= 0 || peakLag >= nsdf.size - 1) return peakLag.toDouble() to nsdf[peakLag]

        val left = nsdf[peakLag - 1]
        val mid = nsdf[peakLag]
        val right = nsdf[peakLag + 1]
        val denominator = 2.0 * (2.0 * mid - left - right)
        if (denominator == 0.0) return peakLag.toDouble() to mid

        val shift = (right - left) / denominator
        // A well-formed peak shifts by less than half a sample. Anything larger means the three points
        // do not describe a maximum, so keep the integer lag rather than trusting the fit.
        if (shift < -0.5 || shift > 0.5) return peakLag.toDouble() to mid

        val height = mid - 0.25 * (left - right) * shift
        return (peakLag + shift) to height
    }
}
