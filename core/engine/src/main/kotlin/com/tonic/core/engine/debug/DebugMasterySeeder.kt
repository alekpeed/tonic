package com.tonic.core.engine.debug

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.fsrs.FsrsScheduler
import com.tonic.core.engine.mastery.BinaryMasteryEvaluator
import com.tonic.core.engine.mastery.MasteryEvaluator
import com.tonic.core.engine.mastery.PredictionMasteryEvaluator
import com.tonic.core.engine.scheduling.AxisScheduler
import com.tonic.core.engine.scheduling.AxisSchedulerState
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.state.FsrsGrade
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.SkillState
import java.time.Instant

/**
 * Synthetic attempt histories that mastery-clear one node, for `:feature:settings`'s debug-only
 * "jump to node" tool (BuildConfig.DEBUG only — never compiled into a release build).
 *
 * A tester manually exercising Phase 2 content is bottlenecked behind the same two things that make
 * [MasteryEvaluator] correct for a learner: a rolling 30-item window that has to be *earned*, and
 * `CADENCE_FADE`'s deliberately one-rung-at-a-time climb (docs/07-ADAPTIVE-ENGINE.md §2a). Both are
 * right for a learner and far too slow for someone who needs to reach M9, minor, M11 and M12 in one
 * evening rather than grind toward them for real.
 *
 * This does not bypass mastery — it generates a fully-correct, plausible attempt log and hands it to
 * the same [com.tonic.core.data.repository.AttemptRepository]/`rebuildFromAttempts` path a real
 * session uses, so the resulting [com.tonic.core.model.state.SkillState] is produced by the exact
 * same reducer, evaluator and axis scheduler as genuine play. Only the *source* of the attempts is
 * synthetic; nothing about how they are scored is.
 *
 * Every answer is correct, so no seed is needed to satisfy CLAUDE.md §5's determinism rule: the same
 * (skillId, sessionId, startAt) always produces the same attempt list.
 */
public object DebugMasterySeeder {
    /** Generous upper bound so a criterion that can never be satisfied fails loudly instead of hanging. */
    private const val MAX_SYNTHETIC_ATTEMPTS = 500

    /** Purely for a plausible, strictly increasing [Attempt.timestamp] — never read by any evaluator. */
    private const val ATTEMPT_SPACING_SECONDS = 6L

    /**
     * A mastered [SkillState] for [skillId], written straight to the repository — **what the debug jump
     * tool actually uses.**
     *
     * [attemptsToMaster] below fabricates a full attempt log and replays it through the real evaluator,
     * which is the more principled construction and was the tool's first implementation. It was the
     * wrong call for a debug affordance: reaching the end of the chain meant 655 attempts across 25
     * nodes, seconds of SQLite writes on a phone, and a Room invalidation storm behind every one of
     * them — a button that looks dead while it works, and a large surface for things to go wrong on a
     * device nobody in this loop can attach a debugger to. Stamping ~25 rows is instant and has almost
     * nothing in it to fail.
     *
     * The axis levels are set to what mastery actually requires rather than to the maximum, so a
     * jumped-to node behaves like one genuinely just cleared: `CADENCE_FADE` at
     * [CadenceFadeLevel.MASTERY_MINIMUM] for a recognition node, `PREDICT_GAP` at
     * [PredictionMasteryEvaluator.MIN_GAP_LEVEL] for a prediction one. `M9` has no axes at all.
     */
    public fun masteredStateFor(
        skillId: SkillId,
        now: Instant,
    ): SkillState {
        val scope = SkillGraph.scopeFor(skillId)
        val axisLevels =
            when (scope) {
                DifficultyAxis.Scope.RECOGNITION ->
                    DifficultyAxis.RECOGNITION_AXES.associateWith { axis ->
                        if (axis == DifficultyAxis.CADENCE_FADE) CadenceFadeLevel.MASTERY_MINIMUM.level else 0
                    }

                DifficultyAxis.Scope.PREDICTION ->
                    DifficultyAxis.PREDICTION_AXES.associateWith { axis ->
                        if (axis == DifficultyAxis.PREDICT_GAP) PredictionMasteryEvaluator.MIN_GAP_LEVEL else 0
                    }

                // The rhythm analog, and well-defined without any of Stage 4.3's scoring:
                // docs/40-PHASE-4-SPEC.md §5.3 criterion 4 makes METRONOME_FADE >= 4 a mastery
                // requirement for exactly the reason CADENCE_FADE >= 4 is one - without it a learner
                // "masters" rhythm having never kept time unaided.
                DifficultyAxis.Scope.RHYTHM ->
                    DifficultyAxis.RHYTHM_AXES.associateWith { axis ->
                        if (axis == DifficultyAxis.METRONOME_FADE) MetronomeFadeLevel.MASTERY_MINIMUM.level else 0
                    }

                // Deliberately empty, matching SkillStateReducer.replayModeId - not an empty map
                // standing in for unknown levels.
                DifficultyAxis.Scope.MODE_ID -> emptyMap()
            }

        return SkillState(
            skillId = skillId,
            axisLevels = axisLevels,
            staircaseStates = emptyMap(),
            activeAxis = null,
            masteryState = MasteryState.MASTERED,
            masteredAt = now,
            // Mastery doubles as the first FSRS review everywhere else in this codebase; matching that
            // keeps a jumped-to node from looking permanently overdue on the progress screen.
            fsrs = FsrsScheduler.initial(FsrsGrade.GOOD, now),
            totalAttempts = 0,
            updatedAt = now,
        )
    }

    /**
     * A fully-correct attempt log that mastery-clears [skillId] when replayed, for a synthetic
     * [sessionId] starting at [startAt].
     *
     * No longer used by the jump tool (see [masteredStateFor]). Kept because
     * `DebugMasterySeederTest` drives it through the real [MasteryEvaluator] for every node in the
     * graph, which is a genuine curriculum invariant — "every node can actually be mastered by playing
     * it" — and is what caught `M11.CHROM_FLAT6` being mathematically unmasterable.
     */
    public fun attemptsToMaster(
        skillId: SkillId,
        sessionId: Long,
        startAt: Instant,
    ): List<Attempt> =
        when (SkillGraph.scopeFor(skillId)) {
            DifficultyAxis.Scope.RECOGNITION -> recognitionAttempts(skillId, sessionId, startAt)
            DifficultyAxis.Scope.MODE_ID -> modeIdAttempts(skillId, sessionId, startAt)
            DifficultyAxis.Scope.PREDICTION -> predictionAttempts(skillId, sessionId, startAt)

            // Unreachable today: no M3 node is registered in SkillGraph until Stage 4.4, so
            // scopeFor never returns this. Left as a loud failure rather than an empty list because
            // synthesizing a mastering run needs the rhythm Attempt fields and the production mastery
            // criteria, both of which are Stage 4.3's. An empty list would silently seed a rhythm node
            // as mastered on no evidence, which is the one thing a debug seeder must not do.
            DifficultyAxis.Scope.RHYTHM ->
                error("Debug seeding for rhythm nodes is not built - docs/40-PHASE-4-SPEC.md stage 4.3")
        }

    /**
     * `M2`/`M10`/`M11` nodes, cycled through the active degree set by [FairDegreeCycle] — see that
     * class's own doc for why a plain round-robin is not quite enough for an `M11` node's introduced
     * degree.
     */
    private fun recognitionAttempts(
        skillId: SkillId,
        sessionId: Long,
        startAt: Instant,
    ): List<Attempt> {
        val activeDegrees = SkillGraph.activeDegreesFor(skillId).toList()
        require(activeDegrees.isNotEmpty()) { "${skillId.raw} has no active degrees to seed" }
        val focusDegree = SkillGraph.focusDegreeFor(skillId)
        val cycle = FairDegreeCycle(activeDegrees, focusDegree)

        var axisState = AxisSchedulerState()
        val attempts = mutableListOf<Attempt>()
        val window = ArrayDeque<Attempt>()

        var index = 0
        while (attempts.size < MAX_SYNTHETIC_ATTEMPTS) {
            val degree = cycle.next()
            val levelsAtGeneration = axisState.levels
            val attempt =
                syntheticAttempt(
                    skillId = skillId,
                    sessionId = sessionId,
                    index = index,
                    startAt = startAt,
                    axisLevels = levelsAtGeneration,
                    cadenceFadeLevel = levelsAtGeneration[DifficultyAxis.CADENCE_FADE] ?: 0,
                    label = degree.canonicalLabel,
                )
            attempts += attempt
            window.addLast(attempt)
            if (window.size > MasteryEvaluator.WINDOW_SIZE) window.removeFirst()
            axisState = AxisScheduler.update(axisState, correct = true)

            val verdict =
                MasteryEvaluator.evaluate(
                    window.toList(),
                    SkillGraph.activeDegreesFor(skillId),
                    axisState.levels,
                    focusDegree,
                )
            if (verdict.isMastered) return attempts
            if (attempts.size == MAX_SYNTHETIC_ATTEMPTS) {
                error(
                    "${skillId.raw} did not master within $MAX_SYNTHETIC_ATTEMPTS synthetic attempts; " +
                        "still blocked on ${verdict.blockingCriterion}",
                )
            }
            index++
        }
        error("${skillId.raw} produced no attempts at all")
    }

    /** `M9`'s three nodes — no axes, d-prime over a balanced MAJOR/MINOR alternation. */
    private fun modeIdAttempts(
        skillId: SkillId,
        sessionId: Long,
        startAt: Instant,
    ): List<Attempt> {
        val labels = AnswerAlphabet.MajorMinor.labels
        val attempts = mutableListOf<Attempt>()
        val window = ArrayDeque<Attempt>()

        var index = 0
        while (attempts.size < MAX_SYNTHETIC_ATTEMPTS) {
            val label = labels[index % labels.size]
            val attempt =
                syntheticAttempt(
                    skillId = skillId,
                    sessionId = sessionId,
                    index = index,
                    startAt = startAt,
                    axisLevels = emptyMap(),
                    cadenceFadeLevel = 0,
                    label = label,
                )
            attempts += attempt
            window.addLast(attempt)
            if (window.size > BinaryMasteryEvaluator.WINDOW_SIZE) window.removeFirst()

            val verdict =
                BinaryMasteryEvaluator.evaluate(window.toList(), signalLabel = AnswerAlphabet.MajorMinor.MINOR)
            if (verdict.isMastered) return attempts
            index++
        }
        error("${skillId.raw} did not master within $MAX_SYNTHETIC_ATTEMPTS synthetic attempts")
    }

    /**
     * `M12`'s prediction nodes — a MATCHED/mismatch alternation so d-prime sees both signal and noise
     * trials, over the prediction axes so `PREDICT_GAP` climbs to [PredictionMasteryEvaluator.MIN_GAP_LEVEL]
     * the same way `CADENCE_FADE` climbs for a recognition node.
     */
    private fun predictionAttempts(
        skillId: SkillId,
        sessionId: Long,
        startAt: Instant,
    ): List<Attempt> {
        val labels = listOf(AnswerAlphabet.MatchDirection.MATCHED, AnswerAlphabet.MatchDirection.TOO_LOW)
        var axisState = AxisSchedulerState.forScope(DifficultyAxis.Scope.PREDICTION)
        val attempts = mutableListOf<Attempt>()
        val window = ArrayDeque<Attempt>()

        var index = 0
        while (attempts.size < MAX_SYNTHETIC_ATTEMPTS) {
            val label = labels[index % labels.size]
            val levelsAtGeneration = axisState.levels
            val attempt =
                syntheticAttempt(
                    skillId = skillId,
                    sessionId = sessionId,
                    index = index,
                    startAt = startAt,
                    axisLevels = levelsAtGeneration,
                    cadenceFadeLevel = 0,
                    label = label,
                )
            attempts += attempt
            window.addLast(attempt)
            if (window.size > PredictionMasteryEvaluator.WINDOW_SIZE) window.removeFirst()
            axisState = AxisScheduler.update(axisState, correct = true)

            val verdict = PredictionMasteryEvaluator.evaluate(window.toList(), axisState.levels)
            if (verdict.isMastered) return attempts
            index++
        }
        error("${skillId.raw} did not master within $MAX_SYNTHETIC_ATTEMPTS synthetic attempts")
    }

    private fun syntheticAttempt(
        skillId: SkillId,
        sessionId: Long,
        index: Int,
        startAt: Instant,
        axisLevels: Map<DifficultyAxis, Int>,
        cadenceFadeLevel: Int,
        label: String,
    ): Attempt =
        Attempt(
            skillId = skillId,
            sessionId = sessionId,
            itemSeed = index.toLong(),
            axisLevels = axisLevels,
            targetLabel = label,
            responseLabel = label,
            correct = true,
            latencyMs = DEBUG_LATENCY_MS,
            replayCount = 0,
            keyPitchClass = 0,
            targetMidi = DEBUG_TARGET_MIDI,
            timbreId = "debug_seed",
            cadenceFadeLevel = cadenceFadeLevel,
            timestamp = startAt.plusSeconds(index * ATTEMPT_SPACING_SECONDS),
        )

    private const val DEBUG_LATENCY_MS = 800L
    private const val DEBUG_TARGET_MIDI = 60
}

/**
 * A deterministic degree sequence in which, over any window aligned to a multiple of the cycle's own
 * period, every degree appears exactly its target count — smooth weighted round-robin (the interleaving
 * load balancers use to avoid bursts of one backend), applied here so a degree's occurrences spread
 * evenly across the sequence instead of clustering.
 *
 * With no [focusDegree], every degree gets weight 1 and this is a plain round-robin: exactly what
 * [MasteryEvaluator]'s `DEGREE_COVERAGE` criterion needs, since every active degree gets an identical
 * share of any full window.
 *
 * With a [focusDegree] (every `M11` node), plain round-robin under-serves it: `FOCUS_DEGREE` asks for
 * more attempts on the introduced degree than an equal share of eight-or-more active degrees gives it
 * in a 30-item window. [otherTarget]/[focusTarget] mirror [MasteryEvaluator]'s own
 * `requiredAttemptsPerDegree`/`requiredFocusAttempts` formulas exactly, weight per degree by its own
 * target count, and are what turned up the one active-degree count (ten, `M11.CHROM_FLAT6`) where the
 * two formulas asked for more attempts than a 30-item window holds - fixed on the evaluator side, not
 * papered over here; see [MasteryEvaluator]'s KDoc on `requiredFocusAttempts`. Duplicated rather than
 * shared because the evaluator derives them from a live attempt window, not from the degree set alone -
 * [com.tonic.core.engine.debug.DebugMasterySeederTest] drives every node through the real evaluator, so
 * a drift between the two copies fails loudly there rather than silently here.
 */
private class FairDegreeCycle(
    activeDegrees: List<ScaleDegree>,
    focusDegree: ScaleDegree?,
) {
    private val weights: Map<ScaleDegree, Int>
    private val credit: MutableMap<ScaleDegree, Int> = activeDegrees.associateWith { 0 }.toMutableMap()
    private val totalWeight: Int

    init {
        weights =
            if (focusDegree == null) {
                activeDegrees.associateWith { 1 }
            } else {
                val n = activeDegrees.size
                val otherTarget = minOf(MasteryEvaluator.MIN_ATTEMPTS_PER_DEGREE, MasteryEvaluator.WINDOW_SIZE / n)
                // The budget left for the focus degree once every other active degree has taken its own
                // coverage floor - the bound that fixed CHROM_FLAT6 on the evaluator side, mirrored here.
                val budget = MasteryEvaluator.WINDOW_SIZE - (n - 1) * otherTarget
                val focusTarget =
                    minOf(
                        MasteryEvaluator.MIN_FOCUS_DEGREE_ATTEMPTS,
                        // MasteryEvaluator.BALANCE_MAX_FREQUENCY_MULTIPLE is private; 1.5 is its value,
                        // stable since docs/03-CURRICULUM.md §5.4 defined the balance rule.
                        (MasteryEvaluator.WINDOW_SIZE * 1.5 / n).toInt(),
                        budget,
                    ).coerceAtLeast(1)
                activeDegrees.associateWith { if (it == focusDegree) focusTarget else otherTarget }
            }
        totalWeight = weights.values.sum()
    }

    fun next(): ScaleDegree {
        for ((degree, weight) in weights) credit[degree] = credit.getValue(degree) + weight
        val picked = credit.entries.maxBy { it.value }.key
        credit[picked] = credit.getValue(picked) - totalWeight
        return picked
    }
}
