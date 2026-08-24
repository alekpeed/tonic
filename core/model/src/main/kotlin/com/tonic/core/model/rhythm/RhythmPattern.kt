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
    /**
     * A second meter that takes over part-way through — `M3.METER_CHANGE`, docs/40-PHASE-4-SPEC.md
     * §5.1. Null for every other pattern, which is all of them until that node.
     *
     * **The beat does not change, only the grouping.** That is what makes this a small addition
     * rather than a second time system: a beat is [Meter.TICKS_PER_BEAT] ticks in both meters, so
     * every onset position, every Takadimi syllable and every metronome beat-click is unaffected.
     * What moves is where the bar turns over, which is exactly the thing the learner has to hear.
     */
    public val changesTo: MeterChange? = null,
) {
    init {
        require(bars >= 1) { "A pattern is at least one bar, was $bars" }
        require(onsetTicks.isNotEmpty()) { "A pattern with no onsets is silence, not a rhythm" }
        require(onsetTicks == onsetTicks.sorted()) { "Onsets must be ascending: $onsetTicks" }
        require(onsetTicks.distinct().size == onsetTicks.size) { "Two onsets at one position: $onsetTicks" }
        require(onsetTicks.first() >= 0 && onsetTicks.last() < totalTicks) {
            "Onsets must lie inside the pattern's $totalTicks ticks: $onsetTicks"
        }
        changesTo?.let { change ->
            require(change.atBar < bars) { "A change at bar ${change.atBar} never happens in $bars bars" }
            require(change.meter != meter) { "A change to the same meter is not a change" }
        }
    }

    /** Length of the whole pattern in ticks, both meters counted. */
    public val totalTicks: Int
        get() {
            val change = changesTo ?: return bars * meter.ticksPerBar
            return change.atBar * meter.ticksPerBar + (bars - change.atBar) * change.meter.ticksPerBar
        }

    /** The tick the bar at [barIndex] starts on. */
    public fun barStartTick(barIndex: Int): Int {
        require(barIndex in 0..bars) { "Bar $barIndex is outside a $bars-bar pattern" }
        val change = changesTo ?: return barIndex * meter.ticksPerBar
        if (barIndex <= change.atBar) return barIndex * meter.ticksPerBar
        return change.atBar * meter.ticksPerBar + (barIndex - change.atBar) * change.meter.ticksPerBar
    }

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

    /**
     * True when [tick] is the first beat of a bar — where "one" is.
     *
     * Walks the bars rather than taking a remainder, because with a meter change the bars are not all
     * the same length and a single modulus would put "one" in the wrong place from the change onward.
     */
    public fun isDownbeat(tick: Int): Boolean = (0 until bars).any { barStartTick(it) == tick }

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

/**
 * Where a pattern changes meter, and to what — `M3.METER_CHANGE`.
 *
 * @property atBar the first bar in the new meter, counting from zero. Never zero: a pattern that
 *   "changes" at its first bar is simply a pattern in the second meter, and allowing it would give two
 *   spellings of one rhythm.
 * @property meter what it changes to. Never equal to the pattern's own, for the same reason.
 */
public data class MeterChange(
    public val atBar: Int,
    public val meter: Meter,
) {
    init {
        require(atBar >= 1) { "A meter change at bar 0 is just a pattern in that meter" }
    }
}
