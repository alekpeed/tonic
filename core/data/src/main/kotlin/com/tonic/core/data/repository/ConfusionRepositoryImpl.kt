package com.tonic.core.data.repository

import com.tonic.core.data.dao.ConfusionStateDao
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.ConfusionMatrix
import com.tonic.core.model.state.ConfusionState
import com.tonic.core.model.state.ConfusionTracking
import com.tonic.core.model.time.Clock
import javax.inject.Inject

internal class ConfusionRepositoryImpl
    @Inject
    constructor(
        private val dao: ConfusionStateDao,
        private val tracking: ConfusionTracking,
        private val clock: Clock,
    ) : ConfusionRepository {
        override suspend fun record(
            skillId: SkillId,
            target: String,
            response: String,
        ) {
            val current = dao.find(skillId.raw)?.toDomain() ?: ConfusionState(skillId)
            val updated = tracking.record(current, target, response, clock.now())
            dao.upsert(updated.toEntity())
        }

        override suspend fun matrixFor(skillId: SkillId): ConfusionMatrix {
            val state = dao.find(skillId.raw)?.toDomain() ?: ConfusionState(skillId)
            return tracking.toMatrix(state)
        }
    }
