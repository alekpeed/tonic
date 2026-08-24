package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.ClickAccent
import com.tonic.core.model.rhythm.Meter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `M3.METER_CHANGE` — docs/40-PHASE-4-SPEC.md §5.1: "meter changes mid-pattern". */
class M3MeterChangeTest {
    private fun axes(length: Int = 3) =
        mapOf(
            DifficultyAxis.PATTERN_LENGTH to length,
            DifficultyAxis.RHYTHMIC_DENSITY to 2,
            // L1: a click on every beat, so the accents are visible to a test.
            DifficultyAxis.METRONOME_FADE to 1,
            DifficultyAxis.TEMPO_DEVIATION to 0,
            DifficultyAxis.TIMING_TOLERANCE to 0,
            DifficultyAxis.TIMBRE_VARIETY to 0,
        )

    @Test
    fun `the bar changes length part-way through, and never at the first bar`() {
        for (seed in 1L..30L) {
            val item = M3ItemGenerator.generate(SkillIds.M3_METER_CHANGE, axes(), seed)
            val change = assertNotNull(item.pattern.changesTo, "seed $seed produced no change")
            assertTrue(change.atBar >= 1, "a change at bar 0 is just a pattern in that meter")
            assertTrue(change.atBar < item.pattern.bars, "a change after the end never happens")
            assertTrue(change.meter != item.meter)
        }
    }

    @Test
    fun `the metronome's downbeats move with the change`() {
        // The whole skill. If the accents kept falling on the original bar length the learner would
        // be told the bar had not changed while the rhythm said it had.
        val item = M3ItemGenerator.generate(SkillIds.M3_METER_CHANGE, axes(), seed = 3L)
        val change = assertNotNull(item.pattern.changesTo)

        val accented =
            item.metronomePlan.clicks
                .filter { it.accent == ClickAccent.DOWNBEAT && it.tick >= 0 }
                .map { it.tick }
                .sorted()
        val expected = (0 until item.pattern.bars).map { item.pattern.barStartTick(it) }
        assertEquals(expected, accented, "accents must sit on the real bar starts, change included")

        // And the two halves really are different lengths.
        val firstBar = item.pattern.barStartTick(1) - item.pattern.barStartTick(0)
        val lastBar =
            item.pattern.barStartTick(item.pattern.bars) - item.pattern.barStartTick(item.pattern.bars - 1)
        assertEquals(item.meter.ticksPerBar, firstBar)
        assertEquals(change.meter.ticksPerBar, lastBar)
    }

    @Test
    fun `it changes grouping only, never grouping and division at once`() {
        // Two things changing at once is two skills, and a learner who misses it could not say which
        // one they missed. M3.COMPOUND teaches the division on its own.
        for (seed in 1L..30L) {
            val item = M3ItemGenerator.generate(SkillIds.M3_METER_CHANGE, axes(), seed)
            val change = assertNotNull(item.pattern.changesTo)
            assertEquals(item.meter.division, change.meter.division, "the beat must divide the same way")
        }
    }

    @Test
    fun `every onset still lands inside the pattern, whichever meters it spans`() {
        for (seed in 1L..30L) {
            val item = M3ItemGenerator.generate(SkillIds.M3_METER_CHANGE, axes(), seed)
            assertTrue(item.pattern.onsetTicks.all { it >= 0 && it < item.pattern.totalTicks })
            assertTrue(item.pattern.onsetTicks.all { it % (Meter.TICKS_PER_BEAT / item.meter.division) == 0 })
        }
    }

    @Test
    fun `meter change items are deterministic`() {
        for (seed in 1L..20L) {
            assertEquals(
                M3ItemGenerator.generate(SkillIds.M3_METER_CHANGE, axes(), seed),
                M3ItemGenerator.generate(SkillIds.M3_METER_CHANGE, axes(), seed),
            )
        }
    }
}
