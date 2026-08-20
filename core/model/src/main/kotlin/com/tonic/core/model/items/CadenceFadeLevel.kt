package com.tonic.core.model.items

/**
 * The eight cadence-fade levels, docs/02-PEDAGOGY.md §3 — the core
 * difficulty mechanic and, per CLAUDE.md's "two things most likely to go
 * wrong," the single most important design element in the app. Each level
 * names *how much harmonic reference plays before the target*; L6–L7 are
 * where genuine audiation is trained because the tonic is not handed back
 * per item.
 */
enum class CadenceFadeLevel(
    val level: Int,
) {
    /** Full cadence I–IV–V–I, before every item. */
    L0(0),

    /** Full cadence, then 2–3 items answered before it repeats. */
    L1(1),

    /** Short cadence V–I only. */
    L2(2),

    /** Tonic triad only. */
    L3(3),

    /** Tonic drone sustained underneath the entire item. */
    L4(4),

    /** Brief tonic flash, then a silent gap, then the item. */
    L5(5),

    /** No reference per item; key established once at block start. */
    L6(6),

    /** As L6, with the silent gap lengthening across the block. */
    L7(7),
    ;

    companion object {
        val MIN = L0
        val MAX = L7

        /** docs/03-CURRICULUM.md §5.5 criterion 5: a node cannot master below this level. */
        val MASTERY_MINIMUM = L4

        fun fromLevel(level: Int): CadenceFadeLevel =
            entries.find { it.level == level }
                ?: throw IllegalArgumentException("Cadence fade level must be 0..7, was $level")
    }
}
