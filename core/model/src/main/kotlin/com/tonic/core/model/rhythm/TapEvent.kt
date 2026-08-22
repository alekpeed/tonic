package com.tonic.core.model.rhythm

/**
 * One touch, timestamped where it happened — docs/40-PHASE-4-SPEC.md §4.4.
 *
 * "Tap timestamps are captured at the input layer and passed in as data," in the spec's words. The
 * whole point of carrying the time on the event rather than reading a clock further in is that
 * everything downstream then becomes a pure function of recorded values: §4.4 requires scoring to be
 * "a pure function of `(pattern, tapTimestamps, calibrationOffset, tolerance)`" with "no clock reads
 * inside scoring," so that a recorded session replays byte-identically and a scoring bug is
 * reproducible offline. That is the same trick Phase 1 played with the item seed, extended to time.
 *
 * @property monotonicNanos when the touch happened, on a monotonic clock — `System.nanoTime`, or the
 *   event time Android's input dispatch already recorded, which is earlier and better. Monotonic and
 *   not wall-clock: a wall-clock jump mid-pattern would show up as a rhythm error, and the learner
 *   would have no idea why. The same clock must timestamp [OutputTimebase.presentationNanos], or the
 *   difference between them measures the gap between two clocks rather than the gap between a sound
 *   and a finger.
 */
@JvmInline
public value class TapEvent(
    public val monotonicNanos: Long,
)

/**
 * Places taps on the pattern's own timeline — docs/40-PHASE-4-SPEC.md §4.3 and §4.4.
 *
 * The one step between raw touches and scoring, and it does two things and no more: rebase onto the
 * moment the pattern started sounding, and subtract the systematic offset calibration measured. What
 * it deliberately does *not* do is decide anything — no matching, no windows, no verdicts. Scoring is
 * Stage 4.3, and keeping it out of here is what lets both halves stay small enough to be obviously
 * right.
 */
public object TapTimeline {
    /**
     * @param taps every touch recorded during the item, in any order.
     * @param patternStartNanos when the pattern's first expected event sounded, on the same clock as
     *   [TapEvent.monotonicNanos] — normally [OutputTimebase.nanosForFrame] of the frame that event
     *   begins at.
     * @param calibrationOffsetMs the learner's systematic offset on this route, from §4.3's median.
     *   **Positive means they tap late**: the constant is `median(tap − event)`, so removing it means
     *   subtracting. A learner who is uniformly 40 ms late — §9 simulation 2, the one that decides
     *   whether the phase works — comes out of this function landing on the beat.
     * @return each tap in milliseconds from the pattern's start, calibration applied, ascending.
     *   Negative for a tap before the first event, which is a real thing a learner does and an error
     *   for §6.2 to classify, not something to discard here.
     *
     * Sorted on the way out because nothing downstream should have to wonder. Input dispatch delivers
     * touches in order in practice, but "in practice" is the kind of assumption that produces a
     * scoring bug visible only on one device.
     */
    public fun relativeToPatternMs(
        taps: List<TapEvent>,
        patternStartNanos: Long,
        calibrationOffsetMs: Double,
    ): List<Double> =
        taps
            .map { (it.monotonicNanos - patternStartNanos) / NANOS_PER_MS - calibrationOffsetMs }
            .sorted()

    private const val NANOS_PER_MS = 1_000_000.0
}
