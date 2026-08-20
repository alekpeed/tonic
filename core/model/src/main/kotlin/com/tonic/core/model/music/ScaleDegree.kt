package com.tonic.core.model.music

/**
 * A scale degree, stored as the diatonic degree number plus a chromatic
 * [alteration] in semitones (0 for every Phase 1 degree — chromatic degrees
 * are Phase 2, docs/02-PEDAGOGY.md §4). Labels (Arabic numerals by default,
 * movable-do solfège as a user toggle) are a presentation concern that
 * lives in `:core:ui`, not here — docs/02-PEDAGOGY.md §2.
 */
data class ScaleDegree(
    val degree: Int,
    val alteration: Int = 0,
) {
    init {
        require(degree in 1..7) { "Scale degree must be 1..7, was $degree" }
    }

    /**
     * Semitones above the tonic for this degree in [mode]. This is what a
     * generator needs to turn "scale degree 3 in this key" into an actual
     * MIDI note — see docs/03-CURRICULUM.md §5.4.
     */
    fun semitoneOffset(mode: Mode): Int =
        when (mode) {
            Mode.MAJOR -> MAJOR_SEMITONE_OFFSETS.getValue(degree)
        } + alteration

    companion object {
        /** Interval-above-tonic for each diatonic degree of a major scale. */
        private val MAJOR_SEMITONE_OFFSETS = mapOf(1 to 0, 2 to 2, 3 to 4, 4 to 5, 5 to 7, 6 to 9, 7 to 11)

        val TONIC_TRIAD = setOf(ScaleDegree(1), ScaleDegree(3), ScaleDegree(5))
        val ALL_DIATONIC = (1..7).map { ScaleDegree(it) }.toSet()
    }
}
