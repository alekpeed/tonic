package com.tonic.core.model.state

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant

/**
 * The full materialized state of one skill node — the domain type behind
 * `SkillStateRepository.observe()` (docs/05-DATA-MODEL.md §5). Rebuildable
 * from the attempt log; `:core:data` must provide and test a
 * `rebuildFromAttempts()` that reproduces this exactly (docs/09-BUILD-PLAN.md
 * Stage 5).
 *
 * Not itself `@Serializable` — it's an aggregate of plain columns plus two
 * JSON-encoded pieces ([axisLevels], [staircaseStates]) that are each
 * independently serializable; see docs/05-DATA-MODEL.md §1/§2.
 */
data class SkillState(
    val skillId: SkillId,
    val axisLevels: Map<DifficultyAxis, Int>,
    val staircaseStates: Map<DifficultyAxis, StaircaseState>,
    val activeAxis: DifficultyAxis?,
    val masteryState: MasteryState,
    val masteredAt: Instant?,
    val fsrs: FsrsState,
    val totalAttempts: Int,
    val updatedAt: Instant,
)
