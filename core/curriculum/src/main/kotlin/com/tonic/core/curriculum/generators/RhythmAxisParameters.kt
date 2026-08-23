package com.tonic.core.curriculum.generators

import com.tonic.core.model.rhythm.Meter

/**
 * Concrete generation values for each rhythm difficulty-axis level — docs/40-PHASE-4-SPEC.md §5.2
 * states what each axis *means* but not its numeric tuning, exactly as §5.3 does for the pitch track.
 * The choices live here rather than inside the generator so they are one file to retune once someone
 * has a device and can hear the result, which is the same reason [AxisParameters] exists.
 */
internal object RhythmAxisParameters {
    /**
     * How many bars a pattern runs for at `PATTERN_LENGTH` [level] — §5.2: "1 bar → 4 bars".
     *
     * Five levels over four lengths, so two adjacent levels share a length. Level 3 repeats three bars
     * rather than inventing a fifth length, because the axis's step size is 2: a learner stepping 0 → 2
     * → 4 sees one, three and four bars, and a learner who has to step by one still never faces a jump
     * of more than a bar.
     */
    fun bars(level: Int): Int =
        when (level) {
            0 -> 1
            1 -> 2
            2 -> 3
            3 -> 3
            4 -> 4
            else -> error("PATTERN_LENGTH level out of range: $level")
        }

    /**
     * What fraction of the beats in a pattern are subdivided rather than plain, at `RHYTHMIC_DENSITY`
     * [level] — §5.2.
     *
     * Expressed as a count out of twelve rather than a `Double`, and that is a determinism decision
     * rather than a style one: the generator turns this into "how many of these beats subdivide", and
     * an integer ratio makes the answer identical on every device (CLAUDE.md §5).
     */
    fun subdividedBeatsPerTwelve(level: Int): Int =
        when (level) {
            0 -> 0
            1 -> 3
            2 -> 6
            3 -> 9
            else -> error("RHYTHMIC_DENSITY level out of range: $level")
        }

    /**
     * The meters a pattern may be drawn from at [level] of `PATTERN_LENGTH`.
     *
     * Simple meters only, at every level. Compound meter is `M3.COMPOUND`'s, a node with its own
     * prerequisite (§5.1), and offering it here would put the hardest case of the syllable system in
     * front of a learner before the node that teaches it — the exact "unanswerable rather than harder"
     * failure §3.2 warns about on the fade axis.
     */
    fun meters(level: Int): List<Meter> =
        when (level) {
            0 -> listOf(Meter.FOUR_FOUR)
            1, 2 -> listOf(Meter.FOUR_FOUR, Meter.THREE_FOUR)
            3, 4 -> listOf(Meter.FOUR_FOUR, Meter.THREE_FOUR, Meter.TWO_FOUR)
            else -> error("PATTERN_LENGTH level out of range: $level")
        }
}
