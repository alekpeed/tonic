package com.tonic.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tonic.core.data.entity.AttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AttemptDao {
    @Insert
    suspend fun insert(entity: AttemptEntity): Long

    /**
     * The most recent [limit] attempts for [skillId], in chronological (oldest-first) order - matching
     * how `:core:engine` consumes a mastery window (`window.last()` is the most recent attempt). The
     * inner query does the DESC-then-LIMIT selection; the outer ORDER BY restores ascending order.
     */
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM attempts WHERE skillId = :skillId ORDER BY timestamp DESC, id DESC LIMIT :limit
        ) ORDER BY timestamp ASC, id ASC
        """,
    )
    fun recentAttempts(
        skillId: String,
        limit: Int,
    ): Flow<List<AttemptEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM attempts WHERE skillId = :skillId ORDER BY timestamp DESC, id DESC LIMIT :size
        ) ORDER BY timestamp ASC, id ASC
        """,
    )
    suspend fun windowFor(
        skillId: String,
        size: Int,
    ): List<AttemptEntity>

    /** Every attempt ever recorded for [skillId], in chronological order — for `rebuildFromAttempts()`. */
    @Query("SELECT * FROM attempts WHERE skillId = :skillId ORDER BY timestamp ASC, id ASC")
    suspend fun allForSkill(skillId: String): List<AttemptEntity>

    /** Every skill that has at least one recorded attempt — the set `rebuildFromAttempts()` rebuilds. */
    @Query("SELECT DISTINCT skillId FROM attempts")
    suspend fun allSkillIds(): List<String>
}
