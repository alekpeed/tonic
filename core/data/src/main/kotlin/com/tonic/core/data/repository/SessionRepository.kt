package com.tonic.core.data.repository

import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.Session
import java.time.Instant

/** Practice sessions — docs/05-DATA-MODEL.md §1/§5 ("create, complete, findResumable"). */
interface SessionRepository {
    suspend fun create(
        rootSeed: Long,
        plannedItemCount: Int,
        startedAt: Instant,
    ): Session

    /** Persists interruption/resume progress mid-session without ending it. */
    suspend fun updateResumeState(
        sessionId: Long,
        completedItemCount: Int,
        resumeState: ResumeState?,
    )

    suspend fun complete(
        sessionId: Long,
        completedItemCount: Int,
        endedAt: Instant,
    )

    /** The most recent interrupted-but-resumable session, if any. */
    suspend fun findResumable(): Session?

    /** Looks up one session by id — the Summary screen's own `sessionId` nav argument. */
    suspend fun findById(sessionId: Long): Session?

    /** Most-recently-started completed sessions, newest first — bounded by [limit]. Streak's raw material. */
    suspend fun recentCompletedSessions(limit: Int): List<Session>
}
