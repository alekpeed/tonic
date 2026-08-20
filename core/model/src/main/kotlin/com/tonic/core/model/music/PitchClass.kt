package com.tonic.core.model.music

/**
 * One of the 12 pitch classes, 0 = C .. 11 = B. Represents a key's tonic.
 * Deliberately has no notion of "the" spelling (C# vs Db) — Phase 1 never
 * displays note names, only scale-degree numbers (docs/02-PEDAGOGY.md §2),
 * so spelling is not domain logic that needs to exist yet.
 */
@JvmInline
value class PitchClass(
    val value: Int,
) {
    init {
        require(value in 0..11) { "PitchClass must be 0..11, was $value" }
    }

    operator fun plus(semitones: Int): PitchClass = PitchClass(Math.floorMod(value + semitones, 12))

    companion object {
        val C = PitchClass(0)
    }
}
