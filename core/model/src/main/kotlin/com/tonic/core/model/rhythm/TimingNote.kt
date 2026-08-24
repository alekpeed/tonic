package com.tonic.core.model.rhythm

/**
 * The plain-language timing remark shown after a tapped attempt — docs/40-PHASE-4-SPEC.md §7.4.
 *
 * §7.4 asks for exactly this and bounds it tightly: "plain-language timing note ('slightly ahead of
 * the beat'), framed neutrally" and "never a precision grade or score". So the result is one of four
 * words rather than a number, and there is deliberately no way to get a magnitude out of it. A learner
 * who could read "18 ms early" off the screen would start chasing the number, which is the failure
 * docs/02-PEDAGOGY.md §6 rules out for latency and §7 of docs/30-PHASE-3-SPEC.md rules out for cents.
 *
 * This is the display side of §6.3's split. Raw asynchrony is "shown to the user as feedback ('you
 * were a little ahead')" and never scored — and the way that guarantee is kept is structural: nothing
 * in `:core:engine` calls this, and this returns something a staircase could not consume if it tried.
 */
public enum class TimingNote {
    /** Not enough landed to say anything. Silence is the honest output, not a guess. */
    NONE,

    /** Centred on the beat, within [ON_BEAT_FRACTION] of the window. */
    ON_BEAT,

    /** Consistently early. */
    AHEAD,

    /** Consistently late. */
    BEHIND,

    ;

    public companion object {
        /**
         * How [asynchronies] sat relative to the beat, given the window they were judged against.
         *
         * The **median**, not the mean, and signed. One wild tap in a pattern should not decide what a
         * learner is told about the other seven, which is the same reason `Calibrator` takes a median
         * of a calibration run and `RhythmProductionMasteryEvaluator` takes one of a window.
         *
         * Relative to the tolerance rather than to a fixed number of milliseconds, for the reason
         * §6.2 gives about windows themselves: 20 ms is nothing at 60 BPM and most of the window at
         * 200. A learner told they are "a little ahead" at one tempo and "on the beat" at another for
         * the same performance would reasonably conclude the app was guessing.
         *
         * @param asynchronies signed offsets, one per matched event, negative early. Nulls — missed
         *   events — are ignored rather than counted as zero: nothing was played, so nothing was early
         *   or late about it.
         * @param toleranceHalfWidthMs the window those offsets were judged against.
         */
        public fun of(
            asynchronies: List<Double?>,
            toleranceHalfWidthMs: Double,
        ): TimingNote {
            require(toleranceHalfWidthMs > 0) { "A window must be positive, was $toleranceHalfWidthMs" }
            val landed = asynchronies.filterNotNull().sorted()
            if (landed.size < MIN_EVENTS) return NONE
            val median =
                if (landed.size % 2 == 1) {
                    landed[landed.size / 2]
                } else {
                    (landed[landed.size / 2 - 1] + landed[landed.size / 2]) / 2.0
                }
            val share = median / toleranceHalfWidthMs
            return when {
                share < -ON_BEAT_FRACTION -> AHEAD
                share > ON_BEAT_FRACTION -> BEHIND
                else -> ON_BEAT
            }
        }

        /**
         * Two matched events. One tap says nothing about a tendency, and "you were a little ahead"
         * about a single sound is a remark on noise.
         */
        public const val MIN_EVENTS: Int = 2

        /**
         * How much of the window counts as being on the beat.
         *
         * ⚠️ Reasoned, not measured, like every other number in this module that a device would settle.
         * A third means the note only appears when a learner is consistently using up a real part of
         * their margin in one direction — which is what makes it worth telling them — and stays quiet
         * for the ordinary wobble that has no direction to correct.
         */
        public const val ON_BEAT_FRACTION: Double = 1.0 / 3.0
    }
}
