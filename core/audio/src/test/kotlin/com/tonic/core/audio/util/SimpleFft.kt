package com.tonic.core.audio.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * A small in-place radix-2 Cooley-Tukey FFT, kept in the test source set
 * rather than a production dependency — docs/10-TESTING.md §6: "Implement
 * a small FFT in the test source set rather than adding a production
 * dependency." Used only to verify [com.tonic.core.audio.timbre.TimbreBank] /
 * [com.tonic.core.audio.synth.SynthEngine] output; it has no runtime role.
 */
object SimpleFft {
    /** In-place FFT. [real]/[imag] length must be a power of 2. */
    fun transform(
        real: DoubleArray,
        imag: DoubleArray,
    ) {
        val n = real.size
        require(n and (n - 1) == 0) { "FFT length must be a power of 2, was $n" }
        if (n <= 1) return

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wReal = cos(angle)
            val wImag = sin(angle)
            var i = 0
            while (i < n) {
                var curReal = 1.0
                var curImag = 0.0
                for (k in 0 until len / 2) {
                    val evenIdx = i + k
                    val oddIdx = i + k + len / 2
                    val tReal = real[oddIdx] * curReal - imag[oddIdx] * curImag
                    val tImag = real[oddIdx] * curImag + imag[oddIdx] * curReal
                    real[oddIdx] = real[evenIdx] - tReal
                    imag[oddIdx] = imag[evenIdx] - tImag
                    real[evenIdx] += tReal
                    imag[evenIdx] += tImag
                    val nextReal = curReal * wReal - curImag * wImag
                    val nextImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                    curImag = nextImag
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Smallest power of 2 >= [n]. */
    fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    fun hannWindow(size: Int): DoubleArray = DoubleArray(size) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (size - 1)) }

    /**
     * Dominant frequency in [samples] (a segment, ideally the steady-state
     * portion away from onset/release transients), by FFT peak-bin plus
     * quadratic interpolation across the three bins around the peak — the
     * "FFT, find peak bin, interpolate" method docs/10-TESTING.md §6 names.
     * Bin spacing alone (sampleRate/fftSize) is far coarser than the
     * required 1-cent tolerance; the interpolation is what gets sub-bin
     * accuracy.
     */
    fun dominantFrequency(
        samples: FloatArray,
        sampleRate: Int,
    ): Double {
        val fftSize = nextPowerOfTwo(samples.size)
        val window = hannWindow(samples.size)
        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)
        for (i in samples.indices) real[i] = samples[i] * window[i]

        transform(real, imag)

        val magnitudes = DoubleArray(fftSize / 2) { k -> Math.hypot(real[k], imag[k]) }
        var peakBin = 1
        for (k in 2 until magnitudes.size) {
            if (magnitudes[k] > magnitudes[peakBin]) peakBin = k
        }

        // Quadratic (parabolic) interpolation on the log-magnitude around the peak.
        val alpha = ln(magnitudes[(peakBin - 1).coerceAtLeast(0)] + 1e-12)
        val beta = ln(magnitudes[peakBin] + 1e-12)
        val gamma = ln(magnitudes[(peakBin + 1).coerceAtMost(magnitudes.size - 1)] + 1e-12)
        val denom = alpha - 2 * beta + gamma
        val p = if (denom == 0.0) 0.0 else 0.5 * (alpha - gamma) / denom

        val binHz = sampleRate.toDouble() / fftSize
        return (peakBin + p) * binHz
    }
}
