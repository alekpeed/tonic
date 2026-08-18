package com.tonic.core.data.repository

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.ConfusionMatrix

/** The `(target, response)` confusion matrix — docs/05-DATA-MODEL.md §5 / docs/07-ADAPTIVE-ENGINE.md §4. */
interface ConfusionRepository {
    suspend fun record(
        skillId: SkillId,
        target: String,
        response: String,
    )

    suspend fun matrixFor(skillId: SkillId): ConfusionMatrix
}
