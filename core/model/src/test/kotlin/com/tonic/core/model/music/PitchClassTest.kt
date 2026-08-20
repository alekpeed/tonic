package com.tonic.core.model.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PitchClassTest {
    @Test
    fun `rejects out of range values`() {
        assertFailsWith<IllegalArgumentException> { PitchClass(-1) }
        assertFailsWith<IllegalArgumentException> { PitchClass(12) }
    }

    @Test
    fun `plus wraps around the octave`() {
        assertEquals(PitchClass(0), PitchClass(11) + 1)
        assertEquals(PitchClass(11), PitchClass(0) + -1)
        assertEquals(PitchClass(2), PitchClass(11) + 3)
    }
}
