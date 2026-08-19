package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/02-PEDAGOGY.md §3, L6/L7: "No reference per item; key established once at block start." Found
 * unimplemented through live use — L6/L7 items sampled a fresh key per item and played no reference at
 * all, i.e. a bare note in a key the user had never heard, answerable only by chance. These pin the
 * block behavior: one audible key establishment opens each block, the key holds for the whole block,
 * and L7's silent gap grows across it.
 */
class AudiationBlockTest {
    private fun generateRun(
        cadence: CadenceFadeLevel,
        count: Int,
        seedBase: Long = 5_000L,
    ): List<Item.FunctionalRecognitionItem> {
        var history = GenerationHistory()
        val axes = DifficultyAxis.entries.associateWith { 0 } + (DifficultyAxis.CADENCE_FADE to cadence.level)
        return (0 until count).map { i ->
            val result = M2ItemGenerator.generate(SkillIds.M2_DEG_SET_1, axes, seedBase + i, history)
            history = result.updatedHistory
            result.item
        }
    }

    @Test
    fun `an L6 block opens with an audible key establishment and plays nothing per item after`() {
        val items = generateRun(CadenceFadeLevel.L6, 9)
        assertTrue(
            items[0].referencePlan.sequentialDurationMs > 0,
            "the block's first item must actually SOUND the key - 'established once at block start' " +
                "cannot mean established never",
        )
        for (i in 1..7) {
            assertEquals(
                0,
                items[i].referencePlan.elements.size,
                "item $i inside the block plays no reference - that is the audiation demand",
            )
        }
        assertTrue(
            items[8].referencePlan.sequentialDurationMs > 0,
            "item 8 starts the next block and must re-establish audibly",
        )
    }

    @Test
    fun `the key holds for the whole block - a silent key change is an unanswerable item`() {
        val items = generateRun(CadenceFadeLevel.L6, 8)
        val blockKey = items[0].key
        val blockTonic = items[0].targetMidi - items[0].targetDegree.semitoneOffset(items[0].mode)
        for ((i, item) in items.withIndex()) {
            assertEquals(blockKey, item.key, "item $i must stay in the key the block established")
        }
        // The tonic anchor holds too, not just the pitch class.
        val lastTonic = items.last().let { it.targetMidi - it.targetDegree.semitoneOffset(it.mode) }
        assertEquals(blockTonic % 12, lastTonic % 12)
    }

    @Test
    fun `L7's silent gap lengthens across the block`() {
        val items = generateRun(CadenceFadeLevel.L7, 8)
        val gaps = items.map { it.timing.gapAfterReferenceMs }
        for (i in 1 until gaps.size) {
            assertTrue(
                gaps[i] > gaps[i - 1],
                "docs/02-PEDAGOGY.md §3: 'as L6, with the silent gap lengthening across the block' - " +
                    "gaps were $gaps",
            )
        }
    }

    @Test
    fun `a level change closes the block - the establishment is replayed at the new level`() {
        var history = GenerationHistory()
        val l6 = DifficultyAxis.entries.associateWith { 0 } + (DifficultyAxis.CADENCE_FADE to 6)
        val l7 = DifficultyAxis.entries.associateWith { 0 } + (DifficultyAxis.CADENCE_FADE to 7)
        history = M2ItemGenerator.generate(SkillIds.M2_DEG_SET_1, l6, 1L, history).updatedHistory
        val afterChange = M2ItemGenerator.generate(SkillIds.M2_DEG_SET_1, l7, 2L, history)
        assertTrue(
            afterChange.item.referencePlan.sequentialDurationMs > 0,
            "an item at a different level must not silently inherit the old block's open group",
        )
    }

    @Test
    fun `levels below L6 are untouched - every item still carries its own reference`() {
        for (cadence in listOf(CadenceFadeLevel.L0, CadenceFadeLevel.L2, CadenceFadeLevel.L3)) {
            val items = generateRun(cadence, 4)
            assertTrue(
                items.all { it.referencePlan.elements.isNotEmpty() },
                "$cadence items must all carry a per-item reference",
            )
        }
    }
}
