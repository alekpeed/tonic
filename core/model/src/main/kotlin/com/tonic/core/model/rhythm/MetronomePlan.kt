package com.tonic.core.model.rhythm

/**
 * Exactly which metronome clicks sound for one item, and when — docs/40-PHASE-4-SPEC.md §3.2.
 *
 * On one timeline with the pattern starting at tick 0, so **count-in clicks sit at negative ticks**.
 * That is what makes "does the metronome stop when the pattern starts" a readable property of the data
 * rather than a rule spread across a renderer and a UI: everything below zero is the count-in,
 * everything at or above it plays under the learner.
 *
 * The one thing this must never do is leak support the fade level took away. §7.3 records the same
 * hazard on the visual side — "a visual metronome that persists after the audio stops defeats the
 * entire point of the fade axis" — and it is the failure Phase 1 actually hit, where a crutch was
 * silently re-supplied and a level stopped meaning what it said.
 */
public data class MetronomePlan(
    public val clicks: List<MetronomeClick>,
    /**
     * True only at [MetronomeFadeLevel.L7], where the tempo is stated once at the start of a block and
     * no item carries a count-in of its own.
     *
     * Distinct from "no clicks", because it is a different thing to tell the learner: at L7 they were
     * given the tempo and are expected to still have it, which is the skill. A UI that showed nothing
     * would be hiding the exercise rather than presenting it.
     */
    public val announcesTempoAtBlockStart: Boolean,
) {
    /** Clicks before the pattern begins. */
    public val countIn: List<MetronomeClick> get() = clicks.filter { it.tick < 0 }

    /** Clicks that sound while the learner is producing. Empty from [MetronomeFadeLevel.L4] up. */
    public val underPattern: List<MetronomeClick> get() = clicks.filter { it.tick >= 0 }
}

/** One metronome click. [tick] is relative to the pattern's start; negative is count-in. */
public data class MetronomeClick(
    public val tick: Int,
    public val accent: ClickAccent,
)

/**
 * How prominent a click is.
 *
 * Three levels rather than "accented or not" because a learner at [MetronomeFadeLevel.L0] hears
 * subdivisions, beats and downbeats at once, and without three distinguishable sounds the subdivision
 * clicks bury the pulse they exist to support.
 */
public enum class ClickAccent {
    /** Beat 1 of a bar. Where "one" is — the thing `M3.DOWNBEAT` is about. */
    DOWNBEAT,

    /** Any other beat. */
    BEAT,

    /** A division of the beat, below beat level. */
    SUBDIVISION,
}

/**
 * Builds the clicks for a fade level — docs/40-PHASE-4-SPEC.md §3.2's table, as code.
 *
 * Pure and total: every one of the eight levels is handled explicitly, and there is no `else`. The
 * table is the curriculum, so a level that fell through to a default would be a missing rung rather
 * than a missing case.
 */
public object MetronomePlanner {
    /**
     * @param level how much external timekeeping this item gets.
     * @param meter the item's meter — decides where downbeats fall and how a beat divides.
     * @param bars how long the pattern is, so the metronome covers it and stops when it ends.
     */
    public fun plan(
        level: MetronomeFadeLevel,
        meter: Meter,
        bars: Int,
    ): MetronomePlan {
        require(bars >= 1) { "A pattern is at least one bar, was $bars" }
        return MetronomePlan(
            clicks = countInClicks(level, meter) + underPatternClicks(level, meter, bars),
            announcesTempoAtBlockStart = level == MetronomeFadeLevel.L7,
        )
    }

    /**
     * The clicks before tick 0.
     *
     * L0–L2 get a single bar of lead-in rather than nothing. §3.2 calls those levels "continuous
     * throughout", and continuous means the metronome was already running when the pattern began —
     * a learner cannot enter on a pulse that starts at the same instant they are meant to. The lead-in
     * uses the level's own click pattern, so it is the same metronome rather than a different one
     * borrowed for the entrance.
     */
    private fun countInClicks(
        level: MetronomeFadeLevel,
        meter: Meter,
    ): List<MetronomeClick> {
        val beats =
            when (level) {
                // Continuous levels: one bar of the same thing they will hear underneath.
                MetronomeFadeLevel.L0, MetronomeFadeLevel.L1, MetronomeFadeLevel.L2 -> meter.beatsPerBar
                // "Full count-in (2 bars)".
                MetronomeFadeLevel.L3, MetronomeFadeLevel.L4 -> 2 * meter.beatsPerBar
                MetronomeFadeLevel.L5 -> meter.beatsPerBar
                MetronomeFadeLevel.L6 -> 2
                MetronomeFadeLevel.L7 -> 0
            }
        if (beats == 0) return emptyList()

        // Laid out backwards from tick 0 so the last count-in click always lands one beat before the
        // pattern starts, whatever the count is. Counting forwards from a negative start puts the gap
        // in the wrong place for any count that is not a whole number of bars - which is exactly L6.
        return (1..beats)
            .flatMap { beatsBefore ->
                val tick = -beatsBefore * Meter.TICKS_PER_BEAT
                val accent = countInAccent(level, tick, meter)
                listOf(MetronomeClick(tick, accent)) + subdivisionsAfter(level, tick, meter)
            }.sortedBy { it.tick }
    }

    /**
     * Whether a count-in click is a downbeat.
     *
     * Only meaningful when the count-in is whole bars: a two-beat count-in (L6) is the tail of a bar,
     * not a bar of its own, so nothing in it is "one". Marking one of those two clicks as a downbeat
     * would hand the learner the bar position that L6 exists to make them hold.
     */
    private fun countInAccent(
        level: MetronomeFadeLevel,
        tick: Int,
        meter: Meter,
    ): ClickAccent =
        when {
            level == MetronomeFadeLevel.L6 -> ClickAccent.BEAT
            tick % meter.ticksPerBar == 0 -> ClickAccent.DOWNBEAT
            else -> ClickAccent.BEAT
        }

    /** The subdivision clicks between one beat and the next, at L0 only. */
    private fun subdivisionsAfter(
        level: MetronomeFadeLevel,
        beatTick: Int,
        meter: Meter,
    ): List<MetronomeClick> {
        if (level != MetronomeFadeLevel.L0) return emptyList()
        val step = Meter.TICKS_PER_BEAT / meter.division
        return (1 until meter.division).map { MetronomeClick(beatTick + it * step, ClickAccent.SUBDIVISION) }
    }

    /** The clicks from tick 0 to the end of the pattern. Empty from L4 up — that is the whole fade. */
    private fun underPatternClicks(
        level: MetronomeFadeLevel,
        meter: Meter,
        bars: Int,
    ): List<MetronomeClick> {
        val totalTicks = bars * meter.ticksPerBar
        val beatTicks = (0 until totalTicks step Meter.TICKS_PER_BEAT).toList()

        fun accentFor(tick: Int) = if (tick % meter.ticksPerBar == 0) ClickAccent.DOWNBEAT else ClickAccent.BEAT

        return when (level) {
            MetronomeFadeLevel.L0 ->
                beatTicks.flatMap { beat ->
                    listOf(MetronomeClick(beat, accentFor(beat))) + subdivisionsAfter(level, beat, meter)
                }

            MetronomeFadeLevel.L1, MetronomeFadeLevel.L3 ->
                beatTicks.map { MetronomeClick(it, accentFor(it)) }

            MetronomeFadeLevel.L2 ->
                beatTicks
                    .filter { it % meter.ticksPerBar == 0 }
                    .map { MetronomeClick(it, ClickAccent.DOWNBEAT) }

            // The fade. From here the learner keeps time alone, and nothing may be re-supplied.
            MetronomeFadeLevel.L4,
            MetronomeFadeLevel.L5,
            MetronomeFadeLevel.L6,
            MetronomeFadeLevel.L7,
            -> emptyList()
        }
    }
}
