package com.tonic.feature.progress.ui

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.MasteryCriterion
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.MasteryVerdict

/** Sample [ProgressUiState] for `@Preview`s only - never referenced by production code. */
internal object PreviewStates {
    private fun verdict(overallAccuracy: Double) =
        MasteryVerdict(
            listOf(
                MasteryCriterion(
                    MasteryCriterion.Kind.OVERALL_ACCURACY,
                    overallAccuracy >= 0.90,
                    overallAccuracy,
                    0.90,
                ),
                MasteryCriterion(MasteryCriterion.Kind.DEGREE_COVERAGE, true, 5.0, 5.0),
                MasteryCriterion(MasteryCriterion.Kind.WEAKEST_DEGREE_ACCURACY, true, 0.85, 0.80),
                MasteryCriterion(MasteryCriterion.Kind.CONFUSION_CAP, true, 0.05, 0.15),
                MasteryCriterion(MasteryCriterion.Kind.CADENCE_FADE_MINIMUM, true, 4.0, 4.0),
            ),
        )

    val sample =
        ProgressUiState(
            isLoading = false,
            labelStyle = LabelStyle.NUMBERS,
            masteryMap =
                listOf(
                    MasteryMapNode(
                        SkillIds.M2_DEG_SET_1,
                        listOf(1, 3, 5).map(::ScaleDegree),
                        MasteryState.MASTERED,
                        null,
                    ),
                    MasteryMapNode(
                        SkillIds.M2_DEG_SET_2,
                        listOf(1, 2, 3, 5).map(::ScaleDegree),
                        MasteryState.IN_PROGRESS,
                        verdict(0.87),
                    ),
                    MasteryMapNode(
                        SkillIds.M2_DEG_SET_3,
                        listOf(1, 2, 3, 5, 6).map(::ScaleDegree),
                        MasteryState.LOCKED,
                        null,
                    ),
                ),
            degreeAccuracy =
                listOf(
                    DegreeAccuracy(ScaleDegree(1), 0.95),
                    DegreeAccuracy(ScaleDegree(2), 0.88),
                    DegreeAccuracy(ScaleDegree(3), 0.72),
                    DegreeAccuracy(ScaleDegree(5), null),
                ),
            confusionStatements = listOf(ConfusionStatement(ScaleDegree(4), ScaleDegree(3))),
            independenceCheck = IndependenceCheckSummary(IndependenceCheckStatus.NOT_YET_ATTEMPTED, 0.0),
        )
}
