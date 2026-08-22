package com.tonic.core.engine.simulation

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.confusion.ConfusionTracker
import com.tonic.core.model.ids.SkillIds
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/10-TESTING.md §5 simulation 5: "Always answers '1'." Assert mastery
 * is never granted and the confusion tracker identifies the pattern.
 */
class BiasedResponderSimulationTest {
    @Test
    fun `an always-answers-1 responder never masters and the confusion tracker names the pattern`() {
        val skill = SkillIds.M2_FULL_DIATONIC
        val activeDegrees = SkillGraph.activeDegreesFor(skill).sortedBy { it.degree }

        val responder =
            SimulatedResponder(
                correctProbability = { _, target, _ -> if (target.degree == 1) 1.0 else 0.0 },
                wrongAnswerPicker = { _, active, _ -> active.first { it.degree == 1 } },
            )

        val result = SimulationHarness.run(skill, itemCount = 400, responder = responder, seedBase = 500L)

        assertTrue(
            result.masteryTimeline.none { it },
            "an indiscriminate always-1 responder must never be granted mastery",
        )

        val matrix = ConfusionTracker.toMatrix(result.finalConfusion)
        val nonOneDegrees = activeDegrees.filter { it.degree != 1 }.map { it.degree.toString() }
        for (degree in nonOneDegrees) {
            val pairs = ConfusionTracker.confusionPairs(matrix)
            assertTrue(
                pairs.any { it.target == degree && it.response == "1" },
                "confusion tracker should flag $degree -> 1 as a confusion pair",
            )
        }

        // "1" itself should show as the (only) weak-free degree; every other active degree should be weak.
        val weak = ConfusionTracker.weakTargets(matrix, activeDegrees.map { it.degree.toString() })
        assertTrue(weak.containsAll(nonOneDegrees), "every non-1 degree should be identified as weak: weak=$weak")
        assertTrue("1" !in weak, "degree 1 itself is answered correctly every time and should not be weak")
    }
}
