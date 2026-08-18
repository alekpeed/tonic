package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.curriculum.sampling.BalancedSampler
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** docs/09-BUILD-PLAN.md Stage 3 acceptance. */
class M2ItemGeneratorTest {
    private val allZeroAxes = DifficultyAxis.entries.associateWith { 0 }

    @Test
    fun `10,000 generations with fixed seeds are reproducible`() {
        repeat(10_000) { i ->
            val seed = i.toLong() * 7919L // spread seeds out rather than using consecutive small integers
            val a = M2ItemGenerator.generate(SkillIds.M2_FULL_DIATONIC, allZeroAxes, seed)
            val b = M2ItemGenerator.generate(SkillIds.M2_FULL_DIATONIC, allZeroAxes, seed)
            assertEquals(a.item, b.item, "seed=$seed did not reproduce")
        }
    }

    @Test
    fun `over 20-item windows no degree exceeds 1-5x expected frequency`() {
        val skill = SkillIds.M2_FULL_DIATONIC
        val degreeCount = SkillGraph.activeDegreesFor(skill).size
        var history = GenerationHistory()
        val generated = mutableListOf<Int>()

        var seed = 1L
        repeat(400) {
            val result = M2ItemGenerator.generate(skill, allZeroAxes, seed, history)
            generated += result.item.targetDegree.degree
            history = result.updatedHistory
            seed += 104_729L
        }

        val expectedPerDegree = BalancedSampler.WINDOW_SIZE.toDouble() / degreeCount
        val maxAllowed = expectedPerDegree * BalancedSampler.MAX_FREQUENCY_MULTIPLE

        for (start in 0..(generated.size - BalancedSampler.WINDOW_SIZE)) {
            val window = generated.subList(start, start + BalancedSampler.WINDOW_SIZE)
            val counts = window.groupingBy { it }.eachCount()
            for ((degree, count) in counts) {
                assertTrue(
                    count <= maxAllowed + 1e-9,
                    "window starting at $start: degree $degree appeared $count times (max allowed ~$maxAllowed)",
                )
            }
        }
    }

    @Test
    fun `no (key, degree, octave) triple repeats consecutively`() {
        val skill = SkillIds.M2_DEG_SET_1
        // Widen octave displacement so repeats are actually possible to test against.
        val axes = allZeroAxes + (DifficultyAxis.OCTAVE_DISPLACE to 2)
        var history = GenerationHistory()
        var previous: Triple<Int, Int, Int>? = null
        var seed = 5L

        repeat(500) {
            val result = M2ItemGenerator.generate(skill, axes, seed, history)
            val item = result.item
            val current = Triple(item.key.value, item.targetDegree.degree, item.targetMidi - item.key.value)
            if (previous != null) {
                assertTrue(current != previous, "consecutive repeat of (key, degree, octave-ish) at seed=$seed")
            }
            previous = current
            history = result.updatedHistory
            seed += 97L
        }
    }

    @Test
    fun `KEY_SPREAD constraint - a key never repeats more than twice consecutively`() {
        val skill = SkillIds.M2_DEG_SET_1
        // Smallest key pool (3 keys) - most likely to stress the constraint.
        val axes = allZeroAxes + (DifficultyAxis.KEY_SPREAD to 0)
        var history = GenerationHistory()
        var seed = 11L
        val keys = mutableListOf<Int>()

        repeat(500) {
            val result = M2ItemGenerator.generate(skill, axes, seed, history)
            keys += result.item.key.value
            history = result.updatedHistory
            seed += 131L
        }

        for (i in 2 until keys.size) {
            assertTrue(
                !(keys[i] == keys[i - 1] && keys[i - 1] == keys[i - 2]),
                "key ${keys[i]} repeated three times consecutively ending at index $i",
            )
        }
    }

    @Test
    fun `generated items respect axis bounds for every axis-level combination`() {
        val skill = SkillIds.M2_FULL_DIATONIC
        var combinations = 0
        for (cadence in DifficultyAxis.CADENCE_FADE.levelRange) {
            for (timbreVariety in DifficultyAxis.TIMBRE_VARIETY.levelRange) {
                for (register in DifficultyAxis.REGISTER_SPREAD.levelRange) {
                    for (octave in DifficultyAxis.OCTAVE_DISPLACE.levelRange) {
                        for (tempo in DifficultyAxis.TEMPO_DENSITY.levelRange) {
                            for (keySpread in DifficultyAxis.KEY_SPREAD.levelRange) {
                                val axes =
                                    mapOf(
                                        DifficultyAxis.CADENCE_FADE to cadence,
                                        DifficultyAxis.TIMBRE_VARIETY to timbreVariety,
                                        DifficultyAxis.REGISTER_SPREAD to register,
                                        DifficultyAxis.OCTAVE_DISPLACE to octave,
                                        DifficultyAxis.TEMPO_DENSITY to tempo,
                                        DifficultyAxis.KEY_SPREAD to keySpread,
                                    )
                                val item = M2ItemGenerator.generate(skill, axes, seed = combinations.toLong()).item
                                assertTrue(item.targetMidi in 0..127, "MIDI out of range at $axes: ${item.targetMidi}")
                                assertTrue(item.key.value in 0..11)
                                assertTrue(item.activeDegrees.contains(item.targetDegree))
                                combinations++
                            }
                        }
                    }
                }
            }
        }
        // 8 * 5 * 4 * 3 * 4 * 3 = 5760 combinations, per docs/10-TESTING.md §4: "cheap to test completely rather than sample."
        assertEquals(8 * 5 * 4 * 3 * 4 * 3, combinations)
    }

    @Test
    fun `all degrees in the active set are reachable at every axis configuration`() {
        for (skill in SkillIds.M2_NODES_IN_ORDER) {
            val activeDegrees = SkillGraph.activeDegreesFor(skill)
            val seenDegrees = mutableSetOf<Int>()
            var history = GenerationHistory()
            var seed = 1000L
            // Enough draws that every degree should show up under balanced sampling even for the 7-degree set.
            repeat(200) {
                val result = M2ItemGenerator.generate(skill, allZeroAxes, seed, history)
                seenDegrees += result.item.targetDegree.degree
                history = result.updatedHistory
                seed += 613L
            }
            assertEquals(activeDegrees.map { it.degree }.toSet(), seenDegrees, "not all degrees reachable for $skill")
        }
    }
}
