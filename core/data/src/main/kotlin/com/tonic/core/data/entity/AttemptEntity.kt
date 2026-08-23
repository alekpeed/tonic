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
    val isIndependenceCheckProbe: Boolean,
    /**
     * docs/30-PHASE-3-SPEC.md §4/§7, added in schema version 2. Both carry defaults so the migration
     * is a pure `ALTER TABLE ADD COLUMN` over an append-only log that must never be rewritten: every
     * row written before Phase 3 reads back as a tapped attempt with no pitch data, which is what it
     * was.
     */
    val inputMethod: String = "TAP",
    val sungCents: Int? = null,
    /**
     * docs/40-PHASE-4-SPEC.md §8, added in schema version 3. Every one is null on a non-rhythm attempt,
     * which is every attempt written before Phase 4 — the migration is a pure `ALTER TABLE ADD COLUMN`
     * over an append-only log that must never be rewritten.
     *
     * `tapTimestampsMs` is what makes §4.4's promise real: "they are recorded with the attempt, which
     * makes any real session fully replayable and any scoring bug reproducible offline." Stored as the
     * corrected millisecond offsets from the pattern's start, not as raw nanosecond instants, because
     * the instants mean nothing without the output timebase of a playback that is long over.
     *
     * `toleranceUsedMs` and `calibrationOffsetUsedMs` are recorded rather than recomputed, so an
     * attempt can be re-scored later against exactly what it faced. A tolerance recomputed from today's
     * axis levels would answer a different question than the one the learner was asked.
     */
    val tapTimestampsMs: String? = null,
    val calibrationOffsetUsedMs: Double? = null,
    val toleranceUsedMs: Double? = null,
    val perEventAsynchronyMs: String? = null,
    val extraTaps: Int? = null,
    val missedTaps: Int? = null,
)
