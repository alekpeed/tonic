package com.tonic.core.engine.confusion

import com.tonic.core.model.state.ConfusionCell
import com.tonic.core.model.state.ConfusionMatrix
import com.tonic.core.model.state.ConfusionState
import com.tonic.core.model.state.ConfusionTracking
import com.tonic.core.model.time.Clock
import java.time.Instant
import kotlin.math.max

/**
 * The `(target, response)` confusion matrix and the remediation weights
 * derived from it — docs/07-ADAPTIVE-ENGINE.md §4. [ConfusionState.recentPairs]
 * is exactly the rolling window docs/05-DATA-MODEL.md §1's `windowCount`
 * column describes (last 100 attempts); `allTimeCounts` is that same
 * table's plain `count` column.
 */
object ConfusionTracker : ConfusionTracking {
    const val WINDOW_SIZE = 100
    const val WEAK_DEGREE_THRESHOLD = 0.80
    const val CONFUSION_PAIR_THRESHOLD = 0.10

    fun record(
        state: ConfusionState,
        target: String,
        response: String,
        clock: Clock,
    ): ConfusionState {
        val pair = target to response
        return state.copy(
            recentPairs = (state.recentPairs + pair).takeLast(WINDOW_SIZE),
            allTimeCounts = state.allTimeCounts + (pair to (state.allTimeCounts.getOrDefault(pair, 0) + 1)),
            updatedAt = clock.now(),
        )
    }

    /**
     * [ConfusionTracking]'s entry point — `:core:data`'s `ConfusionRepository` is injected with this
     * object through that interface (docs/04-ARCHITECTURE.md §2 forbids it depending on `:core:engine`
     * directly; see [com.tonic.core.model.state.SkillStateReplayer] for the same pattern applied to
     * skill-state rebuilding). Delegates to [record] with a one-shot [Clock].
     */
    override fun record(
        state: ConfusionState,
        target: String,
        response: String,
        now: Instant,
    ): ConfusionState = record(state, target, response, Clock { now })

    override fun toMatrix(state: ConfusionState): ConfusionMatrix {
        val windowCounts = state.recentPairs.groupingBy { it }.eachCount()
        val allPairs = (windowCounts.keys + state.allTimeCounts.keys).distinct()
        val cells =
            allPairs.map { pair ->
                ConfusionCell(
                    target = pair.first,
                    response = pair.second,
                    count = state.allTimeCounts.getOrDefault(pair, 0),
                    windowCount = windowCounts.getOrDefault(pair, 0),
                    updatedAt = state.updatedAt,
                )
            }
        return ConfusionMatrix(state.skillId, cells)
    }

    /** Degrees (or any target label) whose window accuracy is below 80% — docs/07-ADAPTIVE-ENGINE.md §4. */
    fun weakTargets(
        matrix: ConfusionMatrix,
        activeTargets: List<String>,
    ): Set<String> =
        activeTargets
            .filter { target ->
                val accuracy = matrix.accuracyFor(target)
                accuracy != null && accuracy < WEAK_DEGREE_THRESHOLD
            }.toSet()

    /** `(target -> response)` pairs exceeding 10% of that target's window attempts. */
    fun confusionPairs(matrix: ConfusionMatrix): List<ConfusionCell> =
        matrix.confusionPairsAbove(CONFUSION_PAIR_THRESHOLD)

    /**
     * Item-generator weights derived from weakness — docs/07-ADAPTIVE-ENGINE.md
     * §4:
     * ```
     * weight(d) *= 1 + 2.0 * max(0, 0.80 - accuracy(d)) / 0.80     // up to 3x
     * cap: no degree exceeds 2.5x the lowest weight
     * ```
     * The cap matters: unbounded oversampling turns a session into a
     * single-degree grind, which is both demoralizing and pedagogically
     * wrong — discrimination requires the weak degree to co-occur with
     * others, not appear in isolation.
     */
    fun remediationWeights(
        matrix: ConfusionMatrix,
        activeTargets: List<String>,
    ): Map<String, Double> {
        val rawWeights =
            activeTargets.associateWith { target ->
                val accuracy = matrix.accuracyFor(target) ?: 1.0 // no data yet - treat as not-weak, don't inflate
                1.0 + REMEDIATION_FACTOR * max(0.0, WEAK_DEGREE_THRESHOLD - accuracy) / WEAK_DEGREE_THRESHOLD
            }
        val lowest = rawWeights.values.minOrNull() ?: return rawWeights
        val cap = lowest * MAX_WEIGHT_MULTIPLE
        return rawWeights.mapValues { (_, w) -> w.coerceAtMost(cap) }
    }

    private const val REMEDIATION_FACTOR = 2.0
    private const val MAX_WEIGHT_MULTIPLE = 2.5
}
