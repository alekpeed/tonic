package com.tonic.core.curriculum.sampling

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalancedSamplerTest {
    @Test
    fun `excludes a candidate that would exceed the max window frequency`() {
        // 4 candidates, window 20 -> expected 5 each, max allowed 7.5. Stuff "A" to 7 in the trailing window.
        val recent = List(19) { if (it < 7) "A" else "B" }
        val random = Random(1)
        repeat(20) {
            val pick = BalancedSampler.pick(listOf("A", "B", "C", "D"), recent, random)
            assertTrue(pick != "A", "A should be excluded once it's already at the cap")
        }
    }

    @Test
    fun `relaxes the constraint rather than stalling when every candidate would be excluded`() {
        val recent = listOf("A", "A", "A") // tiny window, "A" is the only candidate
        val random = Random(2)
        val pick = BalancedSampler.pick(listOf("A"), recent, random)
        assertEquals("A", pick)
    }

    @Test
    fun `weights bias selection without ever selecting a zero-weight candidate`() {
        val random = Random(3)
        val picks =
            (1..200).map {
                BalancedSampler.pick(
                    listOf("A", "B"),
                    emptyList(),
                    random,
                    weights =
                        mapOf(
                            "A" to 1.0,
                            "B" to 0.0,
                        ),
                )
            }
        assertTrue(picks.all { it == "A" })
    }

    @Test
    fun `with no history and uniform weights, both candidates eventually appear`() {
        val random = Random(4)
        val picks = (1..50).map { BalancedSampler.pick(listOf("A", "B"), emptyList(), random) }.toSet()
        assertEquals(setOf("A", "B"), picks)
    }
}
