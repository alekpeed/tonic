package com.tonic.core.audio.synth

/**
 * ADSR shared by every timbre — docs/06-AUDIO-ENGINE.md §4. Hard floors:
 * attack >= 5 ms (a zero-length attack is a click — a broadband transient
 * that carries no pitch but does carry attention) and release >= 20 ms with
 * a smooth taper to zero.
 *
 * Segment lengths are clamped to fit short notes without ever violating
 * those floors: attack and release are protected first, sustain shrinks
 * first, then decay, so a very short note degrades to attack-then-release
 * rather than clicking.
 */
data class AdsrEnvelope(
    val attackMs: Long,
    val decayMs: Long,
    val sustainLevel: Float,
    val releaseMs: Long,
) {
    init {
        require(attackMs >= MIN_ATTACK_MS) { "attack must be >= ${MIN_ATTACK_MS}ms, was $attackMs" }
        require(releaseMs >= MIN_RELEASE_MS) { "release must be >= ${MIN_RELEASE_MS}ms, was $releaseMs" }
        require(sustainLevel in 0f..1f) { "sustainLevel must be 0..1, was $sustainLevel" }
    }

    /** One multiplier per sample, length [totalSamples]. */
    fun render(
        totalSamples: Int,
        sampleRate: Int,
    ): FloatArray {
        val out = FloatArray(totalSamples)
        if (totalSamples == 0) return out

        var attackSamples = msToSamples(attackMs, sampleRate).coerceAtLeast(1)
        var releaseSamples = msToSamples(releaseMs, sampleRate).coerceAtLeast(1)

        // Protect attack + release first; if the note is too short even for
        // those, split it proportionally rather than clicking.
        if (attackSamples + releaseSamples > totalSamples) {
            val scale = totalSamples.toDouble() / (attackSamples + releaseSamples)
            attackSamples = (attackSamples * scale).toInt().coerceAtLeast(1)
            releaseSamples = (totalSamples - attackSamples).coerceAtLeast(1)
        }

        var decaySamples = msToSamples(decayMs, sampleRate).coerceAtLeast(0)
        val remainingAfterAttackRelease = totalSamples - attackSamples - releaseSamples
        if (decaySamples > remainingAfterAttackRelease) decaySamples = remainingAfterAttackRelease
        val sustainSamples = remainingAfterAttackRelease - decaySamples

        var i = 0
        for (s in 0 until attackSamples) {
            out[i++] = (s + 1).toFloat() / attackSamples
        }
        for (s in 0 until decaySamples) {
            val t = (s + 1).toFloat() / decaySamples
            out[i++] = 1f + (sustainLevel - 1f) * t
        }
        for (s in 0 until sustainSamples) {
            out[i++] = sustainLevel
        }
        val releaseStartLevel = if (decaySamples > 0 || sustainSamples > 0) sustainLevel else 1f
        for (s in 0 until releaseSamples) {
            val t = (s + 1).toFloat() / releaseSamples
            out[i++] = releaseStartLevel * (1f - t)
        }
        return out
    }

    companion object {
        const val MIN_ATTACK_MS = 5L
        const val MIN_RELEASE_MS = 20L

        private fun msToSamples(
            ms: Long,
            sampleRate: Int,
        ): Int = ((ms * sampleRate) / 1000L).toInt()
    }
}
