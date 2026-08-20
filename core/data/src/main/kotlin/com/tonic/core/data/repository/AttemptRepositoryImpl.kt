package com.tonic.core.data.repository

import com.tonic.core.data.dao.AttemptDao
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class AttemptRepositoryImpl
    @Inject
    constructor(
        private val dao: AttemptDao,
    ) : AttemptRepository {
        override suspend fun record(attempt: Attempt) {
            dao.insert(attempt.toEntity())
        }

        override fun recentAttempts(
            skillId: SkillId,
            limit: Int,
        ): Flow<List<Attempt>> = dao.recentAttempts(skillId.raw, limit).map { list -> list.map { it.toDomain() } }

        override suspend fun windowFor(
            skillId: SkillId,
            size: Int,
        ): List<Attempt> = dao.windowFor(skillId.raw, size).map { it.toDomain() }
    }
