package com.tonic.core.curriculum.sampling

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class KeySamplerTest {
    @Test
    fun `blocks a key that already repeated twice in a row`() {
        val recent = listOf(0, 0)
        val random = Random(1)
        repeat(20) {
            val pick = KeySampler.pick(listOf(0, 5, 7), recent, random)
            assertTrue(pick != 0, "key 0 should be blocked after two consecutive picks")
        }
    }

    @Test
    fun `allows the only key when the pool has just one`() {
        val recent = listOf(3, 3)
        val random = Random(2)
        val pick = KeySampler.pick(listOf(3), recent, random)
        assertTrue(pick == 3)
    }

    @Test
    fun `does not block after only one repeat`() {
        val recent = listOf(2)
        val random = Random(3)
        val picks = (1..30).map { KeySampler.pick(listOf(2, 4), recent, random) }.toSet()
        assertTrue(picks.contains(2))
    }
}
