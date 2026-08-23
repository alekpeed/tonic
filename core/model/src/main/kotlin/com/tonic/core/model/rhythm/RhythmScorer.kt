package com.tonic.core.model.rhythm

import kotlin.math.abs

/**
 * Scores one tapped attempt — docs/40-PHASE-4-SPEC.md §6, and §4.4's determinism rule.
 *
 * §4.4 states the contract this exists to hold: "Scoring is a pure function of
 * `(pattern, tapTimestamps, calibrationOffset, tolerance)`. No clock reads inside scoring, no ambient
 * state. Given the same recorded taps, scoring must produce byte-identical results forever." That is
 * what makes a real session replayable and a scoring bug reproducible offline, and it is the same
 * trick Phase 1 played with the item seed, extended to time.
 *
 * So there is no clock here, no randomness, and no state. Everything the score depends on arrives as
 * an argument.
 */
public object RhythmScorer {
    /**
     * @param pattern what the learner was asked to play.
     * @param tapMonotonicNanos every recorded touch, on the same clock as [patternStartNanos].
     * @param patternStartNanos when the pattern's first event actually sounded — from the output
     *   timebase, not from when playback was requested (§4.1).
     * @param beatMs the item's beat length, from its tempo.
     * @param toleranceLevel the `TIMING_TOLERANCE` axis level.
     * @param calibrationOffsetMs the learner's constant for this route (§4.3). Positive means they tap
     *   late; it is subtracted, so a uniformly late learner lands on the beat.
     */
    public fun score(
        pattern: RhythmPattern,
        tapMonotonicNanos: List<Long>,
        patternStartNanos: Long,
        beatMs: Double,
        toleranceLevel: Int,
        calibrationOffsetMs: Double,
    ): RhythmScore {
        // Rebased here, corrected in scoreRelative. The offset is deliberately *not* applied twice:
        // this call passes zero, and the one place that subtracts it is below.
        val taps =
            TapTimeline.relativeToPatternMs(
                taps = tapMonotonicNanos.map { TapEvent(it) },
                patternStartNanos = patternStartNanos,
                calibrationOffsetMs = 0.0,
            )
        return scoreRelative(pattern, taps, beatMs, toleranceLevel, calibrationOffsetMs)
    }

    /**
     * The same scoring, over taps already rebased onto the pattern's timeline but **not yet
     * corrected**.
     *
     * Separate from [score] because rebasing and matching are different concerns and each is worth
     * testing without the other — and because a caller replaying a stored attempt already has
     * millisecond times and should not have to invent nanosecond instants to feed them back in.
     *
     * [calibrationOffsetMs] is applied here, not merely recorded. An earlier draft took it, stored it
     * on the result and matched against uncorrected taps, so a caller could hand it a learner's
     * constant and be told they had missed every event — the offset silently doing nothing. The test
     * that caught it is `at a fast tempo the same learner needs calibration to pass at all`, and it is
     * kept pointed at this function for that reason.
     */
    public fun scoreRelative(
        pattern: RhythmPattern,
        tapTimesMs: List<Double>,
        beatMs: Double,
        toleranceLevel: Int,
        calibrationOffsetMs: Double = 0.0,
    ): RhythmScore {
        val halfWidth = ToleranceWindows.halfWidthMs(toleranceLevel, beatMs, pattern)
        val msPerTick = beatMs / Meter.TICKS_PER_BEAT
        val expected = pattern.onsetTicks.map { it * msPerTick }

        // Positive means the learner taps late, so removing it means subtracting - the same convention
        // and the same direction as TapTimeline, and stated in both places because a sign convention
        // written down once is a sign convention waiting to be inverted.
        val corrected = tapTimesMs.map { it - calibrationOffsetMs }
        val assignment = assign(expected, corrected.sorted(), halfWidth)

        return RhythmScore(
            matches =
                expected.mapIndexed { index, ms ->
                    EventMatch(expectedMs = ms, asynchronyMs = assignment.matchedTapFor[index]?.minus(ms))
                },
            extraTaps = assignment.unmatchedTaps,
            toleranceHalfWidthMs = halfWidth,
            calibrationOffsetMs = calibrationOffsetMs,
        )
    }

    /**
     * Pairs taps with events, at most one apiece.
     *
     * Closest pair first, rather than sweeping left to right. A left-to-right sweep gives the first
     * event whichever tap it meets, so one early stray tap consumes the slot its neighbour needed and
     * every later pairing shifts — turning one mistake into a whole pattern scored wrong. Taking the
     * tightest pair first means a stray is left over as an extra tap, which is what it is.
     *
     * Ties are broken by earlier event, then earlier tap, so the result does not depend on iteration
     * order. Without that, two taps equidistant from one event would score differently on different
     * runs, and §4.4's byte-identical promise would be false in exactly the case hardest to reproduce.
     */
    private fun assign(
        expected: List<Double>,
        taps: List<Double>,
        halfWidthMs: Double,
    ): Assignment {
        val candidates =
            expected.indices
                .flatMap { eventIndex ->
                    taps.indices.mapNotNull { tapIndex ->
                        val distance = abs(taps[tapIndex] - expected[eventIndex])
                        if (distance <= halfWidthMs) Candidate(eventIndex, tapIndex, distance) else null
                    }
                }.sortedWith(compareBy({ it.distance }, { it.eventIndex }, { it.tapIndex }))

        val matchedTapFor = arrayOfNulls<Double>(expected.size)
        val usedTaps = mutableSetOf<Int>()
        val usedEvents = mutableSetOf<Int>()
        for (candidate in candidates) {
            if (candidate.eventIndex in usedEvents || candidate.tapIndex in usedTaps) continue
            usedEvents += candidate.eventIndex
            usedTaps += candidate.tapIndex
            matchedTapFor[candidate.eventIndex] = taps[candidate.tapIndex]
        }

        return Assignment(
            matchedTapFor = matchedTapFor.toList(),
            unmatchedTaps = taps.filterIndexed { index, _ -> index !in usedTaps },
        )
    }

    private data class Candidate(
        val eventIndex: Int,
        val tapIndex: Int,
        val distance: Double,
    )

    private data class Assignment(
        val matchedTapFor: List<Double?>,
        val unmatchedTaps: List<Double>,
    )
}
