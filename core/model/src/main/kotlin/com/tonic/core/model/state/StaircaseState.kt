package com.tonic.core.model.state

import kotlinx.serialization.Serializable

/** Which way the staircase last moved. Null before the first response. */
@Serializable
enum class Direction { UP, DOWN }

/**
 * The 2-down/1-up transformed staircase, per axis — docs/07-ADAPTIVE-ENGINE.md
 * §2. `reversals` holds the level at each direction flip; convergence is
 * declared after 6 of them, and the mean of the last 4 is the estimated
 * threshold.
 *
 * Persisted as `staircaseStateJson` (docs/05-DATA-MODEL.md §2) — carries
 * [schemaVersion] and must tolerate unknown fields on deserialization.
 */
@Serializable
data class StaircaseState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val level: Int,
    val consecutiveCorrect: Int = 0,
    val reversals: List<Int> = emptyList(),
    val stepSize: Int = INITIAL_STEP_SIZE,
    val lastDirection: Direction? = null,
) {
    val hasConverged: Boolean get() = reversals.size >= REVERSALS_TO_CONVERGE

    /** Mean of the last 4 reversal levels — null until [hasConverged]. */
    val estimatedThreshold: Double?
        get() = if (hasConverged) reversals.takeLast(4).average() else null

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val INITIAL_STEP_SIZE = 2
        const val MIN_STEP_SIZE = 1
        const val REVERSALS_TO_CONVERGE = 6
        const val STEP_HALVING_AFTER_REVERSAL = 2
    }
}
