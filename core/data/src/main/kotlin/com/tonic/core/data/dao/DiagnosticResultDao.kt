package com.tonic.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tonic.core.data.entity.DiagnosticResultEntity

@Dao
internal interface DiagnosticResultDao {
    @Insert
    suspend fun insert(entity: DiagnosticResultEntity): Long

    @Query("SELECT * FROM diagnostic_results ORDER BY completedAt DESC LIMIT 1")
    suspend fun latest(): DiagnosticResultEntity?
}
