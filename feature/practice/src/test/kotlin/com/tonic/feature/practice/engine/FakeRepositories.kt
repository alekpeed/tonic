package com.tonic.feature.practice.engine

import com.tonic.core.data.repository.AttemptRepository
import com.tonic.core.data.repository.ConfusionRepository
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.engine.confusion.ConfusionTracker
import com.tonic.core.engine.replay.SkillStateReducer
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.ConfusionMatrix
import com.tonic.core.model.state.ConfusionState
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.Session
import com.tonic.core.model.state.SkillState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * In-memory stand-ins for the real Room-backed repositories (proven correct against a real database in
 * Stage 5's tests) that still use the REAL adaptive-engine algorithms - [SkillStateReducer] and
 * [ConfusionTracker] - so these tests exercise genuine "adapt" behavior, not a stub of it.
 */
class FakeAttemptRepository : AttemptRepository {
    private val attempts = mutableListOf<Attempt>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<Attempt>>(emptyList())

    val all: List<Attempt> get() = attempts.toList()

    override suspend fun record(attempt: Attempt) {
        attempts += attempt.copy(id = nextId++)
        flow.value = attempts.toList()
    }

    override fun recentAttempts(
        skillId: SkillId,
        limit: Int,
    ): Flow<List<Attempt>> = flow.asStateFlow().map { list -> list.filter { it.skillId == skillId }.takeLast(limit) }

    override suspend fun windowFor(
        skillId: SkillId,
        size: Int,
    ): List<Attempt> = attempts.filter { it.skillId == skillId }.takeLast(size)

    fun attemptsFor(skillId: SkillId): List<Attempt> = attempts.filter { it.skillId == skillId }
}

class FakeSkillStateRepository(
    private val attemptRepository: FakeAttemptRepository,
) : SkillStateRepository {
    private val states = mutableMapOf<SkillId, SkillState>()
    private val flow = MutableStateFlow<Map<SkillId, SkillState>>(emptyMap())

    override fun observe(skillId: SkillId): Flow<SkillState> =
        flow.asStateFlow().map {
            it[skillId]
                ?: SkillState.initial(skillId)
        }

    override fun observeAll(): Flow<Map<SkillId, SkillState>> = flow.asStateFlow()

    override suspend fun update(state: SkillState) {
        states[state.skillId] = state
        flow.value = states.toMap()
    }

    override suspend fun dueForReview(now: Instant): List<SkillId> =
        states.values.filter { it.fsrs.due != null && !it.fsrs.due!!.isAfter(now) }.map { it.skillId }

    override suspend fun rebuildFromAttempts() {
        for (skillId in states.keys + attemptRepository.all.map { it.skillId }.distinct()) {
            rebuildFromAttempts(skillId)
        }
    }

    override suspend fun rebuildFromAttempts(skillId: SkillId) {
        val attempts = attemptRepository.attemptsFor(skillId)
        states[skillId] = SkillStateReducer.replay(skillId, attempts)
        flow.value = states.toMap()
    }
}

class FakeConfusionRepository : ConfusionRepository {
    private val states = mutableMapOf<SkillId, ConfusionState>()

    override suspend fun record(
        skillId: SkillId,
        target: String,
        response: String,
    ) {
        val current = states[skillId] ?: ConfusionState(skillId)
        states[skillId] = ConfusionTracker.record(current, target, response, Instant.EPOCH)
    }

    override suspend fun matrixFor(skillId: SkillId): ConfusionMatrix =
        ConfusionTracker.toMatrix(
            states[skillId] ?: ConfusionState(skillId),
        )
}

class FakeSessionRepository : SessionRepository {
    private val sessions = mutableMapOf<Long, Session>()
    private var nextId = 1L

    override suspend fun create(
        rootSeed: Long,
        plannedItemCount: Int,
        startedAt: Instant,
    ): Session {
        val session =
            Session(
                id = nextId++,
                startedAt = startedAt,
                endedAt = null,
                plannedItemCount = plannedItemCount,
                completedItemCount = 0,
                rootSeed = rootSeed,
                resumeState = null,
            )
        sessions[session.id!!] = session
        return session
    }

    override suspend fun updateResumeState(
        sessionId: Long,
        completedItemCount: Int,
        resumeState: ResumeState?,
    ) {
        val existing = requireNotNull(sessions[sessionId])
        sessions[sessionId] = existing.copy(completedItemCount = completedItemCount, resumeState = resumeState)
    }

    override suspend fun complete(
        sessionId: Long,
        completedItemCount: Int,
        endedAt: Instant,
    ) {
        val existing = requireNotNull(sessions[sessionId])
        sessions[sessionId] =
            existing.copy(completedItemCount = completedItemCount, endedAt = endedAt, resumeState = null)
    }

    override suspend fun findResumable(): Session? =
        sessions.values.firstOrNull {
            it.endedAt == null &&
                it.resumeState != null
        }

    override suspend fun findById(sessionId: Long): Session? = sessions[sessionId]

    override suspend fun recentCompletedSessions(limit: Int): List<Session> =
        sessions.values
            .filter { it.endedAt != null }
            .sortedByDescending { it.startedAt }
            .take(limit)

    fun get(sessionId: Long): Session = requireNotNull(sessions[sessionId])
}
