package com.tonic.core.model.state

import com.tonic.core.model.ids.SkillId
import java.time.Instant

/**
 * A `(target, response)` count matrix for one skill, over a rolling window
 * of the last 100 attempts — docs/07-ADAPTIVE-ENGINE.md §4. Drives both the
 * mastery evaluator's confusion-cap criterion and remediation item-weight
 * boosts.
 */
data class ConfusionMatrix(
    val skillId: SkillId,
    val cells: List<ConfusionCell>,
) {
    /** Total attempts for [target] across all responses in the window. */
    fun attemptsFor(target: String): Int = cells.filter { it.target == target }.sumOf { it.windowCount }

    /** Correct-response fraction for [target], or null if it has no attempts yet. */
    fun accuracyFor(target: String): Double? {
        val total = attemptsFor(target)
        if (total == 0) return null
        val correct = cells.firstOrNull { it.target == target && it.response == target }?.windowCount ?: 0
        return correct.toDouble() / total
    }

    /** Cells where the response differs from the target, above [minShareOfTarget] of that target's attempts. */
    fun confusionPairsAbove(minShareOfTarget: Double): List<ConfusionCell> =
        cells.filter { cell ->
            cell.target != cell.response &&
                attemptsFor(cell.target).let { total ->
                    total > 0 &&
                        cell.windowCount.toDouble() / total > minShareOfTarget
                }
        }
}

/** One `(target, response)` cell — composite PK `(skillId, targetLabel, responseLabel)` in docs/05-DATA-MODEL.md §1. */
data class ConfusionCell(
    val target: String,
    val response: String,
    val count: Int,
    val windowCount: Int,
    val updatedAt: Instant,
)

/**
 * Per-skill confusion-tracking algorithmic state — the sliding window and
 * all-time counts that [ConfusionMatrix] is derived from. Threaded by the
 * caller across successive `ConfusionTracker.record` calls
 * (docs/07-ADAPTIVE-ENGINE.md §4). Lives here, not in `:core:engine` where
 * the tracking logic itself lives, because `:core:data` needs to persist
 * and reload it (docs/05-DATA-MODEL.md §1) without depending on
 * `:core:engine` (docs/04-ARCHITECTURE.md §2) - the same reasoning as
 * [SkillStateReplayer].
 */
data class ConfusionState(
    val skillId: SkillId,
    val recentPairs: List<Pair<String, String>> = emptyList(),
    val allTimeCounts: Map<Pair<String, String>, Int> = emptyMap(),
    val updatedAt: Instant = Instant.EPOCH,
)

/**
 * The pure confusion-tracking algorithm (`ConfusionTracker` in
 * `:core:engine`), injected into `:core:data`'s `ConfusionRepository` the
 * same way [SkillStateReplayer] is injected into `SkillStateRepository`:
 * `:core:data` depends on this interface only; `:core:engine` implements
 * it; the composition root wires the two together.
 */
interface ConfusionTracking {
    fun record(
        state: ConfusionState,
        target: String,
        response: String,
        now: Instant,
    ): ConfusionState

    fun toMatrix(state: ConfusionState): ConfusionMatrix
}
