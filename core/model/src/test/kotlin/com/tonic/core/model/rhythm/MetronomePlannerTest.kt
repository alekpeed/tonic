package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md's Stage 4.2 acceptance, in as many words: "All 8 `METRONOME_FADE` levels
 * render correctly."
 *
 * Every level gets its own assertion rather than a loop over a table, because the table *is* the
 * curriculum (§3.2) and a loop that encoded it would only be asserting the planner against a second
 * copy of itself.
 */
class MetronomePlannerTest {
    private val meter = Meter.FOUR_FOUR
    private val bars = 2
    private val beat = Meter.TICKS_PER_BEAT

    private fun plan(level: MetronomeFadeLevel) = MetronomePlanner.plan(level, meter, bars)

    @Test
    fun `L0 clicks every subdivision, throughout`() {
        val under = plan(MetronomeFadeLevel.L0).underPattern
        // Two bars of four beats, each beat split in two: sixteen clicks, one every six ticks.
        assertEquals(16, under.size)
        assertEquals((0 until 96 step 6).toList(), under.map { it.tick })
        assertEquals(2, under.count { it.accent == ClickAccent.DOWNBEAT })
        assertEquals(8, under.count { it.accent == ClickAccent.SUBDIVISION })
    }

    @Test
    fun `L1 clicks every beat, throughout`() {
        val under = plan(MetronomeFadeLevel.L1).underPattern
        assertEquals((0 until 96 step beat).toList(), under.map { it.tick })
        assertTrue(under.none { it.accent == ClickAccent.SUBDIVISION }, "L1 is beats, not subdivisions")
    }

    @Test
    fun `L2 clicks beat one of each bar only`() {
        val under = plan(MetronomeFadeLevel.L2).underPattern
        assertEquals(listOf(0, 48), under.map { it.tick })
        assertTrue(under.all { it.accent == ClickAccent.DOWNBEAT })
    }

    @Test
    fun `L3 counts in two bars and keeps going`() {
        val p = plan(MetronomeFadeLevel.L3)
        assertEquals(8, p.countIn.size, "two bars of four")
        assertEquals((0 until 96 step beat).toList(), p.underPattern.map { it.tick })
    }

    @Test
    fun `L4 counts in two bars and then stops - the critical transition`() {
        // §3.2: "L4 is the critical transition - the first level where the learner must keep time
        // unaided." If anything ever sounds under the pattern here, the level has stopped meaning what
        // it says and the mastery criterion built on it (§5.3 criterion 4) is measuring nothing.
        val p = plan(MetronomeFadeLevel.L4)
        assertEquals(8, p.countIn.size)
        assertTrue(p.underPattern.isEmpty(), "nothing may sound under the pattern from L4 up")
    }

    @Test
    fun `L5 counts in one bar, then silence`() {
        val p = plan(MetronomeFadeLevel.L5)
        assertEquals(4, p.countIn.size)
        assertTrue(p.underPattern.isEmpty())
    }

    @Test
    fun `L6 counts in two beats, then silence, and gives away no bar position`() {
        val p = plan(MetronomeFadeLevel.L6)
        assertEquals(2, p.countIn.size)
        assertEquals(listOf(-24, -12), p.countIn.map { it.tick })
        assertTrue(p.underPattern.isEmpty())
        // A two-beat count-in is the tail of a bar, not a bar. Marking either click as a downbeat would
        // hand back the bar position L6 exists to make the learner hold.
        assertTrue(p.countIn.none { it.accent == ClickAccent.DOWNBEAT }, "L6 must not reveal where one is")
    }

    @Test
    fun `L7 has no clicks at all, and says so`() {
        val p = plan(MetronomeFadeLevel.L7)
        assertTrue(p.clicks.isEmpty())
        assertTrue(p.announcesTempoAtBlockStart, "L7 states tempo at block start - it is not simply silent")
    }

    @Test
    fun `only L7 announces the tempo at block start`() {
        for (level in MetronomeFadeLevel.entries.filter { it != MetronomeFadeLevel.L7 }) {
            assertFalse(
                MetronomePlanner.plan(level, meter, bars).announcesTempoAtBlockStart,
                "$level carries its own count-in and must not defer to a block announcement",
            )
        }
    }

    @Test
    fun `support thins within each half of the ladder`() {
        // Asserted in two runs rather than one, because §3.2's ladder is not monotonic across the join
        // and this test is not the place to pretend otherwise. L0-L2 thin the metronome under the
        // pattern (subdivisions, beats, downbeats); L3-L7 shorten the count-in and then remove it. But
        // L3 sounds *beats* under the pattern, which is more than L2's downbeats - so total clicks rise
        // at the L2-to-L3 step. See `the L2 to L3 step adds support back` below.
        val thinning = listOf(MetronomeFadeLevel.L0, MetronomeFadeLevel.L1, MetronomeFadeLevel.L2)
        val underCounts = thinning.map { plan(it).underPattern.size }
        assertEquals(underCounts.sortedDescending(), underCounts, "L0-L2 should thin: $underCounts")

        val fading =
            listOf(
                MetronomeFadeLevel.L3,
                MetronomeFadeLevel.L4,
                MetronomeFadeLevel.L5,
                MetronomeFadeLevel.L6,
                MetronomeFadeLevel.L7,
            )
        val totals = fading.map { plan(it).clicks.size }
        assertEquals(totals.sortedDescending(), totals, "L3-L7 should thin: $totals")
    }

    @Test
    fun `the L2 to L3 step adds support back`() {
        // Not an assertion that this is right - an assertion that it is what the spec's table says, so
        // the discontinuity is visible rather than buried. §3.2 gives L2 "beat 1 of each bar only" and
        // L3 "metronome continues under the pattern", and a metronome that continues on every beat is
        // denser than one on downbeats alone. A learner climbing this axis therefore gets *more*
        // external pulse at L3 than at L2, which is the one place the ladder stops being a fade.
        //
        // Flagged for the maintainer rather than resolved here: reading L3's "continues" as continuing
        // at L2's density would make the ladder monotonic, but it is a change to the pedagogical
        // centerpiece and CLAUDE.md §2 rule 4 says to ask rather than guess.
        assertTrue(
            plan(MetronomeFadeLevel.L3).underPattern.size > plan(MetronomeFadeLevel.L2).underPattern.size,
            "recorded so that changing L3's density is a deliberate act, not an accident",
        )
    }

    @Test
    fun `the metronome stops when the fade says it does, at every level`() {
        // §7.3's warning generalized: the failure Phase 1 actually hit was a crutch silently
        // re-supplied. Asserted for all eight rather than only the four that stop.
        for (level in MetronomeFadeLevel.entries) {
            val sounds = plan(level).underPattern.isNotEmpty()
            assertEquals(level.soundsUnderPattern, sounds, "$level disagrees with its own soundsUnderPattern")
        }
    }

    @Test
    fun `every count-in beat runs up to the downbeat`() {
        // Laid out backwards from zero for this reason. Counting forwards from a negative start puts
        // the gap in the wrong place for any count that is not a whole number of bars - L6 exactly.
        //
        // The *last beat* rather than the last click: at L0 the subdivisions keep going after it, so the
        // final count-in click is a subdivision at -6. That is correct and wanted - a subdivision
        // metronome that fell silent for the last half-beat before the downbeat would be the one moment
        // it stopped doing its job.
        for (level in MetronomeFadeLevel.entries) {
            val beats = plan(level).countIn.filter { it.accent != ClickAccent.SUBDIVISION }
            if (beats.isEmpty()) continue
            assertEquals(-beat, beats.last().tick, "$level's count-in does not run up to the downbeat")
        }
    }

    @Test
    fun `L0 subdivides its count-in too, right up to the downbeat`() {
        val countIn = plan(MetronomeFadeLevel.L0).countIn
        assertEquals(-6, countIn.last().tick, "the half-beat before the downbeat should still click")
        assertEquals(ClickAccent.SUBDIVISION, countIn.last().accent)
    }

    @Test
    fun `clicks never land outside the pattern they accompany`() {
        for (level in MetronomeFadeLevel.entries) {
            val total = bars * meter.ticksPerBar
            assertTrue(
                plan(level).underPattern.all { it.tick in 0 until total },
                "$level clicks past the end of the pattern",
            )
        }
    }

    @Test
    fun `a three-four bar counts in three, not four`() {
        // The meter is data, not an assumption. A count-in hardcoded to four would be wrong here and
        // would teach the learner the wrong place for "one".
        val p = MetronomePlanner.plan(MetronomeFadeLevel.L5, Meter.THREE_FOUR, bars = 2)
        assertEquals(3, p.countIn.size)
        assertEquals(listOf(-36, -24, -12), p.countIn.map { it.tick })
    }

    @Test
    fun `a compound beat subdivides in three at L0`() {
        val compound = Meter(beatsPerBar = 2, division = Meter.COMPOUND)
        val under = MetronomePlanner.plan(MetronomeFadeLevel.L0, compound, bars = 1).underPattern
        // Two beats, each in three: six clicks, four of them subdivisions.
        assertEquals(6, under.size)
        assertEquals(4, under.count { it.accent == ClickAccent.SUBDIVISION })
        assertEquals(listOf(0, 4, 8, 12, 16, 20), under.map { it.tick })
    }
}
