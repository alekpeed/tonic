package com.tonic.core.model.rhythm

/**
 * The eight metronome-fade levels — docs/40-PHASE-4-SPEC.md §3.2, rhythm's pedagogical centerpiece and
 * the exact analog of [com.tonic.core.model.items.CadenceFadeLevel].
 *
 * The pitch track withdraws harmonic support in graded steps so the learner must eventually hold the
 * tonal center internally. This withdraws external timekeeping in graded steps so they must eventually
 * hold the *beat* internally. Same problem, same solution, and the same warning attached: **this axis
 * moves one level at a time.** Skipping a rung here does not make an item harder, it makes it
 * unanswerable — which is precisely what a step size of 2 did to `CADENCE_FADE` in live use
 * (docs/07-ADAPTIVE-ENGINE.md §2a).
 *
 * [L4] is the critical transition, the first level where the learner keeps time unaided. [L6] and [L7]
 * are where genuine internal pulse is trained, and why `M3.INDEPENDENCE_CHECK` sits at L6.
 */
public enum class MetronomeFadeLevel(
    public val level: Int,
) {
    /** Metronome on every subdivision, continuous throughout. The most support there is. */
    L0(0),

    /** Metronome on every beat, continuous throughout. */
    L1(1),

    /** Metronome on beat 1 of each bar only, continuous. The pulse is there; the beats between are not. */
    L2(2),

    /** Full count-in of two bars, then the metronome continues under the pattern. */
    L3(3),

    /**
     * Full count-in of two bars, then the metronome stops for the pattern.
     *
     * The critical transition: the first level at which the learner produces with no external pulse
     * underneath them. docs/40-PHASE-4-SPEC.md §5.3 criterion 4 makes reaching this a mastery
     * requirement for exactly that reason — without it a learner "masters" rhythm having never once
     * kept time unaided, which is the same trap `CADENCE_FADE >= 4` exists to close in the pitch track.
     */
    L4(4),

    /** Count-in of one bar, then silence. */
    L5(5),

    /** Count-in of two beats only, then silence. Where `M3.INDEPENDENCE_CHECK` runs. */
    L6(6),

    /** Tempo stated once at block start; no count-in per item at all. */
    L7(7),
    ;

    /** True when the metronome is still sounding while the learner produces the pattern. */
    public val soundsUnderPattern: Boolean get() = this in setOf(L0, L1, L2, L3)

    public companion object {
        public val MIN: MetronomeFadeLevel = L0
        public val MAX: MetronomeFadeLevel = L7

        /** docs/40-PHASE-4-SPEC.md §5.3 criterion 4: a production node cannot master below this level. */
        public val MASTERY_MINIMUM: MetronomeFadeLevel = L4

        /**
         * Where `M3.INDEPENDENCE_CHECK` runs — docs/40-PHASE-4-SPEC.md §5.1.
         *
         * Two levels above [MASTERY_MINIMUM], and that gap is the check's entire reason to exist. L4
         * and L5 still hand the learner a count-in, so the pulse they keep is one they were just
         * given; [L6] gives two beats and then nothing.
         */
        public const val INDEPENDENCE_CHECK_LEVEL: Int = 6

        public fun fromLevel(level: Int): MetronomeFadeLevel =
            entries.find { it.level == level }
                ?: throw IllegalArgumentException("Metronome fade level must be 0..7, was $level")
    }
}
