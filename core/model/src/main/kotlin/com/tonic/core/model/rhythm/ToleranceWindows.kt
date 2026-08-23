package com.tonic.core.model.rhythm

/**
 * How close a tap has to be to count as landing on an event — docs/40-PHASE-4-SPEC.md §6.2.
 *
 * **A fraction of the beat, never a fixed number of milliseconds.** §6.2 gives the reason in one line:
 * 50 ms is generous at 60 BPM and impossible at 200. A window in milliseconds would silently make the
 * `TEMPO_DEVIATION` axis a second difficulty axis, so that moving tempo changed how strictly the
 * learner was judged without anything saying so.
 */
public object ToleranceWindows {
    /**
     * Half-width of the window around each expected event, in milliseconds.
     *
     * @param level the `TIMING_TOLERANCE` level, 0 (most forgiving) to 3.
     * @param beatMs how long a beat lasts at this item's tempo.
     * @param pattern the pattern being scored — needed because the clamp below depends on how crowded
     *   its events are, not on the level alone.
     *
     * The clamp is §6.2's third bullet and it is not an edge case: "at high densities and tight tempi,
     * adjacent events can crowd; when windows would overlap, the tolerance is clamped rather than
     * allowing one tap to match two events." Two events a sixteenth apart at 140 BPM are 107 ms apart,
     * and level 0's quarter-beat window is 107 ms wide on its own — without the clamp a single tap
     * would sit inside both, and whichever event it was credited to would be an artifact of iteration
     * order rather than of what the learner played.
     */
    public fun halfWidthMs(
        level: Int,
        beatMs: Double,
        pattern: RhythmPattern,
    ): Double {
        require(beatMs > 0) { "A beat must have positive duration, was $beatMs" }
        val requested = beatFractionFor(level) * beatMs
        val closest = closestEventGapMs(pattern, beatMs) ?: return requested
        // Half the smallest gap: at exactly that, two neighbouring windows meet at a point and stop
        // short of overlapping. RhythmScorer resolves a tap landing precisely on the boundary by
        // nearest-then-earliest, so even that single instant maps to one event rather than two.
        return minOf(requested, closest / 2.0)
    }

    /**
     * The window as a fraction of one beat, per `TIMING_TOLERANCE` level.
     *
     * ⚠️ Reasoned, not measured. Level 0's quarter-beat is deliberately loose because §6.2 says the
     * early question is "did you feel the pattern," not "are you a session drummer"; level 3's twelfth
     * of a beat is 50 ms at 100 BPM, which is tight without being drummer-tight. What would move these
     * is device data on the spread real learners produce — §4.3 already measures exactly that per
     * learner, and Stage 4.1's owed measurement is the input.
     */
    public fun beatFractionFor(level: Int): Double {
        require(level in FRACTIONS.indices) { "TIMING_TOLERANCE is 0..${FRACTIONS.size - 1}, was $level" }
        return FRACTIONS[level]
    }

    /** The smallest gap between two adjacent expected events, or null if there is only one. */
    private fun closestEventGapMs(
        pattern: RhythmPattern,
        beatMs: Double,
    ): Double? {
        val msPerTick = beatMs / Meter.TICKS_PER_BEAT
        return pattern.onsetTicks
            .zipWithNext { a, b -> (b - a) * msPerTick }
            .minOrNull()
    }

    private val FRACTIONS = doubleArrayOf(0.25, 0.1875, 0.125, 0.0833)
}
