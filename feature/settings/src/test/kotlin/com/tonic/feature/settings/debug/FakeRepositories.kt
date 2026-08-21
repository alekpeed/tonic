package com.tonic.feature.settings.debug

import com.tonic.core.data.repository.AttemptRepository
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.engine.replay.SkillStateReducer
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.Session
import com.tonic.core.model.state.SkillState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory stand-ins for `:core:data`'s Room-backed repositories, same shape as
 * `:feature:practice`'s own fakes — real [SkillStateReducer] behind [FakeSkillStateRepository] so
 * [com.tonic.feature.settings.debug.DebugSkillJumper]'s tests exercise genuine mastery replay, not a
 * stub of it.
 */
class FakeAttemptRepository : AttemptRepository {
    private val attempts = CopyOnWriteArrayList<Attempt>()
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
    private val states = ConcurrentHashMap<SkillId, SkillState>()
    private val flow = MutableStateFlow<Map<SkillId, SkillState>>(emptyMap())

    override fun observe(skillId: SkillId): Flow<SkillState> =
        flow.asStateFlow().map { it[skillId] ?: SkillState.initial(skillId) }

    override fun observeAll(): Flow<Map<SkillId, SkillState>> = flow.asStateFlow()

    override suspend fun update(state: SkillState) {
        states[state.skillId] = state
        flow.value = states.toMap()
    }

    override suspend fun dueForReview(now: Instant): List<SkillId> = emptyList()

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

class FakeSessionRepository : SessionRepository {
    private val sessions = ConcurrentHashMap<Long, Session>()
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

    override suspend fun discardResumable(now: Instant): Boolean = false

    override suspend fun findResumable(): Session? = null

    override suspend fun findById(sessionId: Long): Session? = sessions[sessionId]

    override suspend fun recentCompletedSessions(limit: Int): List<Session> =
        sessions.values
            .filter { it.endedAt != null }
            .sortedByDescending { it.startedAt }
            .take(limit)

    fun get(sessionId: Long): Session = requireNotNull(sessions[sessionId])
}
