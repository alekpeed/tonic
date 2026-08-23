package com.tonic.core.model.rhythm

/**
 * How beats group into bars, and how each beat divides — docs/40-PHASE-4-SPEC.md §3.1.
 *
 * Deliberately *not* a time signature. A time signature is notation, and this app never shows notation
 * (docs/02-PEDAGOGY.md). What a learner has to feel is how many beats before "one" comes round again
 * and whether the beat splits in two or in three, which is exactly these two numbers. That is also why
 * `4/4` and `2/2` are the same meter here: they sound identical and differ only on paper.
 *
 * @property beatsPerBar how many beats before the downbeat returns.
 * @property division how many equal parts one beat splits into at the first level below the beat —
 *   2 for simple meter, 3 for compound. §3.1's whole argument for Takadimi over Kodály rests on this
 *   being a property of the *beat* rather than of a note value.
 */
public data class Meter(
    public val beatsPerBar: Int,
    public val division: Int,
) {
    init {
        require(beatsPerBar >= 1) { "A bar needs at least one beat, was $beatsPerBar" }
        require(division == SIMPLE || division == COMPOUND) {
            "A beat divides in two (simple) or three (compound), was $division"
        }
    }

    /** True when the beat splits in three. Compound meter is Stage 4.6's; the type carries it from the start. */
    public val isCompound: Boolean get() = division == COMPOUND

    /** Ticks in one bar. */
    public val ticksPerBar: Int get() = beatsPerBar * TICKS_PER_BEAT

    public companion object {
        /** A beat that splits in two. */
        public const val SIMPLE: Int = 2

        /** A beat that splits in three. */
        public const val COMPOUND: Int = 3

        /**
         * The resolution every rhythmic position is expressed in — twelve ticks to the beat.
         *
         * Integer ticks rather than fractions of a beat, and this is a determinism decision, not an
         * optimization. CLAUDE.md §5 requires a generated item to be byte-identical on every run and
         * every device; positions held as `Double` are neither, because 1/3 of a beat is not
         * representable and the error depends on the order the arithmetic happened in. Twelve is the
         * smallest number divisible by 2, 3, 4 and 6, so every division this curriculum uses — halves,
         * thirds, quarters and sixths of a beat — lands on a whole tick with nothing left over.
         *
         * It is not a playback rate. Milliseconds are derived from ticks and tempo at render time, in
         * one place, so tempo can change without any position needing to be recomputed.
         */
        public const val TICKS_PER_BEAT: Int = 12

        /** The default four-in-a-bar, simple. What `M3.BEAT_FIND` and everything before compound meter uses. */
        public val FOUR_FOUR: Meter = Meter(beatsPerBar = 4, division = SIMPLE)

        /** Three in a bar, simple. */
        public val THREE_FOUR: Meter = Meter(beatsPerBar = 3, division = SIMPLE)

        /** Two in a bar, simple. */
        public val TWO_FOUR: Meter = Meter(beatsPerBar = 2, division = SIMPLE)
    }
}
