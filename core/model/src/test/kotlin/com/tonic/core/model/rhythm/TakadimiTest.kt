package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §3.1, and the decision recorded there: Takadimi, not Kodály.
 *
 * The property that decides the argument is asserted first — the beat is `ta` in every meter and every
 * division. That is exactly what a note-value system cannot say, and it is why compound meter is
 * coherent here at all.
 */
class TakadimiTest {
    @Test
    fun `the beat is ta, whatever it divides into`() {
        for (parts in Takadimi.supportedDivisions) {
            assertEquals("ta", Takadimi.syllableAt(parts, 0), "the beat should be ta in $parts parts")
        }
    }

    @Test
    fun `simple division and subdivision`() {
        assertEquals(listOf("ta"), Takadimi.forDivision(1))
        assertEquals(listOf("ta", "di"), Takadimi.forDivision(2))
        assertEquals(listOf("ta", "ka", "di", "mi"), Takadimi.forDivision(4))
    }

    @Test
    fun `compound division is named, and it is the case Kodaly fails`() {
        assertEquals(listOf("ta", "ki", "da"), Takadimi.forDivision(3))
        assertEquals(listOf("ta", "va", "ki", "di", "da", "ma"), Takadimi.forDivision(6))
    }

    @Test
    fun `every table has as many syllables as the beat has parts`() {
        for (parts in Takadimi.supportedDivisions) {
            assertEquals(parts, Takadimi.forDivision(parts).size, "$parts parts should have $parts syllables")
        }
    }

    @Test
    fun `no syllable repeats within a beat`() {
        // The syllable is the learner's handle on *where they are*. A repeat inside one beat would name
        // two positions the same and make the system unable to do the one thing it exists for.
        for (parts in Takadimi.supportedDivisions) {
            val table = Takadimi.forDivision(parts)
            assertEquals(table.size, table.toSet().size, "$parts parts repeats a syllable: $table")
        }
    }

    @Test
    fun `an unsupported division is refused, not guessed at`() {
        // A wrong syllable is worse than none, because the learner is being taught to say it.
        assertFailsWith<IllegalArgumentException> { Takadimi.forDivision(5) }
        assertFailsWith<IllegalArgumentException> { Takadimi.forDivision(0) }
        assertFailsWith<IllegalArgumentException> { Takadimi.syllableAt(4, index = 4) }
    }

    @Test
    fun `every supported division divides the tick grid exactly`() {
        // The reason TICKS_PER_BEAT is twelve. A division that left a remainder would put an onset
        // between two ticks, which is the floating-point problem the integer grid exists to avoid.
        for (parts in Takadimi.supportedDivisions) {
            assertTrue(Meter.TICKS_PER_BEAT % parts == 0, "$parts does not divide ${Meter.TICKS_PER_BEAT}")
        }
    }
}
