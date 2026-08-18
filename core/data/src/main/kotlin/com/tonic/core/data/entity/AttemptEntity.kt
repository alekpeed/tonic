package com.tonic.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The append-only event log — docs/05-DATA-MODEL.md §1: "everything else
 * is derivable from this table; treat it as the source of truth." Never
 * exposed outside `:core:data`; [com.tonic.core.data.repository.AttemptRepositoryImpl]
 * maps to/from [com.tonic.core.model.attempts.Attempt].
 */
@Entity(
    tableName = "attempts",
    indices = [
        Index(value = ["skillId", "timestamp"]),
        Index(value = ["sessionId"]),
        Index(value = ["skillId", "targetLabel", "responseLabel"]),
    ],
)
internal data class AttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val skillId: String,
    val sessionId: Long,
    val itemSeed: Long,
    val axisLevelsJson: String,
    val targetLabel: String,
    val responseLabel: String?,
    val correct: Boolean,
    val latencyMs: Long,
    val replayCount: Int,
    val keyPitchClass: Int,
    val targetMidi: Int,
    val timbreId: String,
    val cadenceFadeLevel: Int,
    val timestamp: Long,
    val isWarmup: Boolean,
    val isAbandoned: Boolean,
)
