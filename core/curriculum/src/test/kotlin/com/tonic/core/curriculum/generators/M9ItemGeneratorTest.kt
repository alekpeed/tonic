package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ModePresentation
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.music.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `M9` generation — docs/20-PHASE-2-SPEC.md §2.4/§3.
 *
 * The load-bearing assertions here are the interval ones. Stage 2.0 deliberately made
 * `ScaleDegree.semitoneOffset` mode-independent, which means a generator that reached for
 * `ScaleDegree(3)` while holding `Mode.MINOR` would build a **major** triad and the whole module would
 * silently be asking an unanswerable question. Counting semitones is the only way to know it didn't.
 */
class M9ItemGeneratorTest {
    private fun items(
        skill: com.tonic.core.model.ids.SkillId,
        count: Int,
    ): List<Item.ModeIdentificationItem> {
        var history = GenerationHistory()
        return (0 until count).map { i ->
            val result = M9ItemGenerator.generate(skill, seed = 5_000L + i, history = history)
            history = result.updatedHistory
            result.item
        }
    }

    private fun Item.ModeIdentificationItem.chordSemitonesAboveTonic(index: Int): List<Int> {
        val chord = elements.filterIsInstance<ReferenceElement.ChordEvent>()[index]
        return chord.midiNotes.map { it - tonicMidi }
    }

    @Test
    fun `a minor tonic triad has a minor third, a major one has a major third`() {
        // 0-4-7 versus 0-3-7. If Mode.MINOR were being threaded into a mode-independent lookup, both
        // would read 0-4-7 and this module would be asking learners to hear a distinction that was
        // never rendered.
        val items = items(SkillIds.M9_MODE_ID_TRIAD, 40)
        assertTrue(items.any { it.mode == Mode.MAJOR } && items.any { it.mode == Mode.MINOR })

        for (item in items) {
            val expected = if (item.mode == Mode.MAJOR) listOf(0, 4, 7) else listOf(0, 3, 7)
            assertEquals(
                expected,
                item.chordSemitonesAboveTonic(0),
                "${item.mode} tonic triad in key ${item.key.value}",
            )
        }
    }

    @Test
    fun `the minor cadence is all-natural-minor, so every chord discriminates`() {
        // docs/20-PHASE-2-SPEC.md §8.1 decision 4. With a major V the dominant is identical in both
        // modes and a third of the progression would carry no information about the question being
        // asked. i-iv-v-i keeps every chord distinct from its major counterpart.
        val minor = items(SkillIds.M9_MODE_ID_CADENCE, 60).first { it.mode == Mode.MINOR }

        assertEquals(listOf(0, 3, 7), minor.chordSemitonesAboveTonic(0), "i")
        assertEquals(listOf(5, 8, 12), minor.chordSemitonesAboveTonic(1), "iv - minor subdominant")
        assertEquals(listOf(7, 10, 14), minor.chordSemitonesAboveTonic(2), "v - minor dominant, not V")
        assertEquals(listOf(0, 3, 7), minor.chordSemitonesAboveTonic(3), "i")

        val major = items(SkillIds.M9_MODE_ID_CADENCE, 60).first { it.mode == Mode.MAJOR }
        assertEquals(listOf(0, 4, 7), major.chordSemitonesAboveTonic(0), "I")
        assertEquals(listOf(5, 9, 12), major.chordSemitonesAboveTonic(1), "IV")
        assertEquals(listOf(7, 11, 14), major.chordSemitonesAboveTonic(2), "V")
    }

    @Test
    fun `a melodic fragment always sounds the third - the note the answer depends on`() {
        // An item whose audio never contained the distinguishing interval would be unanswerable rather
        // than hard, which is exactly the failure docs/07-ADAPTIVE-ENGINE.md §2a exists to prevent.
        for (item in items(SkillIds.M9_MODE_ID_MELODIC, 40)) {
            val thirdSemitone = if (item.mode == Mode.MAJOR) 4 else 3
            val sounded = item.elements.filterIsInstance<ReferenceElement.ToneEvent>().map { it.midi - item.tonicMidi }
            assertTrue(
                thirdSemitone in sounded,
                "${item.mode} fragment never sounded its third: $sounded",
            )
            assertEquals(0, sounded.first(), "a fragment opens on the tonic so the tonic is unambiguous")
            assertEquals(0, sounded.last(), "and settles back on it")
        }
    }

    @Test
    fun `each node uses its own presentation`() {
        assertEquals(ModePresentation.CADENCE, items(SkillIds.M9_MODE_ID_CADENCE, 1).single().presentation)
        assertEquals(ModePresentation.TONIC_TRIAD, items(SkillIds.M9_MODE_ID_TRIAD, 1).single().presentation)
        assertEquals(ModePresentation.MELODIC_FRAGMENT, items(SkillIds.M9_MODE_ID_MELODIC, 1).single().presentation)
    }

    @Test
    fun `mode is balanced, so guessing one answer cannot beat chance`() {
        // The answer *is* the mode, so a generator that drifted would hand a guesser free accuracy.
        val items = items(SkillIds.M9_MODE_ID_TRIAD, 200)
        val majors = items.count { it.mode == Mode.MAJOR }

        assertTrue(
            majors in 80..120,
            "expected a roughly even split over 200 items, got $majors major",
        )
    }

    @Test
    fun `generation is deterministic given the same seed and history`() {
        // CLAUDE.md §5.
        val first = M9ItemGenerator.generate(SkillIds.M9_MODE_ID_CADENCE, seed = 4_242L)
        val second = M9ItemGenerator.generate(SkillIds.M9_MODE_ID_CADENCE, seed = 4_242L)
        assertEquals(first.item, second.item)
    }

    @Test
    fun `the correct label follows the mode`() {
        for (item in items(SkillIds.M9_MODE_ID_TRIAD, 20)) {
            val expected = if (item.mode == Mode.MAJOR) "MAJOR" else "MINOR"
            assertEquals(expected, item.correctLabel)
            assertTrue(item.correctLabel in item.answerAlphabet.labels)
        }
    }
}
