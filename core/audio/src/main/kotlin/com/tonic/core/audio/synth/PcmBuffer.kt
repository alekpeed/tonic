package com.tonic.core.audio.synth

/**
 * Mono, 32-bit float PCM at [sampleRate] Hz — docs/06-AUDIO-ENGINE.md §2:
 * "Internal processing: 32-bit float, mono synthesis, duplicated to stereo
 * at output." Everything in `synth`/`timbre` produces and consumes this;
 * only the `player` package touches actual output framing.
 */
data class PcmBuffer(
    val samples: FloatArray,
    val sampleRate: Int,
) {
    val durationSamples: Int get() = samples.size
    val durationMs: Double get() = 1000.0 * samples.size / sampleRate

    override fun equals(other: Any?): Boolean =
        other is PcmBuffer && sampleRate == other.sampleRate && samples.contentEquals(other.samples)

    override fun hashCode(): Int = 31 * sampleRate + samples.contentHashCode()

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000

        fun silence(
            durationSamples: Int,
            sampleRate: Int = DEFAULT_SAMPLE_RATE,
        ): PcmBuffer = PcmBuffer(FloatArray(durationSamples), sampleRate)

        fun msToSamples(
            ms: Long,
            sampleRate: Int = DEFAULT_SAMPLE_RATE,
        ): Int = ((ms * sampleRate) / 1000L).toInt()
    }
}
