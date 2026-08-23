package com.tonic.core.model.rhythm

/**
 * Takadimi syllables — docs/40-PHASE-4-SPEC.md §3.1, and the decision recorded there.
 *
 * **Beat-oriented, not note-value-oriented.** The syllable names *where you are within the beat*: the
 * beat itself is always `ta`, a division is `ta-di`, a subdivision `ta-ka-di-mi`. An identical-sounding
 * pattern therefore gets identical syllables whatever the notated note values or the meter around it.
 *
 * Kodály's `ta`/`ti-ti` names note values instead, which makes the same syllable mean different metric
 * positions in different meters and makes compound meter incoherent. For an app that never shows
 * notation and teaches feel rather than reading, beat-orientation is the only consistent choice — and
 * it mirrors the pitch track exactly, where a scale degree names function within a key rather than a
 * letter.
 *
 * The compound tables are here from the start even though Stage 4.2 generates only simple meter,
 * because they are the case §9's Stage 4.6 singles out as the one Kodály fails. Writing them alongside
 * the simple ones keeps the shape honest; generating them is that stage's job.
 */
public object Takadimi {
    /**
     * The syllables for one beat divided into [parts], in order.
     *
     * @param parts how many equal parts the beat is divided into for this pattern — 1 (the beat alone),
     *   2 or 4 in simple meter, 3 or 6 in compound.
     * @throws IllegalArgumentException for a division this system has no syllables for. Not a silent
     *   fallback: a wrong syllable is worse than none, because the learner is being taught to say it.
     */
    public fun forDivision(parts: Int): List<String> =
        TABLES[parts]
            ?: throw IllegalArgumentException("No Takadimi syllables for a beat in $parts parts")

    /** Every division this system can name. */
    public val supportedDivisions: Set<Int> get() = TABLES.keys

    /**
     * The syllable for the part at [index] of a beat divided into [parts].
     *
     * This is the call a UI makes per event, and it is deliberately the only way to get one — there is
     * no "syllable for a duration," because duration is exactly what Takadimi does not name.
     */
    public fun syllableAt(
        parts: Int,
        index: Int,
    ): String {
        val table = forDivision(parts)
        require(index in table.indices) { "Part $index is outside a beat in $parts parts" }
        return table[index]
    }

    private val TABLES: Map<Int, List<String>> =
        mapOf(
            // The beat alone. One sound on the beat is "ta" in every meter, which is the whole point.
            1 to listOf("ta"),
            // Simple division and subdivision.
            2 to listOf("ta", "di"),
            4 to listOf("ta", "ka", "di", "mi"),
            // Compound. The beat still opens on "ta" - a learner who has felt "ta" in simple meter has
            // felt the same thing here, which is what Kodaly cannot say.
            3 to listOf("ta", "ki", "da"),
            6 to listOf("ta", "va", "ki", "di", "da", "ma"),
        )
}
