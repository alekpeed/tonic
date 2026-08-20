package com.tonic.core.model.state

import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant

/** Where the M0 diagnostic places a user — docs/03-CURRICULUM.md §3 placement rules. */
enum class EntryPoint {
    /** `amusiaIndicatorFlag == true` or `pitchDirectionThresholdCents > 200`. */
    M1_REMEDIATION,

    /** Otherwise — `initialAxisLevels` derived per docs/07-ADAPTIVE-ENGINE.md §5. */
    M2_STAGE_1,
}

/**
 * The M0 diagnostic's output contract — docs/03-CURRICULUM.md §3 exactly.
 * [amusiaIndicatorFlag] is internal only: docs/02-PEDAGOGY.md §8 forbids
 * surfacing it as a diagnosis, and docs/05-DATA-MODEL.md §1 requires it be
 * reachable only through the placement repository method, never a DAO a
 * composable could call directly.
 */
data class DiagnosticResult(
    val pitchDirectionThresholdCents: Int,
    val discriminationDPrime: Double,
    val tonalMemorySpan: Int,
    val amusiaIndicatorFlag: Boolean,
    val recommendedEntry: EntryPoint,
    val initialAxisLevels: Map<DifficultyAxis, Int>,
    val completedAt: Instant,
    val seed: Long,
)
