package com.tonic.core.model.state

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import kotlinx.serialization.Serializable

/**
 * What `SessionComposer` (Stage 4/6) assembles before a practice session
 * starts — docs/07-ADAPTIVE-ENGINE.md §8. Every item seed derives from
 * [rootSeed] combined with slot index, so the whole session is reproducible
 * from one number (docs/04-ARCHITECTURE.md §4).
 *
 * `@Serializable`: this is the shape of `resumeStateJson`
 * (docs/05-DATA-MODEL.md §1) via [ResumeState].
 */
@Serializable
data class SessionPlan(
    val rootSeed: Long,
    val plannedSlots: List<PlannedSlot>,
)

/** One planned item in a [SessionPlan]. */
@Serializable
data class PlannedSlot(
    val skillId: SkillId,
    val axisLevels: Map<DifficultyAxis, Int>,
    /** First 5 items of a session — excluded from mastery evaluation and the staircase, still recorded. */
    val isWarmup: Boolean = false,
    /** Due-review slot (FSRS) rather than current-node work. */
    val isReview: Boolean = false,
)

/**
 * `resumeStateJson` — docs/05-DATA-MODEL.md §1: non-null on the `sessions`
 * row if the session was interrupted, enabling resume without losing or
 * double-counting attempts.
 */
@Serializable
data class ResumeState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val plan: SessionPlan,
    val completedSlotIndex: Int,
    /**
     * Wall-clock budget the session had left when it was interrupted, so a resumed session keeps the
     * user's session-length promise instead of running unbounded (docs/07-ADAPTIVE-ENGINE.md §8's
     * wall-clock bound). Nullable with a default for rows written before this field existed — those
     * fall back to an estimate from the remaining plan.
     */
    val budgetRemainingSeconds: Long? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
