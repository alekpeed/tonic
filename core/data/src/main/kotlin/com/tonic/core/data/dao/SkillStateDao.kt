package com.tonic.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tonic.core.data.entity.SkillStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface SkillStateDao {
    @Upsert
    suspend fun upsert(entity: SkillStateEntity)

    @Query("SELECT * FROM skill_states WHERE skillId = :skillId")
    fun observe(skillId: String): Flow<SkillStateEntity?>

    @Query("SELECT * FROM skill_states")
    fun observeAll(): Flow<List<SkillStateEntity>>

    /** Mastered nodes ([fsrsDue] is only ever set once a node is mastered) whose review is due. */
    @Query("SELECT skillId FROM skill_states WHERE fsrsDue IS NOT NULL AND fsrsDue <= :now")
    suspend fun dueForReview(now: Long): List<String>

    /** Debug tooling only — see `DebugProgressRepository`. Never called by the practice loop. */
    @Query("DELETE FROM skill_states")
    suspend fun deleteAll()

    /** Every skill state. Export only — see [AttemptDao.allAttempts]. */
    @Query("SELECT * FROM skill_states ORDER BY skillId ASC")
    suspend fun allSkillStates(): List<SkillStateEntity>
}
