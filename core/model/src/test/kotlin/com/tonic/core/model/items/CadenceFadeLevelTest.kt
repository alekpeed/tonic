package com.tonic.core.model.items

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CadenceFadeLevelTest {
    @Test
    fun `fromLevel round trips 0 through 7`() {
        for (level in 0..7) {
            assertEquals(level, CadenceFadeLevel.fromLevel(level).level)
        }
    }

    @Test
    fun `fromLevel rejects out of range`() {
        assertFailsWith<IllegalArgumentException> { CadenceFadeLevel.fromLevel(-1) }
        assertFailsWith<IllegalArgumentException> { CadenceFadeLevel.fromLevel(8) }
    }

    @Test
    fun `mastery minimum is L4`() {
        assertEquals(CadenceFadeLevel.L4, CadenceFadeLevel.MASTERY_MINIMUM)
    }

    @Test
    fun `min and max are L0 and L7`() {
        assertEquals(CadenceFadeLevel.L0, CadenceFadeLevel.MIN)
        assertEquals(CadenceFadeLevel.L7, CadenceFadeLevel.MAX)
    }
}
