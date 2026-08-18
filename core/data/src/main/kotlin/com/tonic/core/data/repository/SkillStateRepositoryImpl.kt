package com.tonic.core.data.repository

import com.tonic.core.data.dao.AttemptDao
import com.tonic.core.data.dao.SkillStateDao
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.state.SkillStateReplayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

internal class SkillStateRepositoryImpl
    @Inject
    constructor(
        private val skillStateDao: SkillStateDao,
        private val attemptDao: AttemptDao,
        private val replayer: SkillStateReplayer,
    ) : SkillStateRepository {
        override fun observe(skillId: SkillId): Flow<SkillState> =
            skillStateDao.observe(skillId.raw).map { it?.toDomain() ?: SkillState.initial(skillId) }

        override fun observeAll(): Flow<Map<SkillId, SkillState>> =
            skillStateDao.observeAll().map { list -> list.associate { SkillId(it.skillId) to it.toDomain() } }

        override suspend fun update(state: SkillState) {
            skillStateDao.upsert(state.toEntity())
        }

        override suspend fun dueForReview(now: Instant): List<SkillId> =
            skillStateDao.dueForReview(now.toEpochMilli()).map { SkillId(it) }

        override suspend fun rebuildFromAttempts() {
            for (raw in attemptDao.allSkillIds()) {
                val skillId = SkillId(raw)
                val attempts = attemptDao.allForSkill(raw).map { it.toDomain() }
                skillStateDao.upsert(replayer.replay(skillId, attempts).toEntity())
            }
        }
    }
