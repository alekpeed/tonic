package com.tonic.core.engine.replay

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.fsrs.FsrsScheduler
import com.tonic.core.engine.mastery.MasteryEvaluator
import com.tonic.core.engine.scheduling.AxisScheduler
import com.tonic.core.engine.scheduling.AxisSchedulerState
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.ModuleId
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.state.FsrsGrade
import com.tonic.core.model.state.FsrsState
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.state.SkillStateReplayer
import java.time.Instant

/**
 * The canonical implementation of [SkillStateReplayer], and the sole place
 * that folds one attempt at a time into a [SkillState] update using the
 * real staircase/mastery/FSRS algorithms. `:core:data`'s
 * `SkillStateRepository.rebuildFromAttempts()` calls this over a skill's
 * entire history; the not-yet-built Stage 6 practice loop is expected to
 * call the same fold per attempt as it happens, live — the two are
 * structurally the same reduction run at different times, which is what
 * makes "rebuild reproduces incremental updates exactly"
 * (docs/09-BUILD-PLAN.md Stage 5) true by construction rather than by
 * coincidence.
 *
 * Scope: full replay (staircase + mastery + FSRS) applies only to M2 skill
 * nodes, the only ones [SkillGraph] knows about and the only ones with a
 * defined 5-criteria mastery lifecycle in this codebase — M0 is a one-time
 * diagnostic screening (docs/03-CURRICULUM.md §3) with no analogous
 * "mastered, now under FSRS review" state, and M1 isn't a separate
 * Phase-1-tracked skill at all (folded into M0's remediation path,
 * docs/01-PRODUCT-SPEC.md §3). For any non-M2 [SkillId] this falls back to
 * the only thing that's actually well-defined for it: attempt count, the
 * most recent axis-level snapshot, and the last-seen timestamp.
 */
object SkillStateReducer : SkillStateReplayer {
    /** "A review is a probe block of 10 items on that skill at its mastered axis levels" — docs/07-ADAPTIVE-ENGINE.md §6. */
    private const val REVIEW_BLOCK_SIZE = 10

    override fun replay(
        skillId: SkillId,
        attempts: List<Attempt>,
    ): SkillState {
        // Abandoned attempts never update any state - docs/07-ADAPTIVE-ENGINE.md §2's rule for the
        // staircase specifically, applied here to the whole reduction: an abandoned attempt was never
        // genuinely answered, so nothing it "shows" about the learner is real evidence.
        val real = attempts.filterNot { it.isAbandoned }
        if (real.isEmpty()) return SkillState.initial(skillId)

        val totalAttempts = real.size
        val updatedAt = real.last().timestamp

        if (skillId.moduleId != ModuleId.M2) {
            return SkillState(
                skillId = skillId,
                axisLevels = real.last().axisLevels,
                staircaseStates = emptyMap(),
                activeAxis = null,
                masteryState = MasteryState.IN_PROGRESS,
                masteredAt = null,
                fsrs = FsrsState(stability = 0.0, difficulty = 0.0, lastReview = null, due = null),
                totalAttempts = totalAttempts,
                updatedAt = updatedAt,
            )
        }

        val activeDegrees = SkillGraph.activeDegreesFor(skillId)
        var axisState = AxisSchedulerState()
        val masteryWindow = ArrayDeque<Attempt>()
        var masteryState = MasteryState.IN_PROGRESS
        var masteredAt: Instant? = null
        var fsrs = FsrsState(stability = 0.0, difficulty = 0.0, lastReview = null, due = null)
        var reviewBlock = mutableListOf<Attempt>()

        for (attempt in real) {
            if (masteryState == MasteryState.MASTERED) {
                // Post-mastery: every subsequent attempt is a review-block probe, not staircase input -
                // "only mastered nodes are scheduled for review; nodes in progress are being practiced
                // anyway" (docs/07-ADAPTIVE-ENGINE.md §6).
                reviewBlock.add(attempt)
                if (reviewBlock.size >= REVIEW_BLOCK_SIZE) {
                    val accuracy = reviewBlock.count { it.correct }.toDouble() / reviewBlock.size
                    val grade = FsrsGrade.fromBlockAccuracy(accuracy)
                    fsrs = FsrsScheduler.review(fsrs, grade, reviewBlock.last().timestamp)
                    reviewBlock = mutableListOf()
                }
                continue
            }

            // Warm-up attempts are recorded but excluded from mastery evaluation and the staircase -
            // docs/07-ADAPTIVE-ENGINE.md §8.
            if (attempt.isWarmup) continue

            axisState = AxisScheduler.update(axisState, attempt.correct)
            masteryWindow.addLast(attempt)
            if (masteryWindow.size > MasteryEvaluator.WINDOW_SIZE) masteryWindow.removeFirst()

            val verdict = MasteryEvaluator.evaluate(masteryWindow.toList(), activeDegrees, axisState.levels)
            if (verdict.isMastered) {
                masteryState = MasteryState.MASTERED
                masteredAt = attempt.timestamp
                // Reaching mastery doubles as the first FSRS review - a fresh block starts counting
                // from the next attempt.
                fsrs = FsrsScheduler.initial(FsrsGrade.GOOD, attempt.timestamp)
            }
        }

        return SkillState(
            skillId = skillId,
            axisLevels = axisState.levels,
            staircaseStates = axisState.staircases,
            activeAxis = axisState.activeAxis,
            masteryState = masteryState,
            masteredAt = masteredAt,
            fsrs = fsrs,
            totalAttempts = totalAttempts,
            updatedAt = updatedAt,
        )
    }
}
