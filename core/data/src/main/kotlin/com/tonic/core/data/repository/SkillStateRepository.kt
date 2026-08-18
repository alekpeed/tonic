package com.tonic.core.data.repository

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.SkillState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Materialized per-skill state — docs/05-DATA-MODEL.md §5. */
interface SkillStateRepository {
    /** Emits [SkillState.initial] if [skillId] has no persisted row yet. */
    fun observe(skillId: SkillId): Flow<SkillState>

    fun observeAll(): Flow<Map<SkillId, SkillState>>

    suspend fun update(state: SkillState)

    suspend fun dueForReview(now: Instant): List<SkillId>

    /** Reconstructs every skill's state from scratch by replaying [AttemptRepository]'s full log. */
    suspend fun rebuildFromAttempts()
}
