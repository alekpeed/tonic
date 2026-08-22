package com.tonic.core.engine.simulation

import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M12ItemGenerator
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.curriculum.generators.M9ItemGenerator
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.confusion.ConfusionTracker
import com.tonic.core.engine.mastery.BinaryMasteryEvaluator
import com.tonic.core.engine.mastery.MasteryEvaluator
import com.tonic.core.engine.mastery.PredictionMasteryEvaluator
import com.tonic.core.engine.scheduling.AxisScheduler
import com.tonic.core.engine.scheduling.AxisSchedulerState
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.ConfusionState
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

            val correct = responder.answer(axisLevels, item.targetDegree, item.mode, rng)
            // canonicalLabel, not degree.toString(): the latter silently drops the alteration, so ♭3
            // and ♮3 would both label as "3" and every minor, chromatic or mixed-mode simulation would
            // be scoring two different answers as one. Byte-identical for every Phase 1 degree, which
            // all carry alteration 0 - so the M2 simulations below are provably unmoved.
            val targetLabel = item.targetDegree.canonicalLabel
            val responseLabel =
                if (correct) {
                    targetLabel
                } else {
                    responder.wrongAnswer(item.targetDegree, activeDegrees, rng).canonicalLabel
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
            val verdict =
                MasteryEvaluator.evaluate(
                    window,
                    activeDegrees.toSet(),
                    axisLevels,
                    focusDegree = SkillGraph.focusDegreeFor(skill),
                )
            masteryTimeline += verdict.isMastered
        }

        return SimulationResult(axisState, allAttempts, confusionState, masteryTimeline)
    }

    /**
     * `M9.*` — a two-choice node with no difficulty axes of its own, judged by
     * [BinaryMasteryEvaluator]. Separate from [run] rather than folded into it because almost nothing
     * is shared: there is no degree, no axis to staircase, and no confusion matrix worth keeping (a
     * 2×2 of major/minor says only what the accuracy already said).
     */
    fun runModeId(
        skill: SkillId,
        itemCount: Int,
        responder: BinaryResponder,
        seedBase: Long = 1L,
    ): BinarySimulationResult {
        var genHistory = GenerationHistory()
        val attempts = mutableListOf<Attempt>()
        val masteryTimeline = mutableListOf<Boolean>()
        val rng = Random(seedBase)

        repeat(itemCount) { i ->
            val result = M9ItemGenerator.generate(skill, seed = seedBase + i * 7919L, history = genHistory)
            genHistory = result.updatedHistory
            val item = result.item

            val truth = item.correctLabel
            val response = responder.answer(truth, AnswerAlphabet.MajorMinor.labels, rng)
            attempts +=
                binaryAttempt(
                    skill = skill,
                    index = i,
                    seed = item.seed,
                    axisLevels = emptyMap(),
                    target = truth,
                    response = response,
                    keyPitchClass = item.key.value,
                    targetMidi = item.tonicMidi,
                    timbre = item.timbre.name,
                )
            val window = attempts.takeLast(BinaryMasteryEvaluator.WINDOW_SIZE)
            masteryTimeline +=
                BinaryMasteryEvaluator
                    .evaluate(window, signalLabel = AnswerAlphabet.MajorMinor.MINOR)
                    .isMastered
        }
        return BinarySimulationResult(attempts, masteryTimeline)
    }

    /**
     * `M12.*` — prediction, judged by [PredictionMasteryEvaluator] and staircased over the *prediction*
     * axes. The responder sees the gap level, because that is what §7's simulation 6 is about: mastery
     * at `PREDICT_GAP` ≥ 2 means holding a note across a real silence, not across an echo.
     *
     * @param sungCentsFor what the learner audiated on item `i`, in cents from the degree that was
     *   named, or null for an item they did not sing into — docs/30-PHASE-3-SPEC.md §5.4. Stamped onto
     *   the attempt and otherwise ignored, exactly as the app does it: the sung prediction supplements
     *   the button and never scores, so a simulation that let it reach the responder or the evaluator
     *   would be modeling an app that does not exist. Its only purpose here is to let a test assert
     *   that a whole run's worth of perfect audiation moves nothing.
     */
    fun runPrediction(
        skill: SkillId,
        itemCount: Int,
        responder: PredictionResponder,
        seedBase: Long = 1L,
        sungCentsFor: (Int) -> Int? = { null },
    ): BinarySimulationResult {
        var axisState = AxisSchedulerState.forScope(DifficultyAxis.Scope.PREDICTION)
        var genHistory = GenerationHistory()
        val attempts = mutableListOf<Attempt>()
        val masteryTimeline = mutableListOf<Boolean>()
        val rng = Random(seedBase)

        repeat(itemCount) { i ->
            val axisLevels = axisState.levels
            val result = M12ItemGenerator.generate(skill, axisLevels, seed = seedBase + i * 7919L, history = genHistory)
            genHistory = result.updatedHistory
            val item = result.item

            val truth = item.correctLabel
            val response = responder.answer(axisLevels, truth, item.matches, rng)
            // The node's own scoring rule, not equality - M12.PREDICT_TRIAD forgives a wrong direction
            // (docs/20-PHASE-2-SPEC.md §8.1 decision 3), and a simulation that ignored that would be
            // measuring a stricter app than the one that ships.
            val correct =
                if (SkillGraph.scoresDirection(skill)) {
                    response == truth
                } else {
                    AnswerAlphabet.MatchDirection.matchedVsNot(response) ==
                        AnswerAlphabet.MatchDirection.matchedVsNot(truth)
                }
            attempts +=
                binaryAttempt(
                    skill = skill,
                    index = i,
                    seed = item.seed,
                    axisLevels = axisLevels,
                    target = truth,
                    response = response,
                    keyPitchClass = item.key.value,
                    targetMidi = item.soundedMidi,
                    timbre = item.timbre.name,
                    correct = correct,
                    sungCents = sungCentsFor(i),
                )
            axisState = AxisScheduler.update(axisState, correct)
            val window = attempts.takeLast(PredictionMasteryEvaluator.WINDOW_SIZE)
            masteryTimeline += PredictionMasteryEvaluator.evaluate(window, axisState.levels).isMastered
        }
        return BinarySimulationResult(attempts, masteryTimeline, axisState)
    }

    private fun binaryAttempt(
        skill: SkillId,
        index: Int,
        seed: Long,
        axisLevels: Map<DifficultyAxis, Int>,
        target: String,
        response: String,
        keyPitchClass: Int,
        targetMidi: Int,
        timbre: String,
        correct: Boolean = target == response,
        sungCents: Int? = null,
    ) = Attempt(
        skillId = skill,
        sessionId = 1L,
        itemSeed = seed,
        axisLevels = axisLevels,
        targetLabel = target,
        responseLabel = response,
        correct = correct,
        latencyMs = 900,
        replayCount = 0,
        keyPitchClass = keyPitchClass,
        targetMidi = targetMidi,
        timbreId = timbre,
        cadenceFadeLevel = 0,
        timestamp = Instant.EPOCH.plusSeconds(index.toLong()),
        sungCents = sungCents,
    )
}

/** The result of a two-choice or three-choice simulation — see [SimulationHarness.runModeId]. */
data class BinarySimulationResult(
    val attempts: List<Attempt>,
    /** `isMastered` evaluated after every single item, in order. */
    val masteryTimeline: List<Boolean>,
    val finalAxisState: AxisSchedulerState? = null,
) {
    val everMastered: Boolean get() = masteryTimeline.any { it }

    fun accuracyOverLast(n: Int): Double {
        val window = attempts.takeLast(n)
        if (window.isEmpty()) return 0.0
        return window.count { it.correct }.toDouble() / window.size
    }
}

/** A simulated learner for a two-choice node (`M9`). */
class BinaryResponder(
    /** Given the true label and the alphabet, what does this learner answer? */
    private val respond: (truth: String, alphabet: List<String>, random: Random) -> String,
) {
    fun answer(
        truth: String,
        alphabet: List<String>,
        random: Random,
    ): String = respond(truth, alphabet, random)

    companion object {
        /** Answers correctly with probability [p], otherwise picks the other label. */
        fun accurate(p: Double) =
            BinaryResponder { truth, alphabet, rng ->
                if (rng.nextDouble() < p) truth else alphabet.first { it != truth }
            }

        /** Always answers [label], whatever was played — the response-bias case d-prime exists to catch. */
        fun alwaysAnswers(label: String) = BinaryResponder { _, _, _ -> label }
    }
}

/** A simulated learner for `M12`, which sees the gap it has to hold a note across. */
class PredictionResponder(
    private val respond: (
        axisLevels: Map<DifficultyAxis, Int>,
        truth: String,
        matched: Boolean,
        random: Random,
    ) -> String,
) {
    fun answer(
        axisLevels: Map<DifficultyAxis, Int>,
        truth: String,
        matched: Boolean,
        random: Random,
    ): String = respond(axisLevels, truth, matched, random)
}

data class SimulationResult(
    val finalAxisState: AxisSchedulerState,
    val allAttempts: List<Attempt>,
    val finalConfusion: ConfusionState,
    /** `isMastered` evaluated after every single item, in order. */
    val masteryTimeline: List<Boolean>,
) {
    fun finalVerdict(
        activeDegrees: Set<ScaleDegree>,
        focusDegree: ScaleDegree? = null,
    ): MasteryVerdict =
        MasteryEvaluator.evaluate(
            allAttempts.takeLast(MasteryEvaluator.WINDOW_SIZE),
            activeDegrees,
            finalAxisState.levels,
            focusDegree = focusDegree,
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
    private val correctProbability: (
        axisLevels: Map<DifficultyAxis, Int>,
        target: ScaleDegree,
        /**
         * The item's own mode. Constant for every node except `M10.MIXED_MODE`, and the whole point
         * there — §7's simulation 4 is a learner who is fluent in major and at chance in minor, which
         * cannot be expressed without it.
         */
        mode: Mode,
    ) -> Double,
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
        mode: Mode,
        random: Random,
    ): Boolean = random.nextDouble() < correctProbability(axisLevels, target, mode).coerceIn(0.0, 1.0)

    fun wrongAnswer(
        target: ScaleDegree,
        activeDegrees: List<ScaleDegree>,
        random: Random,
    ): ScaleDegree = wrongAnswerPicker(target, activeDegrees, random)
}
