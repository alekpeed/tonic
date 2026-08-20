package com.tonic.core.model.music

/**
 * Phase 1 was MAJOR only — docs/01-PRODUCT-SPEC.md §3 and docs/02-PEDAGOGY.md §4: minor was deliberately
 * deferred because it introduces natural/harmonic/melodic ambiguity that would have doubled the Phase 1
 * surface for no beginner-stage benefit. Kept as an enum (rather than hardcoding MAJOR everywhere) so
 * that the later addition wouldn't require touching every call site that already threads a [Mode]
 * through — Phase 2 Stage 2.0 is that addition, and the foresight paid: adding [MINOR] here changed no
 * call site's shape.
 *
 * Phase 2 resolves the ambiguity that caused the deferral by treating **natural minor as the base and
 * the raised 6 and 7 as alterations of it**, not as separate modes (docs/20-PHASE-2-SPEC.md §2.1). So
 * there is no `HARMONIC_MINOR` or `MELODIC_MINOR` here and there should not be: harmonic and melodic
 * minor are the same mode with a [ScaleDegree.alteration] applied, which is what keeps one label
 * meaning one fixed pitch relationship across all three forms.
 */
enum class Mode {
    MAJOR,
    MINOR,
    ;

    /**
     * This mode's seven diatonic degrees, in scale order — the answer to "what is the third degree
     * *of this mode*".
     *
     * This exists because [ScaleDegree.semitoneOffset] deliberately does **not** depend on mode: a
     * label denotes one fixed pitch relationship everywhere, so `ScaleDegree(3)` is a major third in
     * minor exactly as it is in major, and minor's third is the separate value `ScaleDegree(3, -1)`.
     * That invariant is what keeps `♭3`-in-minor and `♭3`-as-a-chromatic-in-major the same thing
     * (docs/20-PHASE-2-SPEC.md §2.1), but it means a caller building "the triad on step 3" cannot get
     * there from the step number alone. It looks the degree up here instead.
     *
     * Natural minor is the reference form. Harmonic and melodic minor are alterations applied on top
     * by the caller that wants them, never separate scales.
     */
    val diatonicDegrees: List<ScaleDegree>
        get() =
            when (this) {
                MAJOR -> MAJOR_DEGREES
                MINOR -> NATURAL_MINOR_DEGREES
            }

    /**
     * The degree at 1-indexed [step] of this mode's scale. Steps outside 1..7 wrap within the octave;
     * the caller is responsible for any octave offset, since only it knows which register it wants.
     */
    fun degreeAtStep(step: Int): ScaleDegree = diatonicDegrees[Math.floorMod(step - 1, DEGREES_PER_OCTAVE)]

    companion object {
        private const val DEGREES_PER_OCTAVE = 7

        private val MAJOR_DEGREES = (1..DEGREES_PER_OCTAVE).map { ScaleDegree(it) }

        /** `1, 2, ♭3, 4, 5, ♭6, ♭7` — docs/20-PHASE-2-SPEC.md §2.1. */
        private val NATURAL_MINOR_DEGREES =
            listOf(
                ScaleDegree(1),
                ScaleDegree(2),
                ScaleDegree(3, -1),
                ScaleDegree(4),
                ScaleDegree(5),
                ScaleDegree(6, -1),
                ScaleDegree(7, -1),
            )

        /**
         * What Phase 1 shipped. Retained because Phase 1 skills (`M0.*`, `M1.*`, `M2.*`) are still
         * major-only and say so at their call sites; a Phase 2 skill names its own mode explicitly.
         */
        val PHASE_1_ONLY = MAJOR
    }
}
