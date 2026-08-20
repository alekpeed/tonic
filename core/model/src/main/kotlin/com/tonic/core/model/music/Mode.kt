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

    companion object {
        /**
         * What Phase 1 shipped. Retained because Phase 1 skills (`M0.*`, `M1.*`, `M2.*`) are still
         * major-only and say so at their call sites; a Phase 2 skill names its own mode explicitly.
         */
        val PHASE_1_ONLY = MAJOR
    }
}
