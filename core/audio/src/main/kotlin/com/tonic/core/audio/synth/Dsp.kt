package com.tonic.core.audio.synth

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Shared signal-chain math: dB conversion, RMS loudness matching, mixing,
 * and the soft limiter — docs/06-AUDIO-ENGINE.md §2. "Mix at -12 dBFS
 * nominal. A soft limiter catches sums; hard clipping must never occur,
 * because clipping generates harmonics that are themselves a pitch cue."
 */
object Dsp {
    const val NOMINAL_HEADROOM_DB = -12.0
    val NOMINAL_HEADROOM_LINEAR = dbToLinear(NOMINAL_HEADROOM_DB).toFloat()

    fun dbToLinear(db: Double): Double = Math.pow(10.0, db / 20.0)

    fun linearToDb(linear: Double): Double = 20.0 * (ln(max(linear, 1e-12)) / ln(10.0))

    fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (s in samples) sumSquares += s.toDouble() * s.toDouble()
        return sqrt(sumSquares / samples.size)
    }

    fun peakAbs(samples: FloatArray): Float {
        var peak = 0f
        for (s in samples) peak = max(peak, abs(s))
        return peak
    }

    /**
     * Scales [samples] in place so their RMS equals [targetRms]. This is
     * the concrete mechanism behind docs/06-AUDIO-ENGINE.md §3's "Normalize
     * by an RMS ... per timbre per register" — cross-timbre loudness
     * matching is enforced by construction, not tuned by ear alone (the
     * required *perceptual* check still happens at the Stage 2 manual gate,
     * docs/09-BUILD-PLAN.md).
     */
    fun normalizeToRms(
        samples: FloatArray,
        targetRms: Double,
    ): FloatArray {
        val currentRms = rms(samples)
        if (currentRms < 1e-9) return samples
        val gain = (targetRms / currentRms).toFloat()
        return FloatArray(samples.size) { samples[it] * gain }
    }

    /** Sums [voices] sample-by-sample; buffers may differ in length (shorter ones are zero-padded). */
    fun mix(voices: List<FloatArray>): FloatArray {
        if (voices.isEmpty()) return FloatArray(0)
        val length = voices.maxOf { it.size }
        val out = FloatArray(length)
        for (voice in voices) {
            for (i in voice.indices) out[i] += voice[i]
        }
        return out
    }

    /**
     * A soft (tanh) limiter. Below [threshold] this is transparent (near
     * identity); above it, amplitude compresses asymptotically toward 1.0
     * so `max(abs(sample))` never reaches or exceeds 1.0 — hard clipping
     * must never occur (docs/06-AUDIO-ENGINE.md §2).
     */
    fun softLimit(
        samples: FloatArray,
        threshold: Float = 0.7f,
    ): FloatArray =
        FloatArray(samples.size) { i ->
            val x = samples[i]
            val a = abs(x)
            if (a <= threshold) {
                x
            } else {
                val sign = if (x < 0f) -1f else 1f
                val over = (a - threshold) / (1f - threshold)
                // tanh() saturates to exactly 1.0f in float precision for large `over`, which would make
                // `compressed` equal exactly 1.0f — computed in Double and pulled back off the ceiling by
                // CLIP_MARGIN, so "hard clipping must never occur" holds as a hard guarantee, not just
                // a near-certainty.
                val compressed = threshold + (1f - threshold) * kotlin.math.tanh(over.toDouble()).toFloat()
                (sign * compressed).coerceIn(-CLIP_MARGIN, CLIP_MARGIN)
            }
        }

    private const val CLIP_MARGIN = 0.999999f
}
