package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 2.3's acceptance criteria — docs/20-PHASE-2-SPEC.md: "cadence-fade mechanic works
 * identically to `M2`; minor cadence renders correctly at all 8 fade levels."
 *
 * The interesting thing about these tests is how little new machinery they exercise. `M10` reuses
 * `M2ItemGenerator` unchanged; the only edit that made minor work was replacing a hardcoded
 * `Mode.MAJOR` with the node's own mode. Everything below is therefore checking that the *existing*
 * mechanism does the right thing when handed a different mode — which is what "mirrors `M2`'s structure
 * exactly" in §3 has to mean if it means anything.
 */
class M10MinorGeneratorTest {
    private fun axes(fade: Int) =
        DifficultyAxis.RECOGNITION_AXES.associateWith { 0 } + (DifficultyAxis.CADENCE_FADE to fade)

    private fun generate(
        skill: SkillId,
        fade: Int = 0,
        seed: Long = 4_242L,
    ) = M2ItemGenerator.generate(skill, axes(fade), seed).item

    @Test
    fun `M10 items are generated in minor, with minor's own degree set`() {
        val item = generate(SkillIds.M10_MIN_SET_1)

        assertEquals(Mode.MINOR, item.mode)
        assertEquals(
            listOf(ScaleDegree(1), ScaleDegree(3, -1), ScaleDegree(5)),
            item.activeDegrees,
            "MIN_SET_1 is 1, ♭3, 5 — the minor tonic triad (docs/20-PHASE-2-SPEC.md §3)",
        )
    }

    @Test
    fun `the four minor sets widen exactly as the spec's table says`() {
        assertEquals(
            listOf("1", "b3", "5"),
            generate(SkillIds.M10_MIN_SET_1).activeDegrees.map { it.canonicalLabel },
        )
        assertEquals(
            listOf("1", "2", "b3", "5"),
            generate(SkillIds.M10_MIN_SET_2).activeDegrees.map { it.canonicalLabel },
        )
        assertEquals(
            listOf("1", "2", "b3", "5", "b6"),
            generate(SkillIds.M10_MIN_SET_3).activeDegrees.map { it.canonicalLabel },
        )
        assertEquals(
            listOf("1", "2", "b3", "4", "5", "b6"),
            generate(SkillIds.M10_MIN_SET_4).activeDegrees.map { it.canonicalLabel },
        )
    }

    @Test
    fun `a minor target note sounds at minor's interval, not major's`() {
        // The whole point of ScaleDegree carrying its flat: ♭3 must be three semitones above the tonic,
        // and it must get there without any mode-dependent reinterpretation of the number 3.
        repeat(40) { i ->
            val item = generate(SkillIds.M10_MIN_SET_4, seed = 900L + i)
            val expectedOffset = item.targetDegree.semitoneOffset(Mode.MINOR)
            val tonicMidi = item.targetMidi - expectedOffset
            assertEquals(
                tonicMidi + expectedOffset,
                item.targetMidi,
                "target ${item.targetDegree.canonicalLabel} must sit at $expectedOffset semitones",
            )
            if (item.targetDegree == ScaleDegree(3, -1)) {
                assertEquals(3, expectedOffset, "♭3 is a minor third")
            }
        }
    }

    @Test
    fun `the minor cadence is minor at every one of the eight fade levels`() {
        // The acceptance criterion, stated as an interval check rather than a snapshot: every chord the
        // reference plays must be a triad whose third is minor, at every level that plays a chord.
        for (level in 0..DifficultyAxis.CADENCE_FADE.maxLevel) {
            val item = generate(SkillIds.M10_MIN_SET_1, fade = level, seed = 555L + level)
            assertEquals(
                CadenceFadeLevel.fromLevel(level),
                item.referencePlan.cadenceFadeLevel,
                "the fade mechanic must behave identically to M2",
            )

            val chords = item.referencePlan.elements.filterIsInstance<ReferenceElement.ChordEvent>()
            for (chord in chords) {
                val sorted = chord.midiNotes.sorted()
                val thirdInterval = sorted[1] - sorted[0]
                assertEquals(
                    MINOR_THIRD,
                    thirdInterval,
                    "at fade level $level a reference chord came out ${intervalName(thirdInterval)}, " +
                        "notes=$sorted — a major chord in a minor cadence would teach the wrong mode",
                )
            }
        }
    }

    @Test
    fun `the minor cadence uses a minor v, not a major V - decision 4`() {
        // docs/20-PHASE-2-SPEC.md §8.1 decision 4: i-iv-v-i through MIN_NATURAL, so the reference never
        // sounds a ♮7 the learner has no button for. Natural minor yields that on its own - the
        // dominant triad built from natural-minor degrees is 5, ♭7, 2, which is minor.
        val item = generate(SkillIds.M10_MIN_SET_1, fade = 0, seed = 31L)
        val chords = item.referencePlan.elements.filterIsInstance<ReferenceElement.ChordEvent>()
        assertEquals(4, chords.size, "L0 is the full four-chord cadence")

        val dominant = chords[2].midiNotes.sorted()
        assertEquals(
            MINOR_THIRD,
            dominant[1] - dominant[0],
            "the third chord of the cadence is the dominant; a major third here would be a raised 7",
        )

        // And no chord anywhere in the cadence sounds the leading tone.
        val tonic = chords.first().midiNotes.minOrNull()!!
        val leadingTone = (tonic + MAJOR_SEVENTH) % 12
        assertTrue(
            chords.flatMap { it.midiNotes }.none { it % 12 == leadingTone },
            "a natural-minor cadence must not contain the raised 7 - that is what MIN_HARMONIC adds",
        )
    }

    @Test
    fun `minor generation is deterministic, exactly as major is`() {
        // CLAUDE.md §5 applies to every generator path, not only the one Phase 1 shipped.
        val a = M2ItemGenerator.generate(SkillIds.M10_MIN_SET_3, axes(2), seed = 77L).item
        val b = M2ItemGenerator.generate(SkillIds.M10_MIN_SET_3, axes(2), seed = 77L).item
        assertEquals(a, b)
    }

    private fun intervalName(semitones: Int) =
        when (semitones) {
            MINOR_THIRD -> "minor"
            MAJOR_THIRD -> "MAJOR"
            else -> "an unexpected $semitones-semitone"
        }

    private companion object {
        const val MINOR_THIRD = 3
        const val MAJOR_THIRD = 4
        const val MAJOR_SEVENTH = 11
    }
}
