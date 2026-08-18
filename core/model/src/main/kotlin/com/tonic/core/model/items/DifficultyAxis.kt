package com.tonic.core.model.items

import kotlinx.serialization.Serializable

/**
 * The six independent M2 difficulty axes, docs/03-CURRICULUM.md §5.3. The
 * axis scheduler (docs/07-ADAPTIVE-ENGINE.md §3) moves exactly one of
 * these at a time — moving several at once destroys the ability to
 * attribute a performance drop to a cause, per CLAUDE.md's "two things
 * most likely to go wrong."
 *
 * `@Serializable`: this is the key type of the `axisLevelsJson` /
 * `staircaseStateJson` maps described in docs/05-DATA-MODEL.md §2.
 */
@Serializable
enum class DifficultyAxis(
    val maxLevel: Int,
) {
    /** Harmonic reference strength. 0..7 — see [CadenceFadeLevel]. The pedagogically critical axis. */
    CADENCE_FADE(7),

    /** 0 = one fixed timbre; 4 = all families, randomized, reference and target may differ. */
    TIMBRE_VARIETY(4),

    /** Range the target pitch is drawn from, in octaves around the reference. */
    REGISTER_SPREAD(3),

    /** 0 = target within reference octave; 1 = ±1 octave; 2 = ±2 octaves. */
    OCTAVE_DISPLACE(2),

    /** Duration of reference/target and gap length. Faster/shorter is harder. */
    TEMPO_DENSITY(3),

    /** 0 = key drawn from 3 keys; 1 = 7 keys; 2 = all 12. */
    KEY_SPREAD(2),
    ;

    val levelRange: IntRange get() = 0..maxLevel

    /** Priority order for the axis scheduler — CADENCE_FADE moves first, TEMPO_DENSITY last. */
    companion object {
        val SCHEDULING_PRIORITY =
            listOf(CADENCE_FADE, TIMBRE_VARIETY, KEY_SPREAD, OCTAVE_DISPLACE, REGISTER_SPREAD, TEMPO_DENSITY)
    }
}
