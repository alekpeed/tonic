package com.tonic.core.engine.confusion

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.state.ConfusionState
import com.tonic.core.model.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfusionTrackerTest {
    private val fixedClock = Clock { Instant.parse("2026-01-01T00:00:00Z") }

    private fun recordMany(
        state: ConfusionState,
        vararg pairs: Pair<String, String>,
    ): ConfusionState =
        pairs.fold(state) { acc, (target, response) -> ConfusionTracker.record(acc, target, response, fixedClock) }

    @Test
    fun `weak targets are those below 80 percent window accuracy`() {
        var state = ConfusionState(SkillIds.M2_FULL_DIATONIC)
        // "4" correct 6/10 -> 60%, weak. "1" correct 9/10 -> 90%, not weak.
        state = recordMany(state, *Array(6) { "4" to "4" }, *Array(4) { "4" to "3" })
        state = recordMany(state, *Array(9) { "1" to "1" }, *Array(1) { "1" to "2" })
        val matrix = ConfusionTracker.toMatrix(state)
        val weak = ConfusionTracker.weakTargets(matrix, listOf("1", "4"))
        assertEquals(setOf("4"), weak)
    }

    @Test
    fun `confusion pairs above 10 percent of a target's attempts are flagged`() {
        var state = ConfusionState(SkillIds.M2_FULL_DIATONIC)
        // "7" -> "1" happens 3/10 = 30%, well above 10%.
        state = recordMany(state, *Array(7) { "7" to "7" }, *Array(3) { "7" to "1" })
        val matrix = ConfusionTracker.toMatrix(state)
        val pairs = ConfusionTracker.confusionPairs(matrix)
        assertTrue(pairs.any { it.target == "7" && it.response == "1" })
    }

    @Test
    fun `remediation weight caps at 2-5x the lowest weight even for a very weak degree`() {
        var state = ConfusionState(SkillIds.M2_FULL_DIATONIC)
        // "4" at 0% accuracy (maximally weak); "1", "3", "5" at 100% (not weak at all).
        state = recordMany(state, *Array(10) { "4" to "3" })
        state = recordMany(state, *Array(10) { "1" to "1" })
        state = recordMany(state, *Array(10) { "3" to "3" })
        state = recordMany(state, *Array(10) { "5" to "5" })

        val matrix = ConfusionTracker.toMatrix(state)
        val weights = ConfusionTracker.remediationWeights(matrix, listOf("1", "3", "4", "5"))

        val lowest = weights.values.min()
        val highest = weights.values.max()
        assertTrue(highest <= lowest * 2.5 + 1e-9, "highest=$highest lowest=$lowest ratio=${highest / lowest}")
        assertTrue(weights.getValue("4") == highest, "the 0%-accuracy degree should get the highest weight")
    }

    @Test
    fun `a degree with no data yet is not treated as weak`() {
        val matrix = ConfusionTracker.toMatrix(ConfusionState(SkillIds.M2_FULL_DIATONIC))
        val weights = ConfusionTracker.remediationWeights(matrix, listOf("1", "2"))
        assertEquals(1.0, weights.getValue("1"))
        assertEquals(1.0, weights.getValue("2"))
    }

    @Test
    fun `count is all-time while windowCount reflects only the last 100 attempts`() {
        var state = ConfusionState(SkillIds.M2_FULL_DIATONIC)
        repeat(120) { state = ConfusionTracker.record(state, "1", "1", fixedClock) }
        val matrix = ConfusionTracker.toMatrix(state)
        val cell = matrix.cells.single()
        assertEquals(120, cell.count)
        assertEquals(100, cell.windowCount)
    }
}
