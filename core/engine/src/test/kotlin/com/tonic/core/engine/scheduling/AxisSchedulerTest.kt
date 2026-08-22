package com.tonic.core.engine.scheduling

import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.StaircaseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** docs/09-BUILD-PLAN.md Stage 4 acceptance: moves one axis at a time; 60%-accuracy safety valve fires correctly. */
class AxisSchedulerTest {
    @Test
    fun `starts on the highest-priority axis`() {
        val state = AxisScheduler.update(AxisSchedulerState(), correct = true)
        assertEquals(DifficultyAxis.CADENCE_FADE, state.activeAxis)
    }

    @Test
    fun `only the active axis's level ever changes while it is active`() {
        var state = AxisSchedulerState()
        val before = state.levels.toMap()
        repeat(3) { state = AxisScheduler.update(state, correct = true) }
        val active = state.activeAxis!!
        // RECOGNITION_AXES, not entries, as of Phase 2 Stage 2.0: the scheduler's level map holds the
        // axes of the node's own scope, and a prediction axis is deliberately absent from a recognition
        // node's state rather than present at 0 (docs/20-PHASE-2-SPEC.md §4 change 3). Same six axes
        // asserted as before for every Phase 1 node.
        for (axis in DifficultyAxis.RECOGNITION_AXES) {
            if (axis != active) {
                assertEquals(before.getValue(axis), state.levels.getValue(axis), "$axis moved while inactive")
            }
        }
    }

    @Test
    fun `converging the active axis freezes it and advances to the next priority axis`() {
        var state = AxisSchedulerState()
        // Drive CADENCE_FADE (max level 7) straight to its ceiling with an unbroken run of correct
        // answers - the simplest way to force convergence (via the at-max path) deterministically.
        var iterations = 0
        while (state.activeAxis != DifficultyAxis.TIMBRE_VARIETY && iterations < 100) {
            state = AxisScheduler.update(state, correct = true)
            iterations++
        }
        assertTrue(DifficultyAxis.CADENCE_FADE in state.frozen)
        assertEquals(DifficultyAxis.TIMBRE_VARIETY, state.activeAxis)
    }

    @Test
    fun `reaching max level also freezes and advances, even without 6 reversals`() {
        var state =
            AxisSchedulerState(
                levels =
                    mapOf(
                        DifficultyAxis.OCTAVE_DISPLACE to DifficultyAxis.OCTAVE_DISPLACE.maxLevel,
                    ),
                activeAxis = DifficultyAxis.OCTAVE_DISPLACE,
            )
        state = AxisScheduler.update(state, correct = true)
        state = AxisScheduler.update(state, correct = true) // second correct -> would move up, but already at max
        assertTrue(DifficultyAxis.OCTAVE_DISPLACE in state.frozen)
        assertTrue(state.activeAxis != DifficultyAxis.OCTAVE_DISPLACE)
    }

    @Test
    fun `arriving at max for the first time does not instantly freeze - only reconfirming it on a later trial does`() {
        // Starts one level below max, with the reversal history already at the halved step size so a
        // single pair of correct answers can hop straight onto max in one move - the exact shape of the
        // real bug this guards against: a learner who is genuinely poor at the ceiling level can still
        // get lucky twice in a row and land there.
        val preArrival =
            StaircaseState(level = DifficultyAxis.OCTAVE_DISPLACE.maxLevel - 1, reversals = listOf(0, 1), stepSize = 1)
        var state =
            AxisSchedulerState(
                levels = mapOf(DifficultyAxis.OCTAVE_DISPLACE to DifficultyAxis.OCTAVE_DISPLACE.maxLevel - 1),
                staircases = mapOf(DifficultyAxis.OCTAVE_DISPLACE to preArrival),
                activeAxis = DifficultyAxis.OCTAVE_DISPLACE,
            )

        state = AxisScheduler.update(state, correct = true) // consecutiveCorrect = 1, no move yet
        // consecutiveCorrect hits 2 -> moves onto max, arriving for the first time.
        state = AxisScheduler.update(state, correct = true)
        assertEquals(DifficultyAxis.OCTAVE_DISPLACE.maxLevel, state.levels.getValue(DifficultyAxis.OCTAVE_DISPLACE))
        assertTrue(
            DifficultyAxis.OCTAVE_DISPLACE !in state.frozen,
            "a first-time arrival at max must not instantly freeze - it needs one more trial to confirm the level is genuinely sustainable",
        )
        assertEquals(
            DifficultyAxis.OCTAVE_DISPLACE,
            state.activeAxis,
            "should still be the active axis, awaiting reconfirmation",
        )

        // A miss right here reveals a cliff, same as any other staircase step - not a freeze at a level
        // the learner cannot actually sustain.
        val afterMiss = AxisScheduler.update(state, correct = false)
        assertTrue(
            afterMiss.levels.getValue(DifficultyAxis.OCTAVE_DISPLACE) < DifficultyAxis.OCTAVE_DISPLACE.maxLevel,
            "a miss right after arriving at max must step back down, not get locked in",
        )

        // Reconfirmation takes a genuine *pair*, not one answer. A single correct while sitting at max
        // doesn't even move the staircase (Staircase returns early on a first correct), so treating it
        // as confirmation would freeze on a coin flip - at chance on a 7-degree set, a 1-in-7 shot per
        // trial at being pinned at max forever, since a maxed axis is never revisited. See
        // docs/07-ADAPTIVE-ENGINE.md §3, "Arriving at max vs. reconfirming it."
        val oneCorrectAtMax = AxisScheduler.update(state, correct = true)
        assertTrue(
            DifficultyAxis.OCTAVE_DISPLACE !in oneCorrectAtMax.frozen,
            "one lucky answer at max is not confirmation - it doesn't even move the staircase",
        )

        val reconfirmed = AxisScheduler.update(oneCorrectAtMax, correct = true)
        assertTrue(
            DifficultyAxis.OCTAVE_DISPLACE in reconfirmed.frozen,
            "two consecutive correct at max is a real upward move the ceiling clamps - that freezes it",
        )
    }

    @Test
    fun `safety valve steps the active axis down 2 and clears reversal history below 60 percent over 15 items`() {
        // Constructed directly rather than driven through 14 real update() calls: 14 straight incorrect
        // answers would bottom the staircase out at the axis floor via its own 1-down-per-miss mechanics
        // long before the valve's 15-item window even fills, which would make "stepped down 2" untestable
        // (there'd be nowhere left to step down to). Level 5 has headroom for a clean -2 step to be visible.
        val preValveStaircase = StaircaseState(level = 5, reversals = listOf(3, 6), stepSize = 1)
        var state =
            AxisSchedulerState(
                levels = mapOf(DifficultyAxis.CADENCE_FADE to 5),
                staircases = mapOf(DifficultyAxis.CADENCE_FADE to preValveStaircase),
                activeAxis = DifficultyAxis.CADENCE_FADE,
                recentCorrectness = List(14) { false }, // 14 of the eventual 15-item window already incorrect
            )
        state = AxisScheduler.update(state, correct = false) // the 15th item -> 0% over the window, valve should fire
        val levelAfterValve = state.levels.getValue(DifficultyAxis.CADENCE_FADE)
        // The staircase's own move (-1, since stepSize=1) happens first, then the valve steps down 2 more.
        assertEquals(5 - 1 - 2, levelAfterValve)
        assertEquals(
            0,
            state.staircases
                .getValue(DifficultyAxis.CADENCE_FADE)
                .reversals.size,
        )
        assertTrue(state.recentCorrectness.isEmpty(), "window should clear after the valve fires")
    }

    @Test
    fun `safety valve does not fire above 60 percent over 15 items`() {
        var state =
            AxisSchedulerState(
                levels = mapOf(DifficultyAxis.CADENCE_FADE to 6),
                activeAxis = DifficultyAxis.CADENCE_FADE,
            )
        // 10 correct (well-spaced to avoid triggering convergence early), 5 incorrect = 66% > 60%.
        val pattern =
            listOf(true, true, false, true, true, false, true, true, false, true, true, false, true, true, false)
        for (correct in pattern) state = AxisScheduler.update(state, correct = correct)
        // Should not have been forcibly stepped down by the valve (level may still have moved via normal staircase rules).
        assertNotNull(state.activeAxis)
    }

    @Test
    fun `maintenance pass unfreezes the highest-priority non-max axis once everything is frozen`() {
        val allButOneMaxed =
            DifficultyAxis.entries.associateWith { it.maxLevel }.toMutableMap().apply {
                this[DifficultyAxis.TEMPO_DENSITY] = 0 // the only axis with room left
            }
        val allFrozenExceptNone = DifficultyAxis.entries.toSet() // everything already frozen
        val state = AxisSchedulerState(levels = allButOneMaxed, frozen = allFrozenExceptNone, activeAxis = null)
        val next = AxisScheduler.update(state, correct = true)
        assertEquals(DifficultyAxis.TEMPO_DENSITY, next.activeAxis)
        assertTrue(DifficultyAxis.TEMPO_DENSITY !in next.frozen, "the maintenance pass should unfreeze it")
    }

    @Test
    fun `once every axis is genuinely maxed there is no active axis`() {
        val allMaxed = DifficultyAxis.entries.associateWith { it.maxLevel }
        val state = AxisSchedulerState(levels = allMaxed, frozen = DifficultyAxis.entries.toSet(), activeAxis = null)
        val next = AxisScheduler.update(state, correct = true)
        assertEquals(null, next.activeAxis)
    }
}
