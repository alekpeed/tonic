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
     * Which families of skills this axis applies to. Phase 1 had one family and could treat "every axis"
     * and "every axis that applies here" as the same set; Phase 2's prediction items do not use the
     * cadence-fade/register/octave axes at all, and recognition items have no notion of a silent gap
     * (docs/20-PHASE-2-SPEC.md §4, change 3). Scoping them keeps a prediction axis from ever being
     * offered to the scheduler for an `M2` node, which is what makes adding those entries a no-op for
     * Phase 1 behavior rather than a change to it.
     *
     * A *set* since Phase 4, because [TIMBRE_VARIETY] genuinely belongs to two families:
     * docs/40-PHASE-4-SPEC.md §5.2 reuses it for rhythm on the same generalization argument that put it
     * in the pitch track. One axis in two scopes is the honest model of that; a second enum entry with
     * a different name for the same idea would split one skill's timbre progress across two identifiers.
     */
    val scopes: Set<Scope> = setOf(Scope.RECOGNITION),
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

    /**
     * 0 = one fixed timbre; 4 = all families, randomized, reference and target may differ.
     *
     * Shared with rhythm since Phase 4 (docs/40-PHASE-4-SPEC.md §5.2), where it varies the metronome and
     * pattern voices for the same reason it varies the reference and target here: a learner who has only
     * ever heard one timbre has learned that timbre, not the skill.
     *
     * ⚠️ **Deviation from §5.2's table, which gives rhythm levels 0–3.** It keeps 0–4 in both scopes
     * instead. A per-scope maximum would mean the stored integer `3` meaning "all families" under one
     * module and "not quite all" under another, which is the one-identifier-two-meanings failure
     * docs/03-CURRICULUM.md §1 forbids for skill ids and that is no better here. Rhythm therefore gets
     * five timbre levels rather than four.
     */
    TIMBRE_VARIETY(4, scopes = setOf(Scope.RECOGNITION, Scope.RHYTHM)),

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
    PREDICT_GAP(3, scopes = setOf(Scope.PREDICTION)),

    /**
     * `M12.*` only. How far the sounded note deviates when it is not the stated degree: adjacent
     * diatonic degree → same degree wrong octave → chromatic neighbor → same degree detuned 30 cents
     * (docs/20-PHASE-2-SPEC.md §2.3). Level 3 is deliberately at the edge of reasonable and is an
     * advanced target, never a required mastery gate.
     */
    PREDICT_DEVIATION(3, scopes = setOf(Scope.PREDICTION)),

    /**
     * `M3.*` only. How much external timekeeping the item provides, 0..7 — see
     * [com.tonic.core.model.rhythm.MetronomeFadeLevel].
     * Rhythm's pedagogical centerpiece and the exact analog of [CADENCE_FADE]
     * (docs/40-PHASE-4-SPEC.md §3.2).
     *
     * [initialStepSize] is 1 for the identical reason [CADENCE_FADE]'s is, and the reason is not
     * symmetry: skipping a rung here does not make an item harder, it makes it unanswerable. A learner
     * vaulted from L1's every-beat metronome to L3 has never once been asked to hold a pulse through a
     * bar, and L4 — where the metronome stops entirely — is then a cliff rather than a step.
     */
    METRONOME_FADE(7, initialStepSize = 1, scopes = setOf(Scope.RHYTHM)),

    /** `M3.*` only. 0 = one bar, 4 = four bars (docs/40-PHASE-4-SPEC.md §5.2). Longer is more to hold. */
    PATTERN_LENGTH(4, scopes = setOf(Scope.RHYTHM)),

    /**
     * `M3.*` only. Distance from a comfortable tempo, **in both directions** — docs/40-PHASE-4-SPEC.md
     * §3.5.
     *
     * The one axis in the project that is not monotonic in an underlying quantity. Around 90–120 BPM is
     * easiest because it is near the natural spontaneous tapping rate; both faster *and slower* are
     * harder, fast because it outruns motor comfort and slow because a long inter-beat interval demands
     * genuine internal timekeeping rather than reactive entrainment. So the level measures deviation
     * from the center, not BPM, and a generator reading it as "tempo" would make level 3 uniformly fast
     * and quietly delete the harder half of the axis.
     */
    TEMPO_DEVIATION(3, scopes = setOf(Scope.RHYTHM)),

    /** `M3.*` only. What proportion of beats are subdivided rather than plain (docs/40-PHASE-4-SPEC.md §5.2). */
    RHYTHMIC_DENSITY(3, scopes = setOf(Scope.RHYTHM)),

    /**
     * `M3.*` only. How tight the accuracy window is — docs/40-PHASE-4-SPEC.md §6.2.
     *
     * Level 0 is deliberately forgiving: the early question is "did you feel the pattern," not "are you
     * a session drummer." Windows are a fraction of the beat rather than fixed milliseconds, because
     * 50 ms is generous at 60 BPM and impossible at 200.
     */
    TIMING_TOLERANCE(3, scopes = setOf(Scope.RHYTHM)),
    ;

    /** See [DifficultyAxis.scope]. */
    enum class Scope {
        /** Identify a sounded note against an established key: `M2.*`, `M10.*`, `M11.*`. */
        RECOGNITION,

        /** Audiate a named degree, then judge what actually sounded: `M12.*`. */
        PREDICTION,

        /**
         * Hear or produce a rhythm: `M3.*` — docs/40-PHASE-4-SPEC.md §5.2.
         *
         * Its own family rather than an extension of [RECOGNITION], because rhythm shares no axis with
         * pitch except [TIMBRE_VARIETY] and no skill node at all: §2 makes the track pedagogically
         * independent, and a learner can start `M3.BEAT_FIND` having never touched `M2`. Offering
         * `CADENCE_FADE` to a rhythm node, or `METRONOME_FADE` to `M2`, would be meaningless in both
         * directions.
         */
        RHYTHM,

        /**
         * Say which mode is sounding: `M9.*`. **Has no axes at all** — docs/20-PHASE-2-SPEC.md §3:
         * `M9`'s three nodes *are* its progression, each a separate skill rather than a level on a
         * shared axis. Modeled as a scope rather than as an absence so the scheduler, the replayer and
         * the session composer can all ask the same question of every node and get an answer, instead
         * of each carrying its own special case for the one module that has no axes.
         */
        MODE_ID,
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

        val PREDICTION_SCHEDULING_PRIORITY = listOf(PREDICT_GAP, PREDICT_DEVIATION)

        /**
         * Rhythm scheduling order — docs/40-PHASE-4-SPEC.md §5.2, verbatim.
         *
         * `METRONOME_FADE` first because it is pedagogically critical and the mastery criterion depends
         * on it, exactly as `CADENCE_FADE` leads the recognition list.
         */
        val RHYTHM_SCHEDULING_PRIORITY =
            listOf(
                METRONOME_FADE,
                TIMING_TOLERANCE,
                RHYTHMIC_DENSITY,
                PATTERN_LENGTH,
                TEMPO_DEVIATION,
                TIMBRE_VARIETY,
            )

        /** Every axis that applies to a recognition skill. The six Phase 1 axes, unchanged. */
        val RECOGNITION_AXES: List<DifficultyAxis> = entries.filter { Scope.RECOGNITION in it.scopes }

        /** Every axis that applies to a prediction skill. */
        val PREDICTION_AXES: List<DifficultyAxis> = entries.filter { Scope.PREDICTION in it.scopes }

        /** Every axis that applies to a rhythm skill — docs/40-PHASE-4-SPEC.md §5.2's six. */
        val RHYTHM_AXES: List<DifficultyAxis> = entries.filter { Scope.RHYTHM in it.scopes }

        fun axesFor(scope: Scope): List<DifficultyAxis> =
            when (scope) {
                Scope.RECOGNITION -> RECOGNITION_AXES
                Scope.PREDICTION -> PREDICTION_AXES
                Scope.RHYTHM -> RHYTHM_AXES
                Scope.MODE_ID -> emptyList()
            }

        fun schedulingPriorityFor(scope: Scope): List<DifficultyAxis> =
            when (scope) {
                Scope.RECOGNITION -> SCHEDULING_PRIORITY
                Scope.PREDICTION -> PREDICTION_SCHEDULING_PRIORITY
                Scope.RHYTHM -> RHYTHM_SCHEDULING_PRIORITY
                // Nothing to schedule. AxisScheduler picks no axis and returns its state untouched,
                // which is the correct behavior for a module whose progression is its node list.
                Scope.MODE_ID -> emptyList()
            }
    }
}
