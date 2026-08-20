package com.tonic.feature.progress.ui

import com.tonic.core.model.state.MasteryCriterion
import com.tonic.feature.progress.R
import kotlin.math.roundToInt

/** A plain-language mastery-criterion sentence: a format-string resource plus its arguments, kept out of the composable so it's testable without a composition - the `AnswerCopy` pattern from Stage 8's `:feature:diagnostic`. */
internal data class MasteryCopy(
    val textRes: Int,
    val args: List<Any>,
)

/**
 * docs/08-UI-SPEC.md §6's own examples verbatim: "you're at 87% — 90% needed" (an accuracy-shaped
 * criterion) and "the reference is still playing before every note; it needs to fade further"
 * ([MasteryCriterion.Kind.CADENCE_FADE_MINIMUM], which has no natural percentage reading - fade level is
 * a step count, not a rate - so it gets a fixed sentence instead of a templated one, matching the docs'
 * own choice to phrase that one differently).
 */
internal fun copyFor(criterion: MasteryCriterion): MasteryCopy =
    when (criterion.kind) {
        MasteryCriterion.Kind.OVERALL_ACCURACY ->
            MasteryCopy(
                R.string.progress_criterion_overall_accuracy,
                listOf(percent(criterion.measuredValue), percent(criterion.requiredValue)),
            )
        MasteryCriterion.Kind.DEGREE_COVERAGE ->
            MasteryCopy(
                R.string.progress_criterion_degree_coverage,
                listOf(criterion.measuredValue.roundToInt(), criterion.requiredValue.roundToInt()),
            )
        MasteryCriterion.Kind.WEAKEST_DEGREE_ACCURACY ->
            MasteryCopy(
                R.string.progress_criterion_weakest_degree,
                listOf(percent(criterion.measuredValue), percent(criterion.requiredValue)),
            )
        MasteryCriterion.Kind.CONFUSION_CAP ->
            MasteryCopy(
                R.string.progress_criterion_confusion_cap,
                listOf(percent(criterion.measuredValue), percent(criterion.requiredValue)),
            )
        MasteryCriterion.Kind.CADENCE_FADE_MINIMUM ->
            MasteryCopy(R.string.progress_criterion_cadence_fade, emptyList())
        MasteryCriterion.Kind.WINDOW_COVERAGE ->
            MasteryCopy(
                R.string.progress_criterion_window_coverage,
                listOf(criterion.measuredValue.roundToInt(), criterion.requiredValue.roundToInt()),
            )
        // Never phrased as "your d-prime is low": the statistic is the app's business, and naming it
        // would be jargon a learner cannot act on. The sentence says what it actually measures -
        // being right for the right reason rather than by a lucky answering habit.
        MasteryCriterion.Kind.D_PRIME ->
            MasteryCopy(R.string.progress_criterion_d_prime, emptyList())
    }

private fun percent(fraction: Double): Int = (fraction * 100).roundToInt()
