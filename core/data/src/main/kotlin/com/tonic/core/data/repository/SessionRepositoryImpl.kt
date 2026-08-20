package com.tonic.core.data.repository

import com.tonic.core.data.dao.SessionDao
import com.tonic.core.data.db.JsonCodec
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.Session
import java.time.Instant
import javax.inject.Inject

internal class SessionRepositoryImpl
    @Inject
    constructor(
        private val dao: SessionDao,
    ) : SessionRepository {
        override suspend fun create(
            rootSeed: Long,
            plannedItemCount: Int,
            startedAt: Instant,
        ): Session {
            val session =
                Session(
                    startedAt = startedAt,
                    endedAt = null,
                    plannedItemCount = plannedItemCount,
                    completedItemCount = 0,
                    rootSeed = rootSeed,
                    resumeState = null,
                )
            val id = dao.insert(session.toEntity())
            return session.copy(id = id)
        }

        override suspend fun updateResumeState(
            sessionId: Long,
            completedItemCount: Int,
            resumeState: ResumeState?,
        ) {
            val existing = requireNotNull(dao.findById(sessionId)) { "No session with id $sessionId" }
            dao.update(
                existing.copy(
                    completedItemCount = completedItemCount,
                    resumeStateJson = resumeState?.let { JsonCodec.encodeResumeState(it) },
                ),
            )
        }

        override suspend fun discardResumable(now: Instant): Boolean {
            val resumable = findResumable() ?: return false
            val id = resumable.id ?: return false
            // Same operation as declining a resume offer: complete() clears resumeStateJson and stamps
            // the end. The attempts recorded before the interruption stay exactly as they were.
            complete(id, resumable.completedItemCount, now)
            return true
        }

        override suspend fun complete(
            sessionId: Long,
            completedItemCount: Int,
            endedAt: Instant,
        ) {
            val existing = requireNotNull(dao.findById(sessionId)) { "No session with id $sessionId" }
            dao.update(
                existing.copy(
                    completedItemCount = completedItemCount,
                    endedAt = endedAt.toEpochMilli(),
                    resumeStateJson = null,
                ),
            )
        }

        override suspend fun findResumable(): Session? = dao.findResumable()?.toDomain()

        override suspend fun findById(sessionId: Long): Session? = dao.findById(sessionId)?.toDomain()

        override suspend fun recentCompletedSessions(limit: Int): List<Session> =
            dao.recentCompleted(limit).map { it.toDomain() }
    }
