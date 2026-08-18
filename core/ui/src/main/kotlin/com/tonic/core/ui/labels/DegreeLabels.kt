package com.tonic.core.ui.labels

import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle

/**
 * Presentation-only mapping from a [ScaleDegree] to its on-screen text — docs/02-PEDAGOGY.md §2:
 * "labels are a presentation concern... The label set is a display mapping only," which is why this
 * lives in `:core:ui` rather than on [ScaleDegree] itself. Movable-do only ("fixed-do is not
 * implemented" — same doc), and only Phase 1's un-altered diatonic degrees have a syllable defined;
 * altered degrees are Phase 2.
 */
fun ScaleDegree.displayLabel(style: LabelStyle): String =
    when (style) {
        LabelStyle.NUMBERS -> degree.toString()
        LabelStyle.SOLFEGE -> SOLFEGE_SYLLABLES.getValue(degree)
    }

private val SOLFEGE_SYLLABLES =
    mapOf(1 to "Do", 2 to "Re", 3 to "Mi", 4 to "Fa", 5 to "Sol", 6 to "La", 7 to "Ti")
