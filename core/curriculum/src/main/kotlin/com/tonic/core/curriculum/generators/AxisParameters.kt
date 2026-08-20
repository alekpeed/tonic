package com.tonic.core.curriculum.generators

import com.tonic.core.model.music.TimbreId

/**
 * Concrete generation parameters for each M2 difficulty-axis level —
 * docs/03-CURRICULUM.md §5.3 states what each axis *means*, but not exact
 * numeric tuning; these are the implementer's concrete choices, documented
 * here rather than scattered through the generator so they're easy to
 * retune once a human is listening to the result (docs/09-BUILD-PLAN.md
 * Stage 2's manual gate).
 */
object AxisParameters {
    /**
     * Timbre pool per `TIMBRE_VARIETY` level (docs/03-CURRICULUM.md §5.3:
     * "0 = one fixed timbre; 4 = all families, randomized per item,
     * reference and target may differ"). At level 4 only, the reference and
     * target are sampled independently — every other level uses the same
     * timbre for both, per item.
     */
    fun timbrePool(level: Int): List<TimbreId> =
        when (level) {
            0 -> listOf(TimbreId.PURE)
            1 -> listOf(TimbreId.PURE, TimbreId.SOFT)
            2 -> listOf(TimbreId.PURE, TimbreId.SOFT, TimbreId.REED)
            3, 4 -> TimbreId.entries.toList()
            else -> error("TIMBRE_VARIETY level out of range: $level")
        }

    /** Whether the reference and target may use different timbres at this level. */
    fun independentReferenceTimbre(level: Int): Boolean = level == 4

    /**
     * MIDI register the target is drawn from — docs/03-CURRICULUM.md §5.3
     * plus docs/06-AUDIO-ENGINE.md §9: levels 0-1 are constrained to
     * MIDI 55-84, the range phone speakers reproduce acceptably, since
     * headphone use is encouraged but not gated on.
     */
    fun registerRange(level: Int): IntRange =
        when (level) {
            0, 1 -> 55..84
            2 -> 48..91
            3 -> 40..96
            else -> error("REGISTER_SPREAD level out of range: $level")
        }

    /**
     * Allowed octave offsets of the target from the reference's octave —
     * docs/03-CURRICULUM.md §5.3: "0 = target within reference octave;
     * 1 = +/-1 octave; 2 = +/-2 octaves."
     */
    fun octaveOffsets(level: Int): List<Int> =
        when (level) {
            0 -> listOf(0)
            1 -> listOf(-1, 0, 1)
            2 -> listOf(-2, -1, 0, 1, 2)
            else -> error("OCTAVE_DISPLACE level out of range: $level")
        }

    /** Reference-tone/target/gap durations, in ms. Faster/shorter is harder — docs/03-CURRICULUM.md §5.3. */
    fun timing(level: Int): TempoTiming =
        when (level) {
            0 -> TempoTiming(referenceDurationMs = 600, gapAfterReferenceMs = 500, targetDurationMs = 900)
            1 -> TempoTiming(referenceDurationMs = 500, gapAfterReferenceMs = 400, targetDurationMs = 750)
            2 -> TempoTiming(referenceDurationMs = 400, gapAfterReferenceMs = 250, targetDurationMs = 600)
            3 -> TempoTiming(referenceDurationMs = 300, gapAfterReferenceMs = 150, targetDurationMs = 500)
            else -> error("TEMPO_DENSITY level out of range: $level")
        }

    /**
     * Key pool per `KEY_SPREAD` level — docs/03-CURRICULUM.md §5.3: "0 = key
     * drawn from 3 keys; 1 = 7 keys; 2 = all 12." Level 0/1 pools are the
     * subsets of level 2's full chromatic pool, not arbitrary picks, so
     * widening the axis only *adds* keys the learner has already seen.
     */
    fun keyPool(level: Int): List<Int> =
        when (level) {
            0 -> listOf(0, 5, 7) // C, F, G — tonic/subdominant/dominant relationship
            1 -> listOf(0, 2, 4, 5, 7, 9, 11) // the seven "white key" majors
            2 -> (0..11).toList()
            else -> error("KEY_SPREAD level out of range: $level")
        }
}

data class TempoTiming(
    val referenceDurationMs: Long,
    val gapAfterReferenceMs: Long,
    val targetDurationMs: Long,
)
