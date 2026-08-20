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
    /**
     * Which family of skills this axis applies to. Phase 1 had one family and could treat "every axis"
     * and "every axis that applies here" as the same set; Phase 2's prediction items do not use the
     * cadence-fade/register/octave axes at all, and recognition items have no notion of a silent gap
     * (docs/20-PHASE-2-SPEC.md §4, change 3). Scoping them keeps a prediction axis from ever being
     * offered to the scheduler for an `M2` node, which is what makes adding these two entries a no-op
     * for Phase 1 behavior rather than a change to it.
     */
    val scope: Scope = Scope.RECOGNITION,
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

    /**
     * `M12.*` only. Length of the silent gap the learner audiates across before the note sounds:
     * 1000ms → 2000ms → 3500ms → 5000ms (docs/20-PHASE-2-SPEC.md §2.3). Longer is harder, because the
     * internal representation has to be *held* rather than merely formed.
     */
    PREDICT_GAP(3, scope = Scope.PREDICTION),

    /**
     * `M12.*` only. How far the sounded note deviates when it is not the stated degree: adjacent
     * diatonic degree → same degree wrong octave → chromatic neighbor → same degree detuned 30 cents
     * (docs/20-PHASE-2-SPEC.md §2.3). Level 3 is deliberately at the edge of reasonable and is an
     * advanced target, never a required mastery gate.
     */
    PREDICT_DEVIATION(3, scope = Scope.PREDICTION),
    ;

    /** See [DifficultyAxis.scope]. */
    enum class Scope {
        /** Identify a sounded note against an established key: `M2.*`, `M10.*`, `M11.*`. */
        RECOGNITION,

        /** Audiate a named degree, then judge what actually sounded: `M12.*`. */
        PREDICTION,
    }

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

        /**
         * Recognition scheduling order — `CADENCE_FADE` moves first, `TEMPO_DENSITY` last.
         *
         * Deliberately *not* every axis, as of Phase 2: this is the priority list for
         * [Scope.RECOGNITION], and a prediction axis must never appear here or the scheduler could
         * offer one to an `M2` node. Use [schedulingPriorityFor] when the scope is not statically known.
         */
        val SCHEDULING_PRIORITY =
            listOf(CADENCE_FADE, TIMBRE_VARIETY, KEY_SPREAD, OCTAVE_DISPLACE, REGISTER_SPREAD, TEMPO_DENSITY)

        /** Prediction scheduling order — the gap lengthens before the deviation narrows. */
        val PREDICTION_SCHEDULING_PRIORITY = listOf(PREDICT_GAP, PREDICT_DEVIATION)

        /** Every axis that applies to a recognition skill. The six Phase 1 axes, unchanged. */
        val RECOGNITION_AXES: List<DifficultyAxis> = entries.filter { it.scope == Scope.RECOGNITION }

        /** Every axis that applies to a prediction skill. */
        val PREDICTION_AXES: List<DifficultyAxis> = entries.filter { it.scope == Scope.PREDICTION }

        fun axesFor(scope: Scope): List<DifficultyAxis> =
            when (scope) {
                Scope.RECOGNITION -> RECOGNITION_AXES
                Scope.PREDICTION -> PREDICTION_AXES
            }

        fun schedulingPriorityFor(scope: Scope): List<DifficultyAxis> =
            when (scope) {
                Scope.RECOGNITION -> SCHEDULING_PRIORITY
                Scope.PREDICTION -> PREDICTION_SCHEDULING_PRIORITY
            }
    }
}
