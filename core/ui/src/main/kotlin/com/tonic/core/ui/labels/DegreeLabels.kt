package com.tonic.core.ui.labels

import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle

/**
 * Presentation-only mapping from a [ScaleDegree] to its on-screen text — docs/02-PEDAGOGY.md §2:
 * "labels are a presentation concern... The label set is a display mapping only," which is why this
 * lives in `:core:ui` rather than on [ScaleDegree] itself. Movable-do only ("fixed-do is not
 * implemented" — same doc).
 *
 * Altered degrees arrived with Phase 2 — minor's `♭3`/`♭6`/`♭7` and the chromatic degrees of
 * docs/20-PHASE-2-SPEC.md §2.2. Note the deliberate split from
 * [com.tonic.core.model.music.ScaleDegree.canonicalLabel]: that one is ASCII (`b3`) because it is
 * *stored*, in the attempt log and the confusion matrix, where a rendering choice has no business.
 * This one uses the real glyphs (`♭3`) because it is *read*, and `b3` on a button would look like a
 * typo.
 */
fun ScaleDegree.displayLabel(style: LabelStyle): String =
    when (style) {
        LabelStyle.NUMBERS -> "${accidentalGlyph()}$degree"
        LabelStyle.SOLFEGE ->
            SOLFEGE_SYLLABLES[degree to alteration]
                ?: error("no movable-do syllable for $canonicalLabel")
    }

private fun ScaleDegree.accidentalGlyph(): String =
    when {
        alteration < 0 -> "\u266d"
        alteration > 0 -> "\u266f"
        else -> ""
    }

/**
 * Movable-do, including the standard chromatic syllables: flats descend (`Me`, `Le`, `Te`) and sharps
 * ascend (`Di`, `Ri`, `Fi`, `Si`, `Li`). Keyed by degree *and* alteration, because `3` and `♭3` are
 * different notes and must never share a syllable — the same collision `canonicalLabel` exists to
 * prevent on the storage side.
 */
private val SOLFEGE_SYLLABLES =
    mapOf(
        (1 to 0) to "Do",
        (2 to 0) to "Re",
        (3 to 0) to "Mi",
        (4 to 0) to "Fa",
        (5 to 0) to "Sol",
        (6 to 0) to "La",
        (7 to 0) to "Ti",
        // Flats — minor's characteristic degrees plus the chromatic flats.
        (2 to -1) to "Ra",
        (3 to -1) to "Me",
        (5 to -1) to "Se",
        (6 to -1) to "Le",
        (7 to -1) to "Te",
        // Sharps.
        (1 to 1) to "Di",
        (2 to 1) to "Ri",
        (4 to 1) to "Fi",
        (5 to 1) to "Si",
        (6 to 1) to "Li",
    )
