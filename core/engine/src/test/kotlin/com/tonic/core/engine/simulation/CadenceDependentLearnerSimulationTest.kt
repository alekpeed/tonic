package com.tonic.core.engine.simulation

import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M2ItemGenerator
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.mastery.IndependenceCheck
import com.tonic.core.engine.mastery.MasteryEvaluator
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/10-TESTING.md §5 simulation 6 — **the most important test in this
 * codebase**: "High accuracy at fade L0-L3, chance at L6. Assert
 * `M2.INDEPENDENCE_CHECK` fails and the fade axis is lowered. This
 * simulation directly tests the app's central pedagogical claim — if it
 * passes a learner who cannot function without the crutch, the app is
 * broken in exactly the way competing apps are broken."
 *
 * The responder here is deliberately generous through L4-L5 (still
 * passable mastery accuracy — those levels still provide *some* reference:
 * a drone, a brief flash) and craters at L6 (no reference at all). That gap
 * is exactly what mastery criterion 5 (CADENCE_FADE >= 4, not >= 6) can't
 * see on its own, and exactly what `M2.INDEPENDENCE_CHECK` exists to catch.
 *
 * Both halves of this test are built from a fixed representative axis
 * snapshot (the mastery check at L5, the independence check at L6),
 * rather than driven through the full six-axis [SimulationHarness] /
 * `AxisScheduler` loop. That loop is the right tool for testing *scheduler*
 * dynamics (docs/10-TESTING.md simulations 2-4, already covered by
 * [LearnerSimulationTest] and `AxisSchedulerTest`), but is the wrong tool
 * here: because this responder's accuracy depends only on the cadence
 * level - not on which axis is nominally being staircased at the moment -
 * and CADENCE_FADE is scheduling priority #1, once the other five axes
 * reach their own ceilings CADENCE_FADE becomes the *only* axis with room
 * left. Every maintenance-pass reactivation from then on immediately
 * re-selects it, forcing it to keep re-probing the L5/L6 cliff for the
 * rest of the run (confirmed directly: this repeats indefinitely, even
 * over thousands of items, and never lets a 30-item mastery window stay
 * clean of an L6 excursion for long). That's a real property of one
 * unbroken, unbounded simulated session - not of the real app, where a
 * maintenance pass is spread across separate practice sessions - so
 * whether *that* settles is [LearnerSimulationTest]'s job, not this test's.
 * What this test verifies is narrower and is exactly what a fixed L5
 * snapshot gives directly: the divergence between ordinary mastery and the
 * independence check.
 */
class CadenceDependentLearnerSimulationTest {
    private val skill = SkillIds.M2_FULL_DIATONIC
    private val activeDegrees = SkillGraph.activeDegreesFor(skill).sortedBy { it.degree }

    private fun cadenceDependentResponder(): SimulatedResponder =
        SimulatedResponder(correctProbability = { axisLevels, _ ->
            val cadence = axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0
            if (cadence < 6) 0.92 else 1.0 / activeDegrees.size // chance level once the reference is truly gone
        })

    @Test
    fun `a cadence-dependent learner masters the node but fails the independence check`() {
        val responder = cadenceDependentResponder()

        // "High accuracy at fade L0-L3, chance at L6" - L5 is deep in that high-accuracy band and is
        // also where CadenceFadeLevel.MASTERY_MINIMUM (L4) is already satisfied, so this is a faithful
        // representative snapshot of "the node the learner would organically settle at." Mastery is
        // evaluated the same way [SimulationHarness] does it - after every attempt, over the trailing
        // 30-item window - rather than assuming the very first 30 items happen to qualify: the item
        // generator balances target-degree frequency over a rolling span (docs/03-CURRICULUM.md §5.4),
        // not within every single arbitrary 30-item slice, so an early window can legitimately undershoot
        // DEGREE_COVERAGE by chance even at 92% accuracy. Generating a larger block and taking the first
        // qualifying window is the correct way to ask "does this learner reach mastery," matching how the
        // real practice loop would observe it.
        val masteryBatch = runFixedAxisBlock(responder, cadenceLevel = 5, seedBase = 600L, itemCount = 200)
        val masteryWindow =
            (MasteryEvaluator.WINDOW_SIZE..masteryBatch.size)
                .asSequence()
                .map { end -> masteryBatch.subList(end - MasteryEvaluator.WINDOW_SIZE, end) }
                .firstOrNull { MasteryEvaluator.evaluate(it, activeDegrees.toSet(), it.last().axisLevels).isMastered }
        val verdict =
            MasteryEvaluator.evaluate(
                masteryWindow ?: masteryBatch.takeLast(MasteryEvaluator.WINDOW_SIZE),
                activeDegrees.toSet(),
                masteryBatch.last().axisLevels,
            )
        assertTrue(
            verdict.isMastered,
            "the learner should reach ordinary mastery within ${masteryBatch.size} items - " +
                verdict.criteria.joinToString {
                    "${it.kind}=${it.met}(${it.measuredValue}/${it.requiredValue})"
                },
        )

        // The independence check runs separately at a forced CADENCE_FADE=6, using axis levels otherwise
        // matching where the node landed - docs/03-CURRICULUM.md §5.6.
        val independenceItems =
            runFixedAxisBlock(
                responder,
                cadenceLevel = 6,
                seedBase = 601L,
                itemCount = IndependenceCheck.REQUIRED_ITEMS,
            )
        val checkResult = IndependenceCheck.evaluate(independenceItems)

        assertTrue(
            !checkResult.passed,
            "a cadence-dependent learner (chance-level at L6) must FAIL the independence check - " +
                "measured accuracy=${checkResult.accuracy} over ${checkResult.itemCount} items",
        )

        val loweredAxes = IndependenceCheck.applyFailure(masteryBatch.last().axisLevels)
        assertTrue(
            loweredAxes.getValue(DifficultyAxis.CADENCE_FADE) <
                masteryBatch.last().axisLevels.getValue(DifficultyAxis.CADENCE_FADE),
            "failing the independence check must lower the fade axis",
        )
    }

    @Test
    fun `a genuinely independent learner - good even with no reference - passes the check`() {
        // Equally good at every cadence level, including L6.
        val independentResponder = SimulatedResponder(correctProbability = { _, _ -> 0.90 })
        val independenceItems =
            runFixedAxisBlock(
                independentResponder,
                cadenceLevel = 6,
                seedBase = 602L,
                itemCount = IndependenceCheck.REQUIRED_ITEMS,
            )
        val checkResult = IndependenceCheck.evaluate(independenceItems)
        assertTrue(
            checkResult.passed,
            "a learner who performs equally well with no reference should pass: accuracy=${checkResult.accuracy}",
        )
    }

    /** [itemCount] items generated at a forced [cadenceLevel], all other axes at a representative mastered level. */
    private fun runFixedAxisBlock(
        responder: SimulatedResponder,
        cadenceLevel: Int,
        seedBase: Long,
        itemCount: Int = MasteryEvaluator.WINDOW_SIZE,
    ): List<Attempt> {
        val axes =
            mapOf(
                DifficultyAxis.CADENCE_FADE to cadenceLevel,
                DifficultyAxis.TIMBRE_VARIETY to 2,
                DifficultyAxis.REGISTER_SPREAD to 1,
                DifficultyAxis.OCTAVE_DISPLACE to 1,
                DifficultyAxis.TEMPO_DENSITY to 1,
                DifficultyAxis.KEY_SPREAD to 1,
            )
        var history = GenerationHistory()
        val rng = Random(seedBase)
        val items = mutableListOf<Attempt>()
        repeat(itemCount) { i ->
            val result = M2ItemGenerator.generate(skill, axes, seed = seedBase + i * 131L, history = history)
            history = result.updatedHistory
            val item = result.item
            val correct = responder.answer(axes, item.targetDegree, rng)
            val targetLabel = item.targetDegree.degree.toString()
            val responseLabel =
                if (correct) {
                    targetLabel
                } else {
                    responder
                        .wrongAnswer(
                            item.targetDegree,
                            activeDegrees,
                            rng,
                        ).degree
                        .toString()
                }
            items +=
                Attempt(
                    skillId = skill,
                    sessionId = 2L,
                    itemSeed = item.seed,
                    axisLevels = axes,
                    targetLabel = targetLabel,
                    responseLabel = responseLabel,
                    correct = correct,
                    latencyMs = 600,
                    replayCount = 0,
                    keyPitchClass = item.key.value,
                    targetMidi = item.targetMidi,
                    timbreId = item.timbre.name,
                    cadenceFadeLevel = cadenceLevel,
                    timestamp = Instant.EPOCH,
                )
        }
        return items
    }
}
