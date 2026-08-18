package com.tonic.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One practice session — docs/05-DATA-MODEL.md §1. */
@Entity(tableName = "sessions")
internal data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val plannedItemCount: Int,
    val completedItemCount: Int,
    val rootSeed: Long,
    val resumeStateJson: String?,
)
