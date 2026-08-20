package com.tonic.core.model.music

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Twelve-tone equal temperament, docs/06-AUDIO-ENGINE.md §6. Lives in
 * `:core:model` rather than `:core:audio` because it's domain logic that
 * the diagnostic's cent-level thresholds (docs/03-CURRICULUM.md §3,
 * `M0.PITCH_DIR`) depend on directly, not just the synthesizer.
 */
object Tuning {
    const val DEFAULT_A4_HZ = 440.0
    private const val A4_MIDI = 69

    /** `f = a4 * 2^((midi - 69) / 12)`. */
    fun midiToHz(
        midi: Int,
        a4Hz: Double = DEFAULT_A4_HZ,
    ): Double = a4Hz * Math.pow(2.0, (midi - A4_MIDI) / 12.0)

    /**
     * Inverse of [midiToHz], rounded to the nearest MIDI note. Used only for
     * round-trip verification — item generation always starts from a MIDI
     * number, never from a measured frequency.
     */
    fun hzToMidi(
        hz: Double,
        a4Hz: Double = DEFAULT_A4_HZ,
    ): Int = (A4_MIDI + 12.0 * (ln(hz / a4Hz) / ln(2.0))).roundToInt()

    /**
     * `f = base * 2^(cents / 1200)`. Needed for `M0.PITCH_DIR`'s
     * sub-semitone trials (docs/03-CURRICULUM.md §3), which probe finer
     * than a single MIDI step once the staircase reaches the semitone
     * floor.
     */
    fun offsetByCents(
        baseHz: Double,
        cents: Double,
    ): Double = baseHz * Math.pow(2.0, cents / 1200.0)

    /** Signed cent distance from [fromHz] to [toHz]. */
    fun centsBetween(
        fromHz: Double,
        toHz: Double,
    ): Double = 1200.0 * (ln(toHz / fromHz) / ln(2.0))
}
