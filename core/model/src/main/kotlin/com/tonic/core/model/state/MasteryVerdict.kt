package com.tonic.core.model.state

import com.tonic.core.model.music.ScaleDegree

/**
 * The result of evaluating a skill node's mastery criteria,
 * docs/03-CURRICULUM.md §5.5 / docs/07-ADAPTIVE-ENGINE.md §7. All five
 * [criteria] must hold for [isMastered] to be true. Each criterion is
 * reported individually — "the progress UI shows the user what is actually
 * blocking them, which is far more useful than a percentage bar."
 */
data class MasteryVerdict(
    val criteria: List<MasteryCriterion>,
) {
    val isMastered: Boolean get() = criteria.all { it.met }

    /** The first unmet criterion, in the docs' §5.5 order — what the progress UI surfaces. */
    val blockingCriterion: MasteryCriterion? get() = criteria.firstOrNull { !it.met }
}

/** One of the five docs/03-CURRICULUM.md §5.5 criteria, with the measured value that decided it. */
data class MasteryCriterion(
    val kind: Kind,
    val met: Boolean,
    val measuredValue: Double,
    val requiredValue: Double,
    /**
     * What the criterion is about, when it is about one specific thing — the degree for
     * [Kind.FOCUS_DEGREE], null for every criterion that judges the window as a whole.
     *
     * Carried here so the progress screen can name the note rather than saying "the newest one". §5.5's
     * whole reason for reporting criteria individually is that a learner can act on what is blocking
     * them, and "♭2 isn't solid yet" is actionable in a way that an anonymous sentence is not. Kept as
     * the domain type rather than a rendered string so the screen applies the learner's own label style
     * to it, the same as every other degree it shows.
     */
    val subject: ScaleDegree? = null,
) {
    enum class Kind {
        /** Overall accuracy ≥ 90% over the last 30 attempts at current axis levels. */
        OVERALL_ACCURACY,

        /** Every active degree has ≥ 5 attempts in the window. */
        DEGREE_COVERAGE,

        /** No individual degree is below 80% accuracy. */
        WEAKEST_DEGREE_ACCURACY,

        /** No single confusion pair accounts for more than 15% of window attempts. */
        CONFUSION_CAP,

        /**
         * Every rhythmic figure the node teaches has ≥ 5 attempts in the window — `M3.*`'s counterpart
         * of [DEGREE_COVERAGE], docs/40-PHASE-4-SPEC.md §8.
         *
         * The unit is the *beat's fill*, not the whole pattern. A pattern is very nearly unique — vary
         * one sixteenth and it is a different pattern — so coverage over patterns could never be met by
         * anyone. A beat's fill is a small alphabet that recurs, and it is the unit §3.1's Takadimi
         * already names.
         */
        FIGURE_COVERAGE,

        /** No individual rhythmic figure is below 80% accuracy — `M3.*`'s [WEAKEST_DEGREE_ACCURACY]. */
        WEAKEST_FIGURE_ACCURACY,

        /**
         * The share of each pattern the learner reproduced, averaged over the window ≥ 90% —
         * docs/40-PHASE-4-SPEC.md §5.3 criterion 1, "right notes in the right places".
         *
         * Per *event*, not per attempt, which is what makes it different from [OVERALL_ACCURACY]: an
         * attempt that struck nine of ten events counts as nine tenths here and as a failure there.
         * §6.1 wants both, because a learner who drops one sound out of a four-bar pattern and a
         * learner who taps a different rhythm entirely are not in the same place.
         */
        PATTERN_ACCURACY,

        /**
         * Whole patterns landed inside the current tolerance on ≥ 85% of the window —
         * docs/40-PHASE-4-SPEC.md §5.3 criterion 2.
         *
         * "Within the current tolerance" is the whole of what timing contributes here. §6.1 says
         * timing precision gates "only via `TIMING_TOLERANCE`", and §6.3 forbids raw asynchrony
         * magnitude from entering mastery at all — so this counts attempts that were correct at the
         * window the learner actually faced, and reads nothing about how far inside it they fell.
         * As `TIMING_TOLERANCE` rises the window narrows and the same performance stops qualifying,
         * which is how the axis does its gating.
         */
        TIMING_CONSISTENCY,

        /**
         * The learner is not progressively rushing or dragging — docs/40-PHASE-4-SPEC.md §5.3
         * criterion 3.
         *
         * Measured as the trend of asynchrony across a pattern, never as its magnitude, and §5.3 gives
         * the reason in one line: "a constant offset is a calibration artifact while a growing offset
         * is a real timekeeping failure." A learner uniformly 40 ms late has drifted by zero.
         *
         * This is the one sanctioned reader of asynchrony under §6.3, which permits it for "drift
         * analysis" by name while forbidding the magnitude everywhere else.
         */
        TIMING_DRIFT,

        /**
         * `METRONOME_FADE` level ≥ 4 — docs/40-PHASE-4-SPEC.md §5.3 criterion 4, and the exact
         * counterpart of [CADENCE_FADE_MINIMUM].
         *
         * L4 is the first level at which the metronome stops under the pattern (§3.2). Without this a
         * learner masters rhythm having never once kept time unaided, which is the same crutch
         * dependence [CADENCE_FADE_MINIMUM] exists to catch in the pitch track.
         */
        METRONOME_FADE_MINIMUM,

        /** CADENCE_FADE level ≥ 4 — "the one that matters." Without it, mastery can hide crutch dependence. */
        CADENCE_FADE_MINIMUM,

        /**
         * Enough attempts in the window to judge anything at all. Only used by binary-answer nodes
         * (`M9.*`, `M12.*`), where a short window plus a lucky streak would otherwise certify a
         * learner off a handful of coin flips.
         */
        WINDOW_COVERAGE,

        /**
         * The degree this node *introduces* has both enough attempts to judge and ≥ 80% accuracy on
         * its own — docs/20-PHASE-2-SPEC.md §3, `M11`'s sixth criterion. [WEAKEST_DEGREE_ACCURACY]
         * already holds every degree to 80%, but its coverage requirement scales down as the active
         * set grows: at twelve simultaneous degrees a 30-item window gives each one about two
         * attempts, and two lucky answers are not evidence of anything. This criterion holds the new
         * degree to a real sample, so a learner cannot master "the node that adds ♭2" while being at
         * chance on ♭2 specifically, carried by eleven confident answers elsewhere.
         */
        FOCUS_DEGREE,

        /**
         * `PREDICT_GAP` level ≥ 2 — docs/20-PHASE-2-SPEC.md §3's mastery rule for `M12`, and the exact
         * counterpart of [CADENCE_FADE_MINIMUM] for prediction nodes. A 1-second gap is short enough
         * that the sounded note can be judged against a still-ringing echo of the cadence; at 3.5
         * seconds there is nothing left to compare against except what the learner built internally,
         * which is the entire skill. Without this a learner could master `M12` while never having
         * audiated anything.
         */
        PREDICT_GAP_MINIMUM,

        /**
         * d-prime ≥ 2.0 — sensitivity with response bias factored out. Only used by binary-answer
         * nodes (docs/20-PHASE-2-SPEC.md §3): on a two-choice task raw accuracy cannot tell a learner
         * who hears the distinction from one who found a lucky answering habit.
         */
        D_PRIME,
    }
}
