package com.tonic.core.model.rhythm

/**
 * One rhythm to hear or to tap back — docs/40-PHASE-4-SPEC.md §5 and §6.1.
 *
 * A pattern is its **onsets**: the metric positions where a sound starts. Rests are not events, they
 * are the absence of one, which is why `M3.RESTS` needs no representation of its own — a bar with a
 * gap in it is a bar whose onset list skips a position. Modelling silence explicitly would create two
 * ways to write the same rhythm and give scoring a distinction the learner cannot hear.
 *
 * Positions are integer ticks from the start of the pattern, at [Meter.TICKS_PER_BEAT] to the beat, so
 * a pattern is exactly comparable and byte-identical across runs (CLAUDE.md §5). Milliseconds appear
 * only at render time, where tempo is applied.
 *
 * @property meter how the beats group and divide.
 * @property bars how many bars long the pattern is — the `PATTERN_LENGTH` axis.
 * @property onsetTicks every sounding onset, ascending, distinct, inside the pattern.
 */
public data class RhythmPattern(
    public val meter: Meter,
    public val bars: Int,
    public val onsetTicks: List<Int>,
) {
    init {
        require(bars >= 1) { "A pattern is at least one bar, was $bars" }
        require(onsetTicks.isNotEmpty()) { "A pattern with no onsets is silence, not a rhythm" }
        require(onsetTicks == onsetTicks.sorted()) { "Onsets must be ascending: $onsetTicks" }
        require(onsetTicks.distinct().size == onsetTicks.size) { "Two onsets at one position: $onsetTicks" }
        require(onsetTicks.first() >= 0 && onsetTicks.last() < totalTicks) {
            "Onsets must lie inside the pattern's $totalTicks ticks: $onsetTicks"
        }
    }

    /** Length of the whole pattern in ticks. */
    public val totalTicks: Int get() = bars * meter.ticksPerBar

    /** How many sounds the learner has to produce. The count `M3`'s pattern accuracy is measured against. */
    public val onsetCount: Int get() = onsetTicks.size

    /** True when every onset falls exactly on a beat — the `M3.BEAT_FIND` and `M3.DOWNBEAT` shape. */
    public val isOnBeatsOnly: Boolean get() = onsetTicks.all { it % Meter.TICKS_PER_BEAT == 0 }

    /**
     * Which beat of the whole pattern [tick] falls in, counting from zero.
     *
     * Exposed because "which beat was that" is the question both the Takadimi label and the downbeat
     * exercises ask, and deriving it at each call site is how two call sites come to disagree.
     */
    public fun beatIndexOf(tick: Int): Int = tick / Meter.TICKS_PER_BEAT

    /** True when [tick] is the first beat of a bar — where "one" is. */
    public fun isDownbeat(tick: Int): Boolean = tick % meter.ticksPerBar == 0

    /**
     * The Takadimi syllable for the onset at [tick], given the finest division this pattern actually
     * uses — docs/40-PHASE-4-SPEC.md §3.1.
     *
     * The division is taken from the pattern rather than from the meter on purpose. A learner tapping
     * plain beats says "ta ta ta ta"; the same four sounds in a pattern that elsewhere subdivides are
     * still "ta", because Takadimi names position within the beat and the beat has not moved. Passing
     * the whole pattern's division is what makes that come out right.
     */
    public fun syllableAt(tick: Int): String {
        val parts = finestDivision
        val within = (tick % Meter.TICKS_PER_BEAT) / (Meter.TICKS_PER_BEAT / parts)
        return Takadimi.syllableAt(parts, within)
    }

    /**
     * How many equal parts of the beat this pattern needs to name every one of its onsets.
     *
     * The smallest supported division that every onset lands on. A pattern of plain beats needs 1; add
     * one off-beat sound and it needs 2. Smallest rather than largest because the syllables a learner
     * is asked to say should be the simplest set that describes what they heard.
     */
    public val finestDivision: Int
        get() =
            Takadimi.supportedDivisions
                .sorted()
                .firstOrNull { parts -> onsetTicks.all { it % (Meter.TICKS_PER_BEAT / parts) == 0 } }
                ?: throw IllegalStateException("No supported Takadimi division describes $onsetTicks")
}
