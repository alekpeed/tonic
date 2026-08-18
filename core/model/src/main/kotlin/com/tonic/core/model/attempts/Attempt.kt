package com.tonic.core.model.attempts

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant

/**
 * One recorded response. The append-only event log — docs/05-DATA-MODEL.md
 * §1: "everything else is derivable from this table; treat it as the
 * source of truth." [id]/[sessionId] are null/unset for an attempt not yet
 * persisted.
 */
data class Attempt(
    val id: Long? = null,
    val skillId: SkillId,
    val sessionId: Long,
    /** Regenerates the exact item this attempt answered — docs/05-DATA-MODEL.md §1. */
    val itemSeed: Long,
    /** Axis levels at generation time, not current levels. */
    val axisLevels: Map<DifficultyAxis, Int>,
    val targetLabel: String,
    /** Null if skipped or abandoned (docs/06-AUDIO-ENGINE.md §8: an interruption marks the attempt abandoned). */
    val responseLabel: String?,
    val correct: Boolean,
    /** Recorded for diagnostics, never scored — docs/02-PEDAGOGY.md §6. */
    val latencyMs: Long,
    val replayCount: Int,
    val keyPitchClass: Int,
    val targetMidi: Int,
    val timbreId: String,
    /** Denormalized from axisLevels for cheap querying — this is the axis that matters most (docs/05-DATA-MODEL.md §1). */
    val cadenceFadeLevel: Int,
    val timestamp: Instant,
    /**
     * Warm-up attempts are still recorded but excluded from mastery
     * evaluation and the staircase — docs/07-ADAPTIVE-ENGINE.md §8.
     */
    val isWarmup: Boolean = false,
    /** True if the item was abandoned (interruption, session kill) rather than genuinely answered or skipped. */
    val isAbandoned: Boolean = false,
)
