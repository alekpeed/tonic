package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** docs/40-PHASE-4-SPEC.md §6.2 — the windows, and the clamp that keeps them from overlapping. */
class ToleranceWindowsTest {
    private val meter = Meter.FOUR_FOUR
    private val plainBar =
        RhythmPattern(meter, bars = 1, onsetTicks = (0 until 4).map { it * Meter.TICKS_PER_BEAT })

    @Test
    fun `a window is a fraction of the beat, so it scales with tempo`() {
        // §6.2's first bullet: "50 ms is generous at 60 BPM and impossible at 200 BPM." A fixed window
        // would make TEMPO_DEVIATION a second, unannounced difficulty axis.
        val slow = ToleranceWindows.halfWidthMs(2, beatMs = 1000.0, pattern = plainBar)
        val fast = ToleranceWindows.halfWidthMs(2, beatMs = 300.0, pattern = plainBar)
        assertEquals(slow / fast, 1000.0 / 300.0, 0.0001)
    }

    @Test
    fun `tighter levels give smaller windows, and level zero is the most forgiving`() {
        val widths = (0..3).map { ToleranceWindows.halfWidthMs(it, 600.0, plainBar) }
        assertEquals(widths.sortedDescending(), widths, "windows should narrow as the level rises: $widths")
        assertTrue(widths.first() > widths.last() * 2, "level 0 should be markedly looser than level 3")
    }

    @Test
    fun `crowded events clamp the window rather than letting it overlap`() {
        // §6.2's third bullet, at the density and tempo where it bites. Sixteenths at 140 BPM sit 107 ms
        // apart, and level 0's quarter-beat window is 107 ms on its own.
        val fastBeat = 60_000.0 / 140
        val sixteenths = RhythmPattern(meter, bars = 1, onsetTicks = (0 until 16).map { it * 3 })
        val gap = 3 * (fastBeat / Meter.TICKS_PER_BEAT)

        val requested = ToleranceWindows.beatFractionFor(0) * fastBeat
        val actual = ToleranceWindows.halfWidthMs(0, fastBeat, sixteenths)
        assertTrue(actual < requested, "the window should have been clamped: $actual vs $requested")
        assertTrue(2 * actual <= gap + 1e-9, "clamped windows still overlap: $actual across a gap of $gap")
    }

    @Test
    fun `an uncrowded pattern gets the window its level asked for`() {
        val fastBeat = 60_000.0 / 140
        assertEquals(
            ToleranceWindows.beatFractionFor(3) * fastBeat,
            ToleranceWindows.halfWidthMs(3, fastBeat, plainBar),
            0.0001,
        )
    }

    @Test
    fun `a single-onset pattern has nothing to crowd against`() {
        val single = RhythmPattern(meter, bars = 1, onsetTicks = listOf(0))
        assertEquals(
            ToleranceWindows.beatFractionFor(0) * 600.0,
            ToleranceWindows.halfWidthMs(0, 600.0, single),
            0.0001,
        )
    }

    @Test
    fun `an impossible level or tempo is refused`() {
        assertFailsWith<IllegalArgumentException> { ToleranceWindows.beatFractionFor(-1) }
        assertFailsWith<IllegalArgumentException> { ToleranceWindows.beatFractionFor(4) }
        assertFailsWith<IllegalArgumentException> { ToleranceWindows.halfWidthMs(0, 0.0, plainBar) }
    }
}
