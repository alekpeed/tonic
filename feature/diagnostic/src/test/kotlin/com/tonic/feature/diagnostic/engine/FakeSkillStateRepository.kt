package com.tonic.feature.diagnostic.engine

import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.SkillState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** A bare in-memory stand-in - Stage 8's diagnostic loop only writes one row via [update], never reads. */
class FakeSkillStateRepository : SkillStateRepository {
    private val states = mutableMapOf<SkillId, SkillState>()
    private val flow = MutableStateFlow<Map<SkillId, SkillState>>(emptyMap())

    override fun observe(skillId: SkillId): Flow<SkillState> =
        flow.asStateFlow().map { it[skillId] ?: SkillState.initial(skillId) }

    override fun observeAll(): Flow<Map<SkillId, SkillState>> = flow.asStateFlow()

    override suspend fun update(state: SkillState) {
        states[state.skillId] = state
        flow.value = states.toMap()
    }

    override suspend fun dueForReview(now: Instant): List<SkillId> = emptyList()

    override suspend fun rebuildFromAttempts() = Unit

    override suspend fun rebuildFromAttempts(skillId: SkillId) = Unit

    fun get(skillId: SkillId): SkillState? = states[skillId]
}
