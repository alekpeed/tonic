package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 2.4's acceptance criterion — docs/20-PHASE-2-SPEC.md: "degree labeling consistent across all
 * three minor forms."
 *
 * That sentence is the whole reason §2.1 chose to treat harmonic and melodic minor as *alterations of
 * natural minor* rather than as separate scales. Under the rejected design, degree 6 and degree 7 would
 * each mean one thing in natural minor and something else in the other two forms, and a learner would be
 * maintaining three competing label systems for one key. These tests are what makes that a checked
 * property rather than a stated intention.
 */
class MinorFormsTest {
    private fun degreesOf(skill: com.tonic.core.model.ids.SkillId) = SkillGraph.activeDegreesFor(skill)

    @Test
    fun `each minor form adds to the previous one and takes nothing away`() {
        val natural = degreesOf(SkillIds.M10_MIN_NATURAL)
        val harmonic = degreesOf(SkillIds.M10_MIN_HARMONIC)
        val melodic = degreesOf(SkillIds.M10_MIN_MELODIC)

        assertTrue(natural.all { it in harmonic }, "harmonic minor keeps every natural-minor degree")
        assertTrue(harmonic.all { it in melodic }, "melodic minor keeps every harmonic-minor degree")

        assertEquals(setOf(ScaleDegree(7)), harmonic - natural, "harmonic adds the raised 7 and nothing else")
        assertEquals(setOf(ScaleDegree(6)), melodic - harmonic, "melodic adds the raised 6 and nothing else")
    }

    @Test
    fun `a label means one pitch in every minor form - the acceptance criterion`() {
        // The property under test, stated directly: take every degree that appears in more than one
        // form, and confirm it denotes the same interval above the tonic in all of them. Were the forms
        // modeled as separate scales, "7" would be 10 semitones in natural minor and 11 in harmonic.
        val forms =
            listOf(
                SkillIds.M10_MIN_NATURAL,
                SkillIds.M10_MIN_HARMONIC,
                SkillIds.M10_MIN_MELODIC,
            ).map { degreesOf(it) }

        val labelToSemitones = mutableMapOf<String, MutableSet<Int>>()
        for (form in forms) {
            for (degree in form) {
                labelToSemitones
                    .getOrPut(degree.canonicalLabel) { mutableSetOf() }
                    .add(degree.semitoneOffset(Mode.MINOR))
            }
        }

        for ((label, semitones) in labelToSemitones) {
            assertEquals(
                1,
                semitones.size,
                "label \"$label\" denotes $semitones across the three minor forms - it must denote exactly one",
            )
        }
        // Sanity: the labels that actually distinguish the forms are present and distinct.
        assertEquals(setOf(10), labelToSemitones.getValue("b7"))
        assertEquals(setOf(11), labelToSemitones.getValue("7"))
        assertEquals(setOf(8), labelToSemitones.getValue("b6"))
        assertEquals(setOf(9), labelToSemitones.getValue("6"))
    }

    @Test
    fun `the raised degrees really are one semitone above their flat siblings`() {
        assertEquals(
            ScaleDegree(7, -1).semitoneOffset(Mode.MINOR) + 1,
            ScaleDegree(7).semitoneOffset(Mode.MINOR),
            "harmonic minor's leading tone is ♭7 raised by one",
        )
        assertEquals(
            ScaleDegree(6, -1).semitoneOffset(Mode.MINOR) + 1,
            ScaleDegree(6).semitoneOffset(Mode.MINOR),
        )
    }

    @Test
    fun `every minor form generates items whose target is drawn from its own set`() {
        for (skill in listOf(SkillIds.M10_MIN_NATURAL, SkillIds.M10_MIN_HARMONIC, SkillIds.M10_MIN_MELODIC)) {
            val allowed = degreesOf(skill)
            repeat(60) { i ->
                val item =
                    M2ItemGenerator
                        .generate(
                            skill,
                            DifficultyAxis.RECOGNITION_AXES.associateWith { 0 },
                            seed = 6_000L + i,
                        ).item
                assertTrue(
                    item.targetDegree in allowed,
                    "${skill.raw} produced ${item.targetDegree.canonicalLabel}, which is not in its set",
                )
                assertEquals(Mode.MINOR, item.mode)
            }
        }
    }

    @Test
    fun `harmonic minor puts two different answers in one scale position`() {
        // The fact that broke the ladder: ♭7 and ♮7 are both answers and both sit at slot 7. Recorded
        // here as a curriculum fact so the UI constraint it implies cannot be forgotten.
        val harmonic = degreesOf(SkillIds.M10_MIN_HARMONIC)
        val atSlotSeven = harmonic.filter { it.degree == 7 }

        assertEquals(2, atSlotSeven.size, "harmonic minor has two degrees at scale position 7")
        assertEquals(
            atSlotSeven.size,
            atSlotSeven.map { it.canonicalLabel }.toSet().size,
            "and they must carry different labels, or the attempt log conflates them",
        )
    }

    @Test
    fun `only the last node of each chain triggers an independence check`() {
        // docs/20-PHASE-2-SPEC.md §3: minor gets its own check. Before this it was hardcoded to M2, so
        // minor could be mastered without ever being asked to hold a key unaided.
        assertTrue(SkillGraph.triggersIndependenceCheck(SkillIds.M2_FULL_DIATONIC))
        assertTrue(SkillGraph.triggersIndependenceCheck(SkillIds.M10_MIN_MELODIC))

        assertTrue(!SkillGraph.triggersIndependenceCheck(SkillIds.M10_MIN_NATURAL))
        assertTrue(!SkillGraph.triggersIndependenceCheck(SkillIds.M10_MIN_HARMONIC))
        assertTrue(!SkillGraph.triggersIndependenceCheck(SkillIds.M2_DEG_SET_4))
    }
}
