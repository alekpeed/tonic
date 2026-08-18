package com.tonic.core.data.repository

import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import kotlinx.coroutines.flow.Flow

/** The attempt log — docs/05-DATA-MODEL.md §5, the public surface of `:core:data` for it. */
interface AttemptRepository {
    suspend fun record(attempt: Attempt)

    fun recentAttempts(
        skillId: SkillId,
        limit: Int,
    ): Flow<List<Attempt>>

    suspend fun windowFor(
        skillId: SkillId,
        size: Int,
    ): List<Attempt>
}
