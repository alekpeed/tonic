package com.tonic.core.model.state

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

        /** CADENCE_FADE level ≥ 4 — "the one that matters." Without it, mastery can hide crutch dependence. */
        CADENCE_FADE_MINIMUM,
    }
}
