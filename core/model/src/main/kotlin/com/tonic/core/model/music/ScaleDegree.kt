package com.tonic.core.model.music

/**
 * A scale degree, stored as the diatonic degree number plus a chromatic [alteration] in semitones.
 * Every Phase 1 degree has `alteration == 0`; Phase 2's chromatic degrees and minor's characteristic
 * degrees are the same seven slots carrying a flat or a sharp (docs/20-PHASE-2-SPEC.md §2.1/§2.2).
 *
 * Display labels — Arabic numerals by default, movable-do solfège as a user toggle, and Phase 2's
 * fading semantic subtitles — are a presentation concern that lives in `:core:ui`, not here
 * (docs/02-PEDAGOGY.md §2). [canonicalLabel] is the separate, storage-facing form; see its KDoc.
 */
data class ScaleDegree(
    val degree: Int,
    val alteration: Int = 0,
) {
    init {
        require(degree in 1..7) { "Scale degree must be 1..7, was $degree" }
        require(alteration in -1..1) {
            "Alteration must be -1 (flat), 0 (natural) or 1 (sharp), was $alteration. Double accidentals " +
                "are not a thing this curriculum teaches: every chromatic pitch is reachable as a single " +
                "alteration of some diatonic slot."
        }
    }

    /**
     * Semitones above the tonic for this degree in [mode]. This is what a generator needs to turn
     * "scale degree 3 in this key" into an actual MIDI note — see docs/03-CURRICULUM.md §5.4.
     */
    fun semitoneOffset(mode: Mode): Int =
        when (mode) {
            // Both modes read the same table, on purpose. Minor's characteristic degrees carry their
            // flattening in [alteration] - ♭3 is ScaleDegree(3, -1), never degree 3 silently
            // reinterpreted - which is exactly what makes one label mean one fixed pitch relationship
            // in every mode (docs/20-PHASE-2-SPEC.md §2.1), and what lets ♭3-in-minor and
            // ♭3-as-a-chromatic-in-major be the same value rather than two things that collide.
            // The parameter stays because M8's modal scales are not guaranteed to share this table.
            Mode.MAJOR, Mode.MINOR -> DIATONIC_SEMITONE_OFFSETS.getValue(degree)
        } + alteration

    /**
     * The storage form — what lands in `Attempt.targetLabel` / `responseLabel` and keys the confusion
     * matrix (docs/05-DATA-MODEL.md §1). ASCII on purpose: `"b3"`, `"3"`, `"#4"`. The glyphs a user sees
     * (♭, ♯, solfège) are `:core:ui`'s business, and a stored label should not carry a rendering choice.
     *
     * **Byte-identical to the Phase 1 form for every unaltered degree**, which is load-bearing twice
     * over: existing attempt rows stay readable without a migration, and the Stage 2.0 golden corpus
     * stays valid. Before this existed the label was `degree.toString()`, which silently dropped the
     * alteration — so `♭3` and `♮3` would both have stored as `"3"`, conflating two different answers
     * in the attempt log and collapsing them into one cell of the confusion matrix.
     */
    val canonicalLabel: String
        get() =
            when {
                alteration < 0 -> "b$degree"
                alteration > 0 -> "#$degree"
                else -> degree.toString()
            }

    companion object {
        /** Interval-above-tonic for each unaltered diatonic degree, measured against the major scale. */
        private val DIATONIC_SEMITONE_OFFSETS = mapOf(1 to 0, 2 to 2, 3 to 4, 4 to 5, 5 to 7, 6 to 9, 7 to 11)

        val TONIC_TRIAD = setOf(ScaleDegree(1), ScaleDegree(3), ScaleDegree(5))
        val ALL_DIATONIC = (1..7).map { ScaleDegree(it) }.toSet()

        /** The minor tonic triad — `1, ♭3, 5` (docs/20-PHASE-2-SPEC.md §3, `M10.MIN_SET_1`). */
        val MINOR_TONIC_TRIAD = setOf(ScaleDegree(1), ScaleDegree(3, -1), ScaleDegree(5))

        /** Natural minor: `1, 2, ♭3, 4, 5, ♭6, ♭7` (docs/20-PHASE-2-SPEC.md §2.1). */
        val ALL_NATURAL_MINOR =
            listOf(
                ScaleDegree(1),
                ScaleDegree(2),
                ScaleDegree(3, -1),
                ScaleDegree(4),
                ScaleDegree(5),
                ScaleDegree(6, -1),
                ScaleDegree(7, -1),
            ).toSet()

        /**
         * The five chromatic degrees of major, in the introduction order of docs/20-PHASE-2-SPEC.md
         * §2.2 — strongest pull toward a stable tone first, because a strong pull is easier to hear.
         */
        val CHROMATIC_INTRODUCTION_ORDER =
            listOf(
                ScaleDegree(4, 1), // ♯4, pulls to 5
                ScaleDegree(7, -1), // ♭7, pulls down to 6
                ScaleDegree(6, -1), // ♭6, pulls down to 5
                ScaleDegree(3, -1), // ♭3, pulls down to 2
                ScaleDegree(2, -1), // ♭2, pulls down to 1
            )

        /**
         * All twelve pitches of the octave, named as degrees of major — the answer set `M11.CHROM_FULL`
         * ends at (docs/20-PHASE-2-SPEC.md §2.2). Twelve distinct [semitoneOffset] values, which is the
         * property `ChromaticDegreesTest` checks rather than assumes.
         */
        val ALL_CHROMATIC = ALL_DIATONIC + CHROMATIC_INTRODUCTION_ORDER
    }
}
