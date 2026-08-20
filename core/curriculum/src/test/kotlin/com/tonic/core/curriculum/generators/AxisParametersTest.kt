package com.tonic.core.curriculum.generators

import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AxisParametersTest {
    @Test
    fun `timbre pool grows monotonically and only level 4 allows independent reference timbre`() {
        var previousSize = 0
        for (level in 0..4) {
            val pool = AxisParameters.timbrePool(level)
            assertTrue(pool.size >= previousSize)
            previousSize = pool.size
            assertEquals(level == 4, AxisParameters.independentReferenceTimbre(level))
        }
        assertEquals(TimbreId.entries.toSet(), AxisParameters.timbrePool(4).toSet())
    }

    @Test
    fun `register range levels 0 and 1 are constrained to the phone-speaker-safe band`() {
        assertEquals(55..84, AxisParameters.registerRange(0))
        assertEquals(55..84, AxisParameters.registerRange(1))
    }

    @Test
    fun `register range widens at higher levels`() {
        assertTrue(AxisParameters.registerRange(2).last - AxisParameters.registerRange(2).first > 29)
        assertTrue(
            AxisParameters.registerRange(3).last - AxisParameters.registerRange(3).first >
                AxisParameters.registerRange(2).last - AxisParameters.registerRange(2).first,
        )
    }

    @Test
    fun `octave offsets match docs 03-CURRICULUM section 5-3`() {
        assertEquals(listOf(0), AxisParameters.octaveOffsets(0))
        assertEquals(listOf(-1, 0, 1), AxisParameters.octaveOffsets(1))
        assertEquals(listOf(-2, -1, 0, 1, 2), AxisParameters.octaveOffsets(2))
    }

    @Test
    fun `key pools grow from 3 to 7 to 12 and are nested`() {
        val pool0 = AxisParameters.keyPool(0).toSet()
        val pool1 = AxisParameters.keyPool(1).toSet()
        val pool2 = AxisParameters.keyPool(2).toSet()
        assertEquals(3, pool0.size)
        assertEquals(7, pool1.size)
        assertEquals(12, pool2.size)
        assertTrue(pool0.all { it in pool1 })
        assertTrue(pool1.all { it in pool2 })
    }

    @Test
    fun `tempo timing gets faster at higher levels`() {
        val t0 = AxisParameters.timing(0)
        val t3 = AxisParameters.timing(3)
        assertTrue(t3.referenceDurationMs < t0.referenceDurationMs)
        assertTrue(t3.gapAfterReferenceMs < t0.gapAfterReferenceMs)
        assertTrue(t3.targetDurationMs < t0.targetDurationMs)
    }

    @Test
    fun `out of range levels fail loudly rather than silently clamping`() {
        assertFailsWith<IllegalStateException> { AxisParameters.timbrePool(5) }
        assertFailsWith<IllegalStateException> { AxisParameters.registerRange(4) }
        assertFailsWith<IllegalStateException> { AxisParameters.octaveOffsets(3) }
        assertFailsWith<IllegalStateException> { AxisParameters.timing(4) }
        assertFailsWith<IllegalStateException> { AxisParameters.keyPool(3) }
    }
}
