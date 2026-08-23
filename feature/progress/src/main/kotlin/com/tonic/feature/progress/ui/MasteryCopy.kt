package com.tonic.feature.progress.ui

import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.ui.labels.displayLabel
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
internal fun copyFor(
    criterion: MasteryCriterion,
    labelStyle: LabelStyle,
): MasteryCopy =
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
        // Rhythm's three, docs/40-PHASE-4-SPEC.md §5.3. Worded as "rhythm" rather than "figure":
        // a figure is what the code calls a beat's fill, and the learner has never been taught that
        // word - docs/11-ONBOARDING-CLARITY.md forbids naming a thing the app has not explained.
        MasteryCriterion.Kind.FIGURE_COVERAGE ->
            MasteryCopy(
                R.string.progress_criterion_figure_coverage,
                listOf(criterion.measuredValue.roundToInt(), criterion.requiredValue.roundToInt()),
            )
        MasteryCriterion.Kind.WEAKEST_FIGURE_ACCURACY ->
            MasteryCopy(
                R.string.progress_criterion_weakest_figure,
                listOf(percent(criterion.measuredValue), percent(criterion.requiredValue)),
            )
        // The exact counterpart of CADENCE_FADE_MINIMUM, and phrased the same way for the same reason:
        // a fade level is a step count, not a rate, so it gets a sentence rather than a percentage.
        MasteryCriterion.Kind.METRONOME_FADE_MINIMUM ->
            MasteryCopy(R.string.progress_criterion_metronome_fade, emptyList())
        MasteryCriterion.Kind.WINDOW_COVERAGE ->
            MasteryCopy(
                R.string.progress_criterion_window_coverage,
                listOf(criterion.measuredValue.roundToInt(), criterion.requiredValue.roundToInt()),
            )
        // Names the note. The learner is being told a specific thing to go work on, and "the newest
        // note" would make them count back through their own history to work out which one that is.
        // Rendered in the learner's own label style, like every other degree on this screen.
        MasteryCriterion.Kind.FOCUS_DEGREE ->
            MasteryCopy(
                R.string.progress_criterion_focus_degree,
                listOf(criterion.subject?.displayLabel(labelStyle) ?: ""),
            )
        // The prediction counterpart of the cadence-fade sentence, and phrased the same way: what is
        // missing is not a number the learner can chase but a condition they have not yet practiced
        // under. Naming the gap in seconds would invite them to treat it as a timer, which §5.3 spends
        // a paragraph ruling out.
        MasteryCriterion.Kind.PREDICT_GAP_MINIMUM ->
            MasteryCopy(R.string.progress_criterion_predict_gap, emptyList())
        // Never phrased as "your d-prime is low": the statistic is the app's business, and naming it
        // would be jargon a learner cannot act on. The sentence says what it actually measures -
        // being right for the right reason rather than by a lucky answering habit.
        MasteryCriterion.Kind.D_PRIME ->
            MasteryCopy(R.string.progress_criterion_d_prime, emptyList())
    }

private fun percent(fraction: Double): Int = (fraction * 100).roundToInt()
