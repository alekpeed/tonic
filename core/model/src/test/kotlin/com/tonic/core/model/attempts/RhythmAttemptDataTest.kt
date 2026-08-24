package com.tonic.core.model.attempts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a stored tapped attempt can still say about itself — docs/40-PHASE-4-SPEC.md §8 and §5.3.
 *
 * These are the derivations a mastery verdict reads years after the performance, so they are computed
 * from the record rather than carried alongside it: a number frozen into a column can never be
 * corrected, while the lists it comes from can be re-read by any later definition.
 */
class RhythmAttemptDataTest {
    private fun data(
        asynchronies: List<Double?>,
        figures: List<String> = List(asynchronies.size) { "ta" },
        extraTaps: Int = 0,
    ) = RhythmAttemptData(
        tapTimesMs = asynchronies.filterNotNull(),
        calibrationOffsetMs = 0.0,
        toleranceHalfWidthMs = 150.0,
        expectedEventTimesMs = asynchronies.indices.map { it * 600.0 },
        perEventFigures = figures,
        perEventAsynchronyMs = asynchronies,
        extraTaps = extraTaps,
        missedTaps = asynchronies.count { it == null },
    )

    @Test
    fun `pattern accuracy counts what was struck against what was asked or played`() {
        assertEquals(1.0, data(List(4) { 10.0 }).patternAccuracy)
        assertEquals(0.75, data(listOf(10.0, 10.0, 10.0, null)).patternAccuracy)
        // Four events all struck, plus a tap that landed on nothing: five taps for four events.
        assertEquals(0.8, data(List(4) { 10.0 }, extraTaps = 1).patternAccuracy)
    }

    @Test
    fun `a constant offset has no drift`() {
        // §5.3 criterion 3: a constant offset is a calibration artifact, a growing one is a
        // timekeeping failure.
        assertEquals(0.0, data(List(6) { 40.0 }).driftSlope!!, 1e-9)
    }

    @Test
    fun `a growing offset does`() {
        // 30 ms later on each successive beat, 600 ms apart: 0.05 of a beat per beat.
        val drifting = data((0 until 6).map { it * 30.0 })
        assertEquals(0.05, drifting.driftSlope!!, 1e-9)
    }

    @Test
    fun `one matched event is not a trend`() {
        assertNull(data(listOf(10.0, null, null)).driftSlope)
    }

    @Test
    fun `figures are counted by whether every event in them was struck`() {
        val outcomes =
            data(
                asynchronies = listOf(10.0, 10.0, null, 10.0),
                figures = listOf("ta", "ta-di", "ta-di", "ta"),
            ).figureOutcomes()
        assertEquals(2 to 2, outcomes["ta"])
        assertEquals(1 to 2, outcomes["ta-di"])
    }

    @Test
    fun `the per-event lists must line up`() {
        // Entry i of each describes one expected event. A record where they disagree cannot say which
        // tap belongs to which beat, and a plausible-looking verdict computed from it would describe
        // a performance nobody gave.
        val mismatched =
            runCatching {
                RhythmAttemptData(
                    tapTimesMs = listOf(0.0),
                    calibrationOffsetMs = 0.0,
                    toleranceHalfWidthMs = 150.0,
                    expectedEventTimesMs = listOf(0.0, 600.0),
                    perEventFigures = listOf("ta"),
                    perEventAsynchronyMs = listOf(0.0, null),
                    extraTaps = 0,
                    missedTaps = 1,
                )
            }.exceptionOrNull()
        assertTrue(mismatched is IllegalArgumentException, "got $mismatched")
    }
}
