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

    /**
     * Reconstructs just [skillId]'s state from its own attempt history. The scoped equivalent of
     * [rebuildFromAttempts] - the practice loop calls this after every real attempt (docs/09-BUILD-PLAN.md
     * Stage 6), and re-replaying one skill's history is cheap at Phase 1's scale, but there is no reason
     * to also recompute every *other* skill's state on each answer.
     */
    suspend fun rebuildFromAttempts(skillId: SkillId)
}
