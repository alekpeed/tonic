package com.tonic.core.engine.simulation

import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.confusion.ConfusionState
import com.tonic.core.engine.confusion.ConfusionTracker
import com.tonic.core.engine.mastery.MasteryEvaluator
import com.tonic.core.engine.scheduling.AxisScheduler
import com.tonic.core.engine.scheduling.AxisSchedulerState
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.MasteryVerdict
import com.tonic.core.model.time.Clock
import java.time.Instant
import kotlin.random.Random

/**
 * docs/10-TESTING.md §5: "Test the adaptive engine against simulated
 * learners with known properties. This is how you find out whether the
 * engine actually works before a human uses it." Wires the real
 * generator, axis scheduler, confusion tracker, and mastery evaluator
 * together — the same pieces the practice loop will use in Stage 6 — and
 * runs a configurable [SimulatedResponder] through it for [itemCount] items.
 */
object SimulationHarness {
    fun run(
        skill: SkillId,
        itemCount: Int,
        responder: SimulatedResponder,
        seedBase: Long = 1L,
    ): SimulationResult {
        val activeDegrees = SkillGraph.activeDegreesFor(skill).sortedBy { it.degree }
        var axisState = AxisSchedulerState()
        var genHistory = GenerationHistory()
        var confusionState = ConfusionState(skill)
        val allAttempts = mutableListOf<Attempt>()
        val masteryTimeline = mutableListOf<Boolean>()
        val clock = Clock { Instant.EPOCH.plusSeconds(allAttempts.size.toLong()) }
        val rng = Random(seedBase)

        repeat(itemCount) { i ->
            val axisLevels = axisState.levels
            val result = M2ItemGenerator.generate(skill, axisLevels, seed = seedBase + i * 7919L, history = genHistory)
            genHistory = result.updatedHistory
            val item = result.item

            val correct = responder.answer(axisLevels, item.targetDegree, rng)
            val targetLabel = item.targetDegree.degree.toString()
            val responseLabel =
                if (correct) {
                    targetLabel
                } else {
                    responder.wrongAnswer(item.targetDegree, activeDegrees, rng).degree.toString()
                }

            val attempt =
                Attempt(
                    skillId = skill,
                    sessionId = 1L,
                    itemSeed = item.seed,
                    axisLevels = axisLevels,
                    targetLabel = targetLabel,
                    responseLabel = responseLabel,
                    correct = correct,
                    latencyMs = 600,
                    replayCount = 0,
                    keyPitchClass = item.key.value,
                    targetMidi = item.targetMidi,
                    timbreId = item.timbre.name,
                    cadenceFadeLevel = axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0,
                    timestamp = clock.now(),
                )
            allAttempts += attempt
            confusionState = ConfusionTracker.record(confusionState, targetLabel, responseLabel, clock)
            axisState = AxisScheduler.update(axisState, correct)

            val window = allAttempts.takeLast(MasteryEvaluator.WINDOW_SIZE)
            val verdict = MasteryEvaluator.evaluate(window, activeDegrees.toSet(), axisLevels)
            masteryTimeline += verdict.isMastered
        }

        return SimulationResult(axisState, allAttempts, confusionState, masteryTimeline)
    }
}

data class SimulationResult(
    val finalAxisState: AxisSchedulerState,
    val allAttempts: List<Attempt>,
    val finalConfusion: ConfusionState,
    /** `isMastered` evaluated after every single item, in order. */
    val masteryTimeline: List<Boolean>,
) {
    fun finalVerdict(activeDegrees: Set<ScaleDegree>): MasteryVerdict =
        MasteryEvaluator.evaluate(
            allAttempts.takeLast(MasteryEvaluator.WINDOW_SIZE),
            activeDegrees,
            finalAxisState.levels,
        )

    fun accuracyOverLast(n: Int): Double {
        val window = allAttempts.takeLast(n)
        if (window.isEmpty()) return 0.0
        return window.count { it.correct }.toDouble() / window.size
    }
}

/** A configurable simulated learner — docs/10-TESTING.md §5. */
class SimulatedResponder(
    /** Probability of a correct answer, as a function of the current axis levels and which degree was asked. */
    private val correctProbability: (axisLevels: Map<DifficultyAxis, Int>, target: ScaleDegree) -> Double,
    /** How an incorrect answer picks its (wrong) response label, given the true target. Defaults to any other active degree. */
    private val wrongAnswerPicker: (
        target: ScaleDegree,
        activeDegrees: List<ScaleDegree>,
        random: Random,
    ) -> ScaleDegree = { target, active, rng ->
        active.filterNot { it == target }.randomOrNull(rng) ?: target
    },
) {
    fun answer(
        axisLevels: Map<DifficultyAxis, Int>,
        target: ScaleDegree,
        random: Random,
    ): Boolean = random.nextDouble() < correctProbability(axisLevels, target).coerceIn(0.0, 1.0)

    fun wrongAnswer(
        target: ScaleDegree,
        activeDegrees: List<ScaleDegree>,
        random: Random,
    ): ScaleDegree = wrongAnswerPicker(target, activeDegrees, random)
}
