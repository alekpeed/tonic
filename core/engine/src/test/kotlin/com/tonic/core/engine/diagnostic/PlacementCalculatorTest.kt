package com.tonic.core.engine.diagnostic

import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.EntryPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** docs/09-BUILD-PLAN.md Stage 8 acceptance: "placement mapping produces correct axis levels for boundary inputs." */
class PlacementCalculatorTest {
    @Test
    fun `above 200 cents routes to M1 remediation regardless of every other measurement`() {
        val placement = PlacementCalculator.compute(201, 4.0, 5, 4.0)
        assertEquals(EntryPoint.M1_REMEDIATION, placement.recommendedEntry)
        assertTrue(placement.initialAxisLevels.isEmpty())
    }

    @Test
    fun `exactly 200 cents does not trigger remediation - the boundary is strictly greater than`() {
        val placement = PlacementCalculator.compute(200, 4.0, 3, 4.0)
        assertEquals(EntryPoint.M2_STAGE_1, placement.recommendedEntry)
    }

    @Test
    fun `low amusia d-prime alone, with a normal pitch-direction threshold, does not flag`() {
        val placement = PlacementCalculator.compute(150, 4.0, 3, 0.1)
        assertFalse(placement.amusiaIndicatorFlag, "both conditions are required, not either")
        assertEquals(EntryPoint.M2_STAGE_1, placement.recommendedEntry)
    }

    @Test
    fun `an elevated pitch-direction threshold alone still routes to remediation but does not flag amusia`() {
        val placement = PlacementCalculator.compute(250, 4.0, 3, 4.0)
        assertEquals(EntryPoint.M1_REMEDIATION, placement.recommendedEntry)
        assertFalse(
            placement.amusiaIndicatorFlag,
            "elevated threshold alone is a remediation router, not an amusia signal",
        )
    }

    @Test
    fun `both weak signals together set the amusia flag`() {
        val placement = PlacementCalculator.compute(250, 4.0, 3, 0.1)
        assertTrue(placement.amusiaIndicatorFlag)
        assertEquals(EntryPoint.M1_REMEDIATION, placement.recommendedEntry)
    }

    @Test
    fun `axis start levels - boundary values from the docs 07-ADAPTIVE-ENGINE 5 table`() {
        // < 20
        assertEquals(
            mapOf(DifficultyAxis.CADENCE_FADE to 1, DifficultyAxis.TIMBRE_VARIETY to 2, DifficultyAxis.KEY_SPREAD to 1),
            axesOf(PlacementCalculator.compute(19, 0.0, 3, 4.0)),
        )
        // 20..49
        assertEquals(
            mapOf(DifficultyAxis.CADENCE_FADE to 1, DifficultyAxis.TIMBRE_VARIETY to 1, DifficultyAxis.KEY_SPREAD to 1),
            axesOf(PlacementCalculator.compute(20, 0.0, 3, 4.0)),
        )
        assertEquals(
            mapOf(DifficultyAxis.CADENCE_FADE to 1, DifficultyAxis.TIMBRE_VARIETY to 1, DifficultyAxis.KEY_SPREAD to 1),
            axesOf(PlacementCalculator.compute(49, 0.0, 3, 4.0)),
        )
        // 50..99
        assertEquals(
            mapOf(DifficultyAxis.CADENCE_FADE to 0, DifficultyAxis.TIMBRE_VARIETY to 1, DifficultyAxis.KEY_SPREAD to 0),
            axesOf(PlacementCalculator.compute(50, 0.0, 3, 4.0)),
        )
        assertEquals(
            mapOf(DifficultyAxis.CADENCE_FADE to 0, DifficultyAxis.TIMBRE_VARIETY to 1, DifficultyAxis.KEY_SPREAD to 0),
            axesOf(PlacementCalculator.compute(99, 0.0, 3, 4.0)),
        )
        // 100..200
        assertEquals(
            mapOf(DifficultyAxis.CADENCE_FADE to 0, DifficultyAxis.TIMBRE_VARIETY to 0, DifficultyAxis.KEY_SPREAD to 0),
            axesOf(PlacementCalculator.compute(100, 0.0, 3, 4.0)),
        )
        assertEquals(
            mapOf(DifficultyAxis.CADENCE_FADE to 0, DifficultyAxis.TIMBRE_VARIETY to 0, DifficultyAxis.KEY_SPREAD to 0),
            axesOf(PlacementCalculator.compute(200, 0.0, 3, 4.0)),
        )
    }

    @Test
    fun `tonal memory span 4 or above starts TEMPO_DENSITY at 1, below 4 starts it at 0`() {
        val below = PlacementCalculator.compute(100, 0.0, 3, 4.0)
        val atFour = PlacementCalculator.compute(100, 0.0, 4, 4.0)
        assertEquals(0, below.initialAxisLevels.getValue(DifficultyAxis.TEMPO_DENSITY))
        assertEquals(1, atFour.initialAxisLevels.getValue(DifficultyAxis.TEMPO_DENSITY))
    }

    @Test
    fun `REGISTER_SPREAD and OCTAVE_DISPLACE always start at 0 - not part of the M0 table`() {
        val placement = PlacementCalculator.compute(15, 0.0, 5, 4.0)
        assertEquals(0, placement.initialAxisLevels.getValue(DifficultyAxis.REGISTER_SPREAD))
        assertEquals(0, placement.initialAxisLevels.getValue(DifficultyAxis.OCTAVE_DISPLACE))
    }

    private fun axesOf(placement: PlacementCalculator.Placement) =
        placement.initialAxisLevels.filterKeys {
            it in setOf(DifficultyAxis.CADENCE_FADE, DifficultyAxis.TIMBRE_VARIETY, DifficultyAxis.KEY_SPREAD)
        }
}
