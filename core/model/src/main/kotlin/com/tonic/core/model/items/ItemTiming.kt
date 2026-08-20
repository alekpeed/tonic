package com.tonic.core.model.items

/**
 * Durations for one item, driven by the `TEMPO_DENSITY` axis
 * (docs/03-CURRICULUM.md §5.3: faster/shorter is harder). There is
 * deliberately no response deadline — docs/02-PEDAGOGY.md §6: no timer
 * pressure in Phase 1. Latency is recorded on the [com.tonic.core.model.attempts.Attempt]
 * itself, not enforced here.
 */
data class ItemTiming(
    val referenceDurationMs: Long,
    val gapAfterReferenceMs: Long,
    val targetDurationMs: Long,
)
