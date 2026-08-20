package com.tonic.core.model.music

/**
 * Phase 1 is MAJOR only — docs/01-PRODUCT-SPEC.md §3 and
 * docs/02-PEDAGOGY.md §4: minor mode is deliberately deferred because it
 * introduces natural/harmonic/melodic ambiguity that would double the
 * Phase 1 surface for no beginner-stage benefit. Kept as an enum (rather
 * than Phase 1 hardcoding MAJOR everywhere) so that later addition doesn't
 * require touching every call site that already threads a [Mode] through.
 */
enum class Mode {
    MAJOR,
    ;

    companion object {
        val PHASE_1_ONLY = MAJOR
    }
}
