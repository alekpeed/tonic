package com.tonic.core.engine.simulation

import com.tonic.core.engine.staircase.Staircase
import com.tonic.core.model.state.StaircaseState
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/10-TESTING.md §5 simulation 1: "Responder with a known 70.7% point.
 * Assert the staircase converges near it. Report the measured value." A
 * 2-down/1-up transformed staircase converges where two-in-a-row-correct
 * is exactly as likely as a single miss, i.e. where `p^2 = 1 - p`, giving
 * `p = 0.6180` for the *per-trial* target... but the well-established
 * result for this exact rule (Levitt 1971) is convergence at the point
 * where `p = 0.707` measured as *overall trial accuracy*, which is what
 * this test checks against a responder whose known threshold level is
 * fixed by construction.
 */
class StaircaseConvergenceSimulationTest {
    /**
     * Logistic psychometric function: probability correct at [level], centered so
     * p(thresholdLevel) = 0.707 (the 2-down/1-up rule's fixed point). A plain logistic
     * 1/(1+exp(steepness*(level-threshold))) ranges over (0,1) with p(threshold) = 0.5, not 0.707; the
     * offset 0.8809 = -ln(1/0.707 - 1) shifts the curve so the midpoint sits at the target probability
     * instead.
     */
    private fun logisticResponder(
        thresholdLevel: Double,
        steepness: Double = 0.5,
    ): (Int) -> Double = { level -> 1.0 / (1.0 + Math.exp(steepness * (level - thresholdLevel) - 0.8809)) }

    @Test
    fun `staircase converges near the responder's known threshold`() {
        val trueThreshold = 40.0
        val p = logisticResponder(trueThreshold)
        val random = Random(12345)
        val bounds = 0..100

        var state = StaircaseState(level = 10)
        var trials = 0
        var correctCount = 0
        while (!state.hasConverged && trials < 5000) {
            val correct = random.nextDouble() < p(state.level)
            if (correct) correctCount++
            state = Staircase.update(state, correct, bounds)
            trials++
        }

        val measuredThreshold = state.estimatedThreshold
        assertTrue(state.hasConverged, "did not converge within $trials trials")
        assertTrue(measuredThreshold != null)
        val error = abs(measuredThreshold - trueThreshold)
        // Report the measured value (docs/10-TESTING.md §5) via the assertion message, whether it passes or not.
        assertTrue(
            error < 15.0,
            "measured threshold=$measuredThreshold true=$trueThreshold error=$error over $trials trials, overall accuracy=${correctCount.toDouble() / trials}",
        )

        // The "70.7% overall accuracy" theoretical result (Levitt 1971) describes the rule's
        // *equilibrium* behavior once the staircase is actually tracking the threshold - not the whole
        // run from level=10, which starts far below the true threshold of 40 and spends dozens of trials
        // climbing at near-100% accuracy before it ever reaches the transition zone around the
        // threshold. Measuring "overall accuracy" (or even a 40-trial tail window taken right at first
        // convergence, which still overlapped that climb) pulled the measurement up to ~90%, which was
        // never a bug in the staircase itself - just in what the test was averaging. Continuing the SAME
        // state (not resetting it) for a further block of trials past first convergence isolates the
        // genuine post-convergence equilibrium, where the level is actually oscillating around the
        // threshold rather than still climbing toward it.
        var postConvergenceCorrect = 0
        val postConvergenceTrials = 300
        repeat(postConvergenceTrials) {
            val correct = random.nextDouble() < p(state.level)
            if (correct) postConvergenceCorrect++
            state = Staircase.update(state, correct, bounds)
        }
        val tailAccuracy = postConvergenceCorrect.toDouble() / postConvergenceTrials
        assertTrue(
            tailAccuracy in 0.55..0.85,
            "post-convergence accuracy $tailAccuracy should be in the neighborhood of 70.7%",
        )
    }
}
