package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** docs/40-PHASE-4-SPEC.md §3.2 and §5.3 criterion 4. */
class MetronomeFadeLevelTest {
    @Test
    fun `levels map to their numbers, and nothing outside the ladder resolves`() {
        for (level in 0..7) {
            assertEquals(level, MetronomeFadeLevel.fromLevel(level).level)
        }
        assertFailsWith<IllegalArgumentException> { MetronomeFadeLevel.fromLevel(-1) }
        assertFailsWith<IllegalArgumentException> { MetronomeFadeLevel.fromLevel(8) }
    }

    @Test
    fun `the ladder runs from L0 to L7`() {
        assertEquals(MetronomeFadeLevel.L0, MetronomeFadeLevel.MIN)
        assertEquals(MetronomeFadeLevel.L7, MetronomeFadeLevel.MAX)
        assertEquals(8, MetronomeFadeLevel.entries.size)
    }

    @Test
    fun `the metronome stops at L4, and never restarts above it`() {
        // §3.2: L4 is "the critical transition - the first level where the learner must keep time
        // unaided". §5.3 criterion 4 builds a mastery requirement on that being true, so it is asserted
        // rather than assumed.
        assertEquals(MetronomeFadeLevel.L4, MetronomeFadeLevel.MASTERY_MINIMUM)
        for (level in MetronomeFadeLevel.entries) {
            if (level.level < MetronomeFadeLevel.L4.level) {
                assertTrue(level.soundsUnderPattern, "$level should still support the learner")
            } else {
                assertFalse(level.soundsUnderPattern, "$level must leave the learner unaided")
            }
        }
    }
}
