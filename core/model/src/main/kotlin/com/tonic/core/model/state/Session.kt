package com.tonic.core.model.state

import java.time.Instant

/**
 * One practice session — the row-level domain type behind `SessionRepository`
 * (docs/05-DATA-MODEL.md §1/§5). [id] is null for a session not yet
 * persisted. [endedAt] null means abandoned or still in progress;
 * [resumeState] non-null means it was interrupted and can be resumed
 * without losing or double-counting attempts.
 */
data class Session(
    val id: Long? = null,
    val startedAt: Instant,
    val endedAt: Instant?,
    val plannedItemCount: Int,
    val completedItemCount: Int,
    val rootSeed: Long,
    val resumeState: ResumeState?,
)
