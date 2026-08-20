package com.tonic.feature.settings.ui

import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.Session
import java.time.Instant

/** Just enough of [SessionRepository] for the discard control - one optional resumable session. */
internal class FakeSessionRepository(
    private var resumable: Session? = null,
) : SessionRepository {
    var discardedAt: Instant? = null

    override suspend fun create(
        rootSeed: Long,
        plannedItemCount: Int,
        startedAt: Instant,
    ): Session = throw UnsupportedOperationException("not exercised by settings")

    override suspend fun updateResumeState(
        sessionId: Long,
        completedItemCount: Int,
        resumeState: ResumeState?,
    ) = throw UnsupportedOperationException("not exercised by settings")

    override suspend fun complete(
        sessionId: Long,
        completedItemCount: Int,
        endedAt: Instant,
    ) = throw UnsupportedOperationException("not exercised by settings")

    override suspend fun findResumable(): Session? = resumable

    override suspend fun discardResumable(now: Instant): Boolean {
        val had = resumable != null
        resumable = null
        if (had) discardedAt = now
        return had
    }

    override suspend fun findById(sessionId: Long): Session? = null

    override suspend fun recentCompletedSessions(limit: Int): List<Session> = emptyList()
}
