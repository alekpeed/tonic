package com.tonic.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tonic.core.data.entity.SessionEntity

@Dao
internal interface SessionDao {
    @Insert
    suspend fun insert(entity: SessionEntity): Long

    @Update
    suspend fun update(entity: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun findById(id: Long): SessionEntity?

    /** The most recent interrupted-but-resumable session, if any. */
    @Query(
        "SELECT * FROM sessions WHERE endedAt IS NULL AND resumeStateJson IS NOT NULL ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun findResumable(): SessionEntity?

    /** Most-recently-started completed sessions, newest first — the raw material for streak computation. */
    @Query("SELECT * FROM sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentCompleted(limit: Int): List<SessionEntity>

    /** Every session, oldest first. Export only — see [AttemptDao.allAttempts]. */
    @Query("SELECT * FROM sessions ORDER BY id ASC")
    suspend fun allSessions(): List<SessionEntity>
}
