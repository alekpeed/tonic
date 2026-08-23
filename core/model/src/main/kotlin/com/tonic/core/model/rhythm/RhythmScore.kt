package com.tonic.core.model.rhythm

/**
 * What one tapped attempt produced — docs/40-PHASE-4-SPEC.md §6.
 *
 * The two dimensions §6.1 insists on keeping apart are kept apart here, and the separation is the
 * point: "a learner who taps the correct rhythm slightly loosely is *right, imprecisely*. A learner who
 * taps a different rhythm precisely is simply wrong." Collapsing them into one number loses exactly
 * the distinction the app most needs to make.
 *
 * @property matches one entry per expected event, in order — see [EventMatch].
 * @property extraTaps taps that matched no event, in milliseconds from the pattern's start. Recorded
 *   separately from misses because §6.2 says the two "mean different things pedagogically": a learner
 *   adding sounds has heard a different rhythm, one dropping them may simply have hesitated.
 * @property toleranceHalfWidthMs the window actually applied, after §6.2's crowding clamp. Recorded
 *   rather than recomputed, so a stored attempt can be re-scored later against exactly what it faced.
 * @property calibrationOffsetMs the constant subtracted from every tap before scoring (§4.3).
 */
public data class RhythmScore(
    public val matches: List<EventMatch>,
    public val extraTaps: List<Double>,
    public val toleranceHalfWidthMs: Double,
    public val calibrationOffsetMs: Double,
) {
    /** Expected events that a tap landed on. */
    public val matchedCount: Int get() = matches.count { it.asynchronyMs != null }

    /** Expected events nothing landed on. */
    public val missedCount: Int get() = matches.size - matchedCount

    /** Taps that landed on nothing. */
    public val extraCount: Int get() = extraTaps.size

    /**
     * The primary score — §6.1: "did the right number of events occur in the right metric positions?"
     *
     * Measured against whichever is larger, the events expected or the taps given, so that adding
     * sounds costs exactly as much as dropping them. Dividing by the expected count alone would let a
     * learner who tapped continuously score perfectly: every event would be covered, and the flurry
     * between them would be free.
     */
    public val patternAccuracy: Double
        get() {
            val denominator = maxOf(matches.size, matchedCount + extraCount)
            return if (denominator == 0) 1.0 else matchedCount.toDouble() / denominator
        }

    /**
     * Whether this attempt reproduced the pattern — every event struck, nothing added.
     *
     * This is what the staircase reads. Note what it does *not* read: how far inside the window each
     * tap fell. §6.3 forbids raw asynchrony from entering the staircase or mastery, and the way that
     * guarantee is kept is by there being no path from an asynchrony to this value at all —
     * `RawAsynchronyIsNeverScoredTest` asserts it rather than trusting it.
     */
    public val isCorrect: Boolean get() = missedCount == 0 && extraCount == 0

    /**
     * Whether the learner is progressively rushing or dragging, in milliseconds per beat —
     * docs/40-PHASE-4-SPEC.md §5.3 criterion 3.
     *
     * The slope of asynchrony across the pattern, not its magnitude, and that distinction is the whole
     * criterion: "a constant offset is a calibration artifact while a growing offset is a real
     * timekeeping failure." A learner uniformly 40 ms late has a drift of zero and should master; a
     * learner who starts on the beat and ends half a beat early has not kept time, however small their
     * average error.
     *
     * Null when fewer than two events were matched, because a trend through one point is not a trend.
     *
     * Dimensionless on purpose: milliseconds of error per millisecond elapsed. Use [driftMsPerBeat] to
     * put it in units a person can read. An earlier draft called this `driftMsPerBeat` and returned
     * this same ratio, which was a name that lied about its units — the sort of thing that survives
     * until someone builds a threshold on it.
     */
    public val driftSlope: Double?
        get() {
            val points = matches.mapNotNull { m -> m.asynchronyMs?.let { m.expectedMs to it } }
            if (points.size < 2) return null
            val meanX = points.sumOf { it.first } / points.size
            val meanY = points.sumOf { it.second } / points.size
            val varianceX = points.sumOf { (it.first - meanX) * (it.first - meanX) }
            if (varianceX == 0.0) return null
            val covariance = points.sumOf { (it.first - meanX) * (it.second - meanY) }
            return covariance / varianceX
        }

    /**
     * [driftSlope] expressed as milliseconds of error accumulated per beat — the readable form, and
     * the one a mastery threshold should be written against.
     *
     * A learner drifting 8 ms per beat is a third of a beat out by the end of a four-bar pattern at
     * common time, which is audible; the same slope stated as 0.013 is not something anyone can judge.
     */
    public fun driftMsPerBeat(beatMs: Double): Double? {
        require(beatMs > 0) { "A beat must have positive duration, was $beatMs" }
        return driftSlope?.times(beatMs)
    }
}

/**
 * One expected event, and the tap that landed on it if any.
 *
 * @property expectedMs when the event should have sounded, in milliseconds from the pattern's start.
 * @property asynchronyMs how far the tap was from it, signed — positive is late. Null when the event
 *   was missed. **Recorded and shown, never scored** (§6.3).
 */
public data class EventMatch(
    public val expectedMs: Double,
    public val asynchronyMs: Double?,
)
