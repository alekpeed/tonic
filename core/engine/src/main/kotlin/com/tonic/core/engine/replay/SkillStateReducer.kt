package com.tonic.core.engine.replay

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.fsrs.FsrsScheduler
import com.tonic.core.engine.mastery.BinaryMasteryEvaluator
import com.tonic.core.engine.mastery.IndependenceCheck
import com.tonic.core.engine.mastery.MasteryEvaluator
import com.tonic.core.engine.mastery.PredictionMasteryEvaluator
import com.tonic.core.engine.scheduling.AxisScheduler
import com.tonic.core.engine.scheduling.AxisSchedulerState
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.DifficultyAxis
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
 * Scope: full replay (staircase + mastery + FSRS) applies to the
 * recognition nodes [SkillGraph] knows about — the only skills with a
 * defined mastery lifecycle in this codebase. M0 is a one-time diagnostic
 * screening (docs/03-CURRICULUM.md §3) with no analogous "mastered, now
 * under FSRS review" state, and M1 isn't a separate Phase-1-tracked skill
 * at all (folded into M0's remediation path, docs/01-PRODUCT-SPEC.md §3).
 * For any other [SkillId] this falls back to the only thing that's actually
 * well-defined for it: attempt count, the most recent axis-level snapshot,
 * and the last-seen timestamp.
 *
 * The test used to be `moduleId != M2`, which was correct only while M2 was
 * the only recognition module. Left alone it would have silently denied
 * every M10 and M11 node a staircase, a mastery verdict and an FSRS
 * schedule — the nodes would have accumulated attempts forever and never
 * been certified, and docs/20-PHASE-2-SPEC.md §3's per-degree criterion
 * would have existed without ever being consulted.
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
        val chronological = attempts.filterNot { it.isAbandoned }
        // Independence-check probes are excluded from the *ordinary* mastery window and FSRS
        // review-block accumulation - docs/03-CURRICULUM.md §5.6 calls M2.INDEPENDENCE_CHECK "a
        // separate, non-blocking assessment," run at a forced CADENCE_FADE=6 rather than the node's
        // normal mastered axis levels, so folding its 30 items in there would corrupt both with data
        // that was never meant to represent them. They are NOT excluded from axis-level replay below:
        // a failed check lowers CADENCE_FADE ("failure is not punitive: it lowers the fade axis" -
        // docs/03-CURRICULUM.md §5.6), and that adjustment has to be re-derivable purely from the
        // attempt log the same way every other axis move is, or a later ordinary review attempt
        // (which calls this same replay from scratch) would recompute axisLevels without any memory
        // of the check ever having failed and silently undo it.
        val real = chronological.filterNot { it.isIndependenceCheckProbe }
        if (real.isEmpty()) return SkillState.initial(skillId)

        val totalAttempts = real.size
        val updatedAt = chronological.last().timestamp

        if (SkillGraph.isKnownNode(skillId)) {
            when (SkillGraph.scopeFor(skillId)) {
                DifficultyAxis.Scope.PREDICTION ->
                    return replayPrediction(skillId, real, chronological, totalAttempts, updatedAt)

                // M9. Routing a learner to a node with no mastery path would strand them there
                // forever - and since M9 gates all of minor, that would be worse than the
                // unreachability it replaced. See replayModeId.
                DifficultyAxis.Scope.MODE_ID ->
                    return replayModeId(skillId, chronological, totalAttempts, updatedAt)

                // Unreachable until Stage 4.4 registers an M3 node. A loud failure rather than a
                // fallthrough to the recognition path: rhythm's mastery criteria are not the five
                // recognition ones (docs/40-PHASE-4-SPEC.md §5.3 replaces them for production nodes),
                // so replaying a rhythm node as if it were an M2 node would reconstruct a state that
                // never existed - and this reducer is what the whole attempt log is re-derived through.
                DifficultyAxis.Scope.RHYTHM ->
                    error("Replay for rhythm nodes is not built - docs/40-PHASE-4-SPEC.md stage 4.4")

                DifficultyAxis.Scope.RECOGNITION -> Unit
            }
        }

        if (!SkillGraph.isRecognitionNode(skillId)) {
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
        var independenceCheckBlock = mutableListOf<Attempt>()

        for (attempt in chronological) {
            if (attempt.isIndependenceCheckProbe) {
                // Only meaningful once mastered - the check can only run against a mastered node - but
                // still walked chronologically rather than pre-filtered so a block only resolves once
                // all REQUIRED_ITEMS of it have actually been seen in order.
                if (masteryState != MasteryState.MASTERED) continue
                independenceCheckBlock.add(attempt)
                if (independenceCheckBlock.size >= IndependenceCheck.REQUIRED_ITEMS) {
                    val result = IndependenceCheck.evaluate(independenceCheckBlock.toList())
                    if (!result.passed) {
                        axisState = axisState.copy(levels = IndependenceCheck.applyFailure(axisState.levels))
                    }
                    independenceCheckBlock = mutableListOf()
                }
                continue
            }

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

            val verdict =
                MasteryEvaluator.evaluate(
                    masteryWindow.toList(),
                    activeDegrees,
                    axisState.levels,
                    // docs/20-PHASE-2-SPEC.md §3's sixth criterion. Null for every node that introduces
                    // nothing, which is all of M2 and M10 - so this changes no Phase 1 outcome.
                    focusDegree = SkillGraph.focusDegreeFor(skillId),
                )
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

    /**
     * The `M9` reduction — docs/20-PHASE-2-SPEC.md §3.
     *
     * The simplest of the three: no staircase, because the module has no axes (its three nodes *are*
     * its progression), and [BinaryMasteryEvaluator]'s two criteria rather than the six that are about
     * scale degrees. FSRS still applies — a mastered mode-identification node is reviewed like any
     * other.
     *
     * `MINOR` is named as the signal condition, arbitrarily: d-prime measures the separation between
     * hit and false-alarm rates, so swapping signal for noise flips the sign of both z-scores and
     * leaves their difference unchanged.
     */
    private fun replayModeId(
        skillId: SkillId,
        chronological: List<Attempt>,
        totalAttempts: Int,
        updatedAt: Instant,
    ): SkillState {
        val masteryWindow = ArrayDeque<Attempt>()
        var masteryState = MasteryState.IN_PROGRESS
        var masteredAt: Instant? = null
        var fsrs = FsrsState(stability = 0.0, difficulty = 0.0, lastReview = null, due = null)
        var reviewBlock = mutableListOf<Attempt>()

        for (attempt in chronological) {
            if (masteryState == MasteryState.MASTERED) {
                reviewBlock.add(attempt)
                if (reviewBlock.size >= REVIEW_BLOCK_SIZE) {
                    val accuracy = reviewBlock.count { it.correct }.toDouble() / reviewBlock.size
                    fsrs =
                        FsrsScheduler.review(fsrs, FsrsGrade.fromBlockAccuracy(accuracy), reviewBlock.last().timestamp)
                    reviewBlock = mutableListOf()
                }
                continue
            }
            if (attempt.isWarmup) continue

            masteryWindow.addLast(attempt)
            if (masteryWindow.size > BinaryMasteryEvaluator.WINDOW_SIZE) masteryWindow.removeFirst()

            val verdict =
                BinaryMasteryEvaluator.evaluate(
                    masteryWindow.toList(),
                    signalLabel = AnswerAlphabet.MajorMinor.MINOR,
                )
            if (verdict.isMastered) {
                masteryState = MasteryState.MASTERED
                masteredAt = attempt.timestamp
                fsrs = FsrsScheduler.initial(FsrsGrade.GOOD, attempt.timestamp)
            }
        }

        return SkillState(
            skillId = skillId,
            // No axes at all, deliberately - not an empty map standing in for unknown levels.
            axisLevels = emptyMap(),
            staircaseStates = emptyMap(),
            activeAxis = null,
            masteryState = masteryState,
            masteredAt = masteredAt,
            fsrs = fsrs,
            totalAttempts = totalAttempts,
            updatedAt = updatedAt,
        )
    }

    /**
     * The `M12` reduction — docs/20-PHASE-2-SPEC.md §3/§4.
     *
     * Structurally the same fold as the recognition path and deliberately not shared with it: the
     * staircase runs over the *prediction* axes, and mastery is
     * [PredictionMasteryEvaluator]'s four criteria rather than [MasteryEvaluator]'s six. Four of those
     * six are about scale degrees, which a three-button match judgment does not have; forcing one
     * object to serve both would have meant weakening criteria `M2` depends on, which
     * docs/20-PHASE-2-SPEC.md §4 forbids ("the mastery evaluator's five criteria: unchanged").
     *
     * There is no independence check here. That check asks whether a learner can hold a key without
     * the cadence propping it up, and every `M12` item plays the full cadence by design — the question
     * it answers is already answered by the `M2` chain, which is `M12.PREDICT_TRIAD`'s prerequisite.
     */
    private fun replayPrediction(
        skillId: SkillId,
        real: List<Attempt>,
        chronological: List<Attempt>,
        totalAttempts: Int,
        updatedAt: Instant,
    ): SkillState {
        var axisState = AxisSchedulerState.forScope(DifficultyAxis.Scope.PREDICTION)
        val masteryWindow = ArrayDeque<Attempt>()
        var masteryState = MasteryState.IN_PROGRESS
        var masteredAt: Instant? = null
        var fsrs = FsrsState(stability = 0.0, difficulty = 0.0, lastReview = null, due = null)
        var reviewBlock = mutableListOf<Attempt>()

        for (attempt in chronological) {
            if (masteryState == MasteryState.MASTERED) {
                reviewBlock.add(attempt)
                if (reviewBlock.size >= REVIEW_BLOCK_SIZE) {
                    val accuracy = reviewBlock.count { it.correct }.toDouble() / reviewBlock.size
                    fsrs =
                        FsrsScheduler.review(fsrs, FsrsGrade.fromBlockAccuracy(accuracy), reviewBlock.last().timestamp)
                    reviewBlock = mutableListOf()
                }
                continue
            }
            if (attempt.isWarmup) continue

            axisState = AxisScheduler.update(axisState, attempt.correct)
            masteryWindow.addLast(attempt)
            if (masteryWindow.size > PredictionMasteryEvaluator.WINDOW_SIZE) masteryWindow.removeFirst()

            if (PredictionMasteryEvaluator.evaluate(masteryWindow.toList(), axisState.levels).isMastered) {
                masteryState = MasteryState.MASTERED
                masteredAt = attempt.timestamp
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
