package com.tonic.feature.progress.ui

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.MasteryVerdict

/** One row of the mastery map. [verdict] is null for [MasteryState.LOCKED] and [MasteryState.MASTERED] - nothing useful to drill into for either (docs/08-UI-SPEC.md §6). */
data class MasteryMapNode(
    val skillId: SkillId,
    val activeDegrees: List<ScaleDegree>,
    val masteryState: MasteryState,
    val verdict: MasteryVerdict?,
)

/** [accuracy] is null when [degree] has no attempts in the current confusion window yet. */
data class DegreeAccuracy(
    val degree: ScaleDegree,
    val accuracy: Double?,
)

/** One `(target heard as response)` plain-language confusion statement — docs/08-UI-SPEC.md §6. */
data class ConfusionStatement(
    val target: ScaleDegree,
    val response: ScaleDegree,
)

enum class IndependenceCheckStatus { NOT_YET_ATTEMPTED, PASSED, FAILED }

data class IndependenceCheckSummary(
    val status: IndependenceCheckStatus,
    val accuracy: Double,
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val labelStyle: LabelStyle = LabelStyle.NUMBERS,
    val masteryMap: List<MasteryMapNode> = emptyList(),
    val expandedNode: SkillId? = null,
    val degreeAccuracy: List<DegreeAccuracy> = emptyList(),
    val confusionStatements: List<ConfusionStatement> = emptyList(),
    val independenceCheck: IndependenceCheckSummary? = null,
)
