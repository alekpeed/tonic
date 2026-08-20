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
    val initialStepSize: Int = DEFAULT_INITIAL_STEP_SIZE,
) {
    /**
     * Harmonic reference strength. 0..7 — see [CadenceFadeLevel]. The pedagogically critical axis.
     *
     * [initialStepSize] is 1 here and 2 everywhere else — docs/07-ADAPTIVE-ENGINE.md §2a, a correction
     * made after a live-use bug. With the global step size of 2, two consecutive correct answers
     * (reachable in the first handful of items) vaulted a first-time user from L0's full four-chord
     * cadence straight to L2's V–I, skipping L1 entirely. That is not a pacing nicety: at L2 the two
     * chords are the same loudness, timbre, and duration with no gap between them, so the only thing
     * marking which one is "home" is harmonic resolution — precisely the skill the exercise exists to
     * build. A user who hasn't built it yet has no means of answering except chance. Every level on
     * this axis must be genuinely passed through; a skipped rung here is a removed rung.
     */
    CADENCE_FADE(7, initialStepSize = 1),

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
        /**
         * "Step size starts at 2 for fast initial convergence" (docs/07-ADAPTIVE-ENGINE.md §2), which
         * still holds for every axis that changes *how hard* an item is without changing whether the
         * user has any means of answering it at all. [CADENCE_FADE] is the one axis that does the
         * latter, and overrides this — see §2a and that constant's own KDoc.
         */
        const val DEFAULT_INITIAL_STEP_SIZE = 2

        val SCHEDULING_PRIORITY =
            listOf(CADENCE_FADE, TIMBRE_VARIETY, KEY_SPREAD, OCTAVE_DISPLACE, REGISTER_SPREAD, TEMPO_DENSITY)
    }
}
