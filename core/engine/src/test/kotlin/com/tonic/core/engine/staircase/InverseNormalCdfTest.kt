package com.tonic.core.engine.staircase

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Checked against well-known standard-normal quantiles. */
class InverseNormalCdfTest {
    @Test
    fun `median is exactly zero`() {
        assertEquals(0.0, InverseNormalCdf.z(0.5), 1e-9)
    }

    @Test
    fun `is antisymmetric around 0-5`() {
        for (p in listOf(0.01, 0.1, 0.3, 0.7, 0.9, 0.99)) {
            assertTrue(abs(InverseNormalCdf.z(p) + InverseNormalCdf.z(1 - p)) < 1e-6, "p=$p")
        }
    }

    @Test
    fun `matches well-known quantiles`() {
        // Phi(1.0) = 0.8413447..., the 97.5th percentile is the famous 1.959964 (used in every 95% CI).
        assertTrue(abs(InverseNormalCdf.z(0.8413447) - 1.0) < 1e-3)
        assertTrue(abs(InverseNormalCdf.z(0.975) - 1.959964) < 1e-3)
        assertTrue(abs(InverseNormalCdf.z(0.025) + 1.959964) < 1e-3)
    }

    @Test
    fun `rejects p outside the open interval 0 to 1`() {
        assertFailsWith<IllegalArgumentException> { InverseNormalCdf.z(0.0) }
        assertFailsWith<IllegalArgumentException> { InverseNormalCdf.z(1.0) }
    }
}
