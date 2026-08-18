package com.tonic.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tonic.core.data.entity.ConfusionStateEntity

@Dao
internal interface ConfusionStateDao {
    @Upsert
    suspend fun upsert(entity: ConfusionStateEntity)

    @Query("SELECT * FROM confusion_state WHERE skillId = :skillId")
    suspend fun find(skillId: String): ConfusionStateEntity?
}
