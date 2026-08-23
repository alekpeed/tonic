package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pattern representation itself — docs/40-PHASE-4-SPEC.md §5, §3.1 and §6.1.
 *
 * Small surface, but everything downstream is measured against it: Stage 4.3 scores taps against these
 * ticks, and §4.4 requires the whole chain to be byte-identical on replay. A pattern that admitted a
 * malformed onset list would push that failure into scoring, where it would look like a timing bug.
 */
class RhythmPatternTest {
    private val beat = Meter.TICKS_PER_BEAT

    @Test
    fun `a bar of plain beats is on beats only`() {
        val p = RhythmPattern(Meter.FOUR_FOUR, bars = 1, onsetTicks = listOf(0, 12, 24, 36))
        assertTrue(p.isOnBeatsOnly)
        assertEquals(4, p.onsetCount)
        assertEquals(48, p.totalTicks)
    }

    @Test
    fun `an onset between beats is not on beats only`() {
        val p = RhythmPattern(Meter.FOUR_FOUR, bars = 1, onsetTicks = listOf(0, 6, 24))
        assertFalse(p.isOnBeatsOnly)
    }

    @Test
    fun `beat index and downbeat are read off the meter, not assumed`() {
        val p = RhythmPattern(Meter.THREE_FOUR, bars = 2, onsetTicks = listOf(0, 12, 36))
        assertEquals(0, p.beatIndexOf(0))
        assertEquals(1, p.beatIndexOf(beat))
        assertEquals(3, p.beatIndexOf(3 * beat))
        assertTrue(p.isDownbeat(0))
        assertFalse(p.isDownbeat(beat), "beat two of a three-four bar is not a downbeat")
        assertTrue(p.isDownbeat(3 * beat), "the second bar starts three beats in, not four")
    }

    @Test
    fun `the finest division is the simplest set of syllables that describes the pattern`() {
        // Takadimi names position within the beat (§3.1), so a pattern of plain beats is "ta ta ta ta"
        // however long it is. Reporting a finer division would ask the learner to say syllables for
        // positions nothing sounded at.
        assertEquals(1, RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 12, 24, 36)).finestDivision)
        assertEquals(2, RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 6, 24)).finestDivision)
        assertEquals(4, RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 3, 6, 24)).finestDivision)
        assertEquals(3, RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 4, 8)).finestDivision)
    }

    @Test
    fun `syllables name position within the beat, and the beat is always ta`() {
        val plain = RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 12, 24, 36))
        assertEquals(listOf("ta", "ta", "ta", "ta"), plain.onsetTicks.map { plain.syllableAt(it) })

        val divided = RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 6, 12, 24))
        assertEquals(listOf("ta", "di", "ta", "ta"), divided.onsetTicks.map { divided.syllableAt(it) })

        val subdivided = RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 3, 6, 9, 12))
        assertEquals(listOf("ta", "ka", "di", "mi", "ta"), subdivided.onsetTicks.map { subdivided.syllableAt(it) })
    }

    @Test
    fun `a malformed pattern is refused rather than carried into scoring`() {
        assertFailsWith<IllegalArgumentException>("no onsets is silence") {
            RhythmPattern(Meter.FOUR_FOUR, 1, emptyList())
        }
        assertFailsWith<IllegalArgumentException>("bars must be positive") {
            RhythmPattern(Meter.FOUR_FOUR, 0, listOf(0))
        }
        assertFailsWith<IllegalArgumentException>("onsets must ascend") {
            RhythmPattern(Meter.FOUR_FOUR, 1, listOf(12, 0))
        }
        assertFailsWith<IllegalArgumentException>("two onsets at one position") {
            RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 0))
        }
        assertFailsWith<IllegalArgumentException>("an onset past the end") {
            RhythmPattern(Meter.FOUR_FOUR, 1, listOf(0, 48))
        }
        assertFailsWith<IllegalArgumentException>("a negative onset") {
            RhythmPattern(Meter.FOUR_FOUR, 1, listOf(-1, 0))
        }
    }
}
