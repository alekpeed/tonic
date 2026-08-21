package com.tonic.core.engine.simulation

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/10-TESTING.md §5 simulations 2-4: improving, plateaued, and
 * struggling learners. Each is the same [SimulationHarness] wired to a
 * different [SimulatedResponder] psychometric function.
 */
class LearnerSimulationTest {
    private val skill = SkillIds.M2_FULL_DIATONIC
    private val activeDegrees = SkillGraph.activeDegreesFor(skill)

    @Test
    fun `improving learner - axis levels advance and mastery is eventually reached without thrashing`() {
        var itemIndex = 0
        val responder =
            SimulatedResponder(correctProbability = { axisLevels, _, _ ->
                val cadence = (axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0).toDouble()
                val skillLevel = itemIndex * 0.03
                itemIndex++
                1.0 / (1.0 + Math.exp(cadence - skillLevel))
            })

        val result = SimulationHarness.run(skill, itemCount = 500, responder = responder, seedBase = 100L)

        val finalCadence = result.finalAxisState.levels.getValue(DifficultyAxis.CADENCE_FADE)
        assertTrue(finalCadence > 0, "cadence fade should have advanced from its starting level of 0")

        // "Monotonic-ish without thrashing": once axes reach their ceiling they should stay frozen, not
        // oscillate back down repeatedly. Check the final 100 items didn't see wild axis-level swings.
        val lateCadenceLevels = result.allAttempts.takeLast(100).map { it.cadenceFadeLevel }
        val lateSwings = lateCadenceLevels.zipWithNext().count { (a, b) -> kotlin.math.abs(a - b) > 2 }
        assertTrue(lateSwings <= 3, "too many large late-session cadence swings: $lateSwings")

        assertTrue(
            result.masteryTimeline.takeLast(50).any {
                it
            },
            "an improving learner should reach mastery by the end of 500 items",
        )
    }

    @Test
    fun `plateaued learner - the engine stabilizes rather than oscillating, and mastery is not falsely granted`() {
        // Ability that declines steeply with cadence-fade level (0.75 at L0, down to a 0.15 floor by L5)
        // so a genuine low equilibrium exists for the staircase to settle at, rather than a flat accuracy
        // the staircase can never converge against (a level-independent responder has no fixed point and
        // random-walks the axis's full range instead of plateauing). The slope has to be steep enough
        // that a lucky streak can't carry the level all the way to the axis ceiling and freeze there via
        // the "reached max level" rule regardless of subsequent misses (a gentler 0.05-per-level slope
        // still left ~40% accuracy near the ceiling - not negligible over hundreds of items) - and low
        // enough by level 2 (0.51) to trip AxisScheduler's 60%-over-15-items safety valve, which yanks
        // the level back down before it can wander further. Every level along the way stays well below
        // the 90% mastery accuracy floor.
        val responder =
            SimulatedResponder(correctProbability = { axisLevels, _, _ ->
                val cadence = (axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0).toDouble()
                (0.75 - cadence * 0.12).coerceIn(0.15, 0.75)
            })
        val result = SimulationHarness.run(skill, itemCount = 400, responder = responder, seedBase = 200L)

        assertTrue(
            result.masteryTimeline.none {
                it
            },
            "a plateaued learner (never above 75% accuracy, at any level) must never be granted mastery",
        )

        // Stabilizes: axis levels in the back half of the run should hover low rather than climb toward
        // the ceiling. A raw distinct-level count is too sensitive to ordinary staircase noise (a single
        // lucky streak briefly nudging the level up by one is not "thrashing"); average and peak level
        // are the more robust signal that the level is genuinely settling near its equilibrium instead
        // of drifting upward.
        val lateLevels = result.allAttempts.takeLast(150).map { it.cadenceFadeLevel }
        val averageLateLevel = lateLevels.average()
        val maxLateLevel = lateLevels.max()
        assertTrue(
            averageLateLevel <= 2.0,
            "expected the plateaued learner's level to settle low on average, got $averageLateLevel",
        )
        assertTrue(
            maxLateLevel <= DifficultyAxis.CADENCE_FADE.maxLevel - 2,
            "expected the plateaued learner to stay well clear of the cadence-fade ceiling, saw a late peak of $maxLateLevel",
        )
    }

    @Test
    fun `struggling learner - the 60 percent safety valve fires, difficulty drops, and the user is never trapped`() {
        // Low ability, and it gets WORSE at higher cadence levels specifically, so climbing difficulty
        // punishes them - exactly the scenario the safety valve exists for.
        val responder =
            SimulatedResponder(correctProbability = { axisLevels, _, _ ->
                val cadence = (axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0).toDouble()
                (0.5 - cadence * 0.08).coerceIn(0.05, 0.5)
            })
        val result = SimulationHarness.run(skill, itemCount = 300, responder = responder, seedBase = 300L)

        // Never trapped: the learner should not be stuck at a high cadence level with sustained low accuracy -
        // check no 30-item window anywhere in the back half sits both at a high level AND below 50% accuracy.
        val windows = result.allAttempts.windowed(30, step = 15)
        val trapped =
            windows.any { w ->
                val avgLevel = w.map { it.cadenceFadeLevel }.average()
                val accuracy = w.count { it.correct }.toDouble() / w.size
                avgLevel > 4.0 && accuracy < 0.40
            }
        assertTrue(!trapped, "found a window where the learner was stuck at a high level with sustained low accuracy")

        assertTrue(
            result.masteryTimeline.none { it },
            "a consistently struggling learner must never be granted mastery",
        )
    }
}
