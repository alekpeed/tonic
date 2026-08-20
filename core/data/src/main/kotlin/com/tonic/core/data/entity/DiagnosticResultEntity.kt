package com.tonic.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One completed M0 diagnostic run — docs/05-DATA-MODEL.md §1. History is
 * kept (never overwritten): "re-screening after the M1 path needs the
 * prior result for comparison."
 *
 * [amusiaIndicatorFlag] is carried through to the domain
 * [com.tonic.core.model.state.DiagnosticResult] as documented, but must
 * never reach user-facing copy — docs/02-PEDAGOGY.md §8 / CLAUDE.md §9.
 * That's a `:feature:diagnostic` (Stage 8) concern, enforced there by an
 * automated string-resource audit; `:core:data` only stores and returns
 * the documented domain type faithfully.
 */
@Entity(tableName = "diagnostic_results")
internal data class DiagnosticResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pitchDirectionThresholdCents: Int,
    val discriminationDPrime: Double,
    val tonalMemorySpan: Int,
    val amusiaIndicatorFlag: Boolean,
    val recommendedEntry: String,
    val initialAxisLevelsJson: String,
    val seed: Long,
    val completedAt: Long,
)
