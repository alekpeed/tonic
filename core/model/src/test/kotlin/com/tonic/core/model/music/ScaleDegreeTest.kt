package com.tonic.core.model.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** docs/09-BUILD-PLAN.md Stage 1 acceptance: degree <-> semitone mapping for major mode. */
class ScaleDegreeTest {
    @Test
    fun `major mode semitone offsets match the diatonic scale`() {
        val expected = mapOf(1 to 0, 2 to 2, 3 to 4, 4 to 5, 5 to 7, 6 to 9, 7 to 11)
        expected.forEach { (degree, semitones) ->
            assertEquals(semitones, ScaleDegree(degree).semitoneOffset(Mode.MAJOR), "degree $degree")
        }
    }

    @Test
    fun `alteration shifts the offset`() {
        assertEquals(3, ScaleDegree(3, alteration = -1).semitoneOffset(Mode.MAJOR))
        assertEquals(5, ScaleDegree(4, alteration = 0).semitoneOffset(Mode.MAJOR))
    }

    @Test
    fun `degree out of 1 to 7 is rejected`() {
        assertFailsWith<IllegalArgumentException> { ScaleDegree(0) }
        assertFailsWith<IllegalArgumentException> { ScaleDegree(8) }
    }

    @Test
    fun `tonic triad is degrees 1 3 5`() {
        assertEquals(setOf(1, 3, 5), ScaleDegree.TONIC_TRIAD.map { it.degree }.toSet())
    }

    @Test
    fun `all diatonic is degrees 1 through 7`() {
        assertEquals((1..7).toSet(), ScaleDegree.ALL_DIATONIC.map { it.degree }.toSet())
    }
}
