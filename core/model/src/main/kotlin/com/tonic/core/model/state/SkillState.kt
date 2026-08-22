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
) {
    companion object {
        /**
         * The state of a skill node before any attempt has ever been recorded
         * for it. `:core:data` has no access to `:core:curriculum`'s
         * prerequisite graph (docs/04-ARCHITECTURE.md §2), so it cannot itself
         * decide LOCKED vs. AVAILABLE - [MasteryState.LOCKED] is the
         * conservative default; a caller that does have prerequisite
         * information (a `:feature:*` ViewModel) is expected to call
         * `SkillStateRepository.update()` with the correct status once it
         * determines the node is actually unlocked.
         */
        fun initial(
            skillId: SkillId,
            /**
             * Which axis family this node uses. Defaults to recognition, which is every Phase 1 node
             * and every Phase 2 node except `M12.*` — so the default reproduces Phase 1's map exactly.
             * A prediction node passes [DifficultyAxis.Scope.PREDICTION] rather than carrying six
             * recognition axes it will never move (docs/20-PHASE-2-SPEC.md §4, change 3).
             */
            scope: DifficultyAxis.Scope = DifficultyAxis.Scope.RECOGNITION,
        ): SkillState =
            SkillState(
                skillId = skillId,
                axisLevels = DifficultyAxis.axesFor(scope).associateWith { 0 },
                staircaseStates = emptyMap(),
                activeAxis = null,
                masteryState = MasteryState.LOCKED,
                masteredAt = null,
                fsrs = FsrsState(stability = 0.0, difficulty = 0.0, lastReview = null, due = null),
                totalAttempts = 0,
                updatedAt = Instant.EPOCH,
            )
    }
}
