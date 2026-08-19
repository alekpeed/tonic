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

    /**
     * Discards the in-progress/resumable session, if one exists: clears its `resumeStateJson` and
     * closes the row, so [findResumable] stops offering it. Returns true if something was discarded.
     *
     * Deliberately closes rather than deletes the row: recorded attempts reference the session, and
     * they are the log every skill's mastery state replays from (docs/05-DATA-MODEL.md) - deleting the
     * row would orphan or destroy real progress. Touches nothing else: no attempts, no skill state, no
     * DataStore settings, so diagnostic placement and mastery are untouched by construction. Exists for
     * the escape hatch in Settings - a session saved under a since-fixed bug must not strand the user
     * in the broken state across an update.
     */
    suspend fun discardResumable(now: java.time.Instant): Boolean

    /** Looks up one session by id — the Summary screen's own `sessionId` nav argument. */
    suspend fun findById(sessionId: Long): Session?

    /** Most-recently-started completed sessions, newest first — bounded by [limit]. Streak's raw material. */
    suspend fun recentCompletedSessions(limit: Int): List<Session>
}
