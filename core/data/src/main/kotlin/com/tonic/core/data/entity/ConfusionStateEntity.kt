package com.tonic.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted [com.tonic.core.model.state.ConfusionState] - the raw sliding
 * window of recent `(target, response)` pairs plus all-time counts, one
 * row per skill.
 *
 * docs/05-DATA-MODEL.md §1 originally specified a `confusion_cells` table
 * with one row per `(skillId, targetLabel, responseLabel)` and plain
 * `count`/`windowCount` columns. That shape can display a snapshot, but
 * cannot correctly *maintain* a true last-100-attempts sliding window on
 * its own: when the window slides and the oldest pair drops out, you need
 * to know which specific pair that was, which an aggregated count can't
 * tell you. Storing the raw ordered pair list (JSON, mirroring how
 * `staircaseStateJson` already stores algorithmic internal state) is what
 * actually makes the window exact and restorable across process death -
 * so this table replaces `confusion_cells`; the aggregated
 * [com.tonic.core.model.state.ConfusionMatrix] callers need is derived on
 * demand via [com.tonic.core.model.state.ConfusionTracking.toMatrix].
 * docs/05-DATA-MODEL.md §1 has been updated to match.
 */
@Entity(tableName = "confusion_state")
internal data class ConfusionStateEntity(
    @PrimaryKey
    val skillId: String,
    val stateJson: String,
    val updatedAt: Long,
)
