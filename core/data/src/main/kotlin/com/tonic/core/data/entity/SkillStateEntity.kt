package com.tonic.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Materialized per-skill state — docs/05-DATA-MODEL.md §1. Rebuildable from [AttemptEntity]. */
@Entity(tableName = "skill_states")
internal data class SkillStateEntity(
    @PrimaryKey
    val skillId: String,
    val axisLevelsJson: String,
    val staircaseStateJson: String,
    val activeAxis: String?,
    val masteryStatus: String,
    val masteredAt: Long?,
    val fsrsStability: Double,
    val fsrsDifficulty: Double,
    val fsrsLastReview: Long?,
    val fsrsDue: Long?,
    val fsrsReps: Int,
    val fsrsLapses: Int,
    val totalAttempts: Int,
    val updatedAt: Long,
)
