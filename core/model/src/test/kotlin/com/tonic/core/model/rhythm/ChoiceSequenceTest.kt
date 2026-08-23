package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The layout a recognition item's audio and its metronome plan both read — docs/40-PHASE-4-SPEC.md
 * §3.3.
 *
 * Worth its own tests because two callers depend on it agreeing with itself: the generator plans a
 * metronome across [ChoiceSequence.totalBars], and the renderer lays patterns at
 * [ChoiceSequence.segmentStartTick]. If those two disagreed the symptom would not look like a bug —
 * the clicks would simply drift out of phase with the exercise.
 */
class ChoiceSequenceTest {
    private val ticksPerBar = Meter.FOUR_FOUR.ticksPerBar

    @Test
    fun `the span covers every segment and the rests between them`() {
        // Three segments of two bars, with a bar of rest between each pair: 6 + 2.
        assertEquals(8, ChoiceSequence.totalBars(segmentCount = 3, barsPerSegment = 2))
        // A single segment has no separators at all.
        assertEquals(2, ChoiceSequence.totalBars(segmentCount = 1, barsPerSegment = 2))
    }

    @Test
    fun `the last segment ends exactly where the span does`() {
        // The property the two callers actually depend on: a plan built from totalBars has to reach
        // the end of the last pattern, and no further. A separator counted after the final segment
        // would leave the metronome clicking into silence; one counted short would cut it off.
        for (segments in 1..4) {
            for (bars in 1..2) {
                val lastStart = ChoiceSequence.segmentStartTick(segments - 1, bars, ticksPerBar)
                val lastEnd = lastStart + bars * ticksPerBar
                assertEquals(
                    ChoiceSequence.totalBars(segments, bars) * ticksPerBar,
                    lastEnd,
                    "$segments segments of $bars bars",
                )
            }
        }
    }

    @Test
    fun `segments never overlap, and are separated by a whole bar`() {
        val bars = 2
        val starts = (0 until 4).map { ChoiceSequence.segmentStartTick(it, bars, ticksPerBar) }
        for ((a, b) in starts.zipWithNext()) {
            val gap = b - (a + bars * ticksPerBar)
            assertEquals(ChoiceSequence.SEPARATOR_BARS * ticksPerBar, gap)
            assertTrue(gap > 0, "two patterns with no gap are one longer pattern")
        }
    }
}
