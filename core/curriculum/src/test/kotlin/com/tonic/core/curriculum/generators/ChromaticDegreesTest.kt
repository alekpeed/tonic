package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `M11` — docs/20-PHASE-2-SPEC.md §2.2/§3. The five notes outside the diatonic set, one node at a time.
 */
class ChromaticDegreesTest {
    private val allAxes = DifficultyAxis.RECOGNITION_AXES.associateWith { 0 }

    private fun degreesOf(skill: com.tonic.core.model.ids.SkillId) = SkillGraph.activeDegreesFor(skill)

    @Test
    fun `each node adds exactly one chromatic degree, in pull-strength order`() {
        val chain =
            listOf(
                SkillIds.M11_CHROM_SHARP4 to ScaleDegree(4, 1),
                SkillIds.M11_CHROM_FLAT7 to ScaleDegree(7, -1),
                SkillIds.M11_CHROM_FLAT6 to ScaleDegree(6, -1),
                SkillIds.M11_CHROM_FLAT3 to ScaleDegree(3, -1),
                SkillIds.M11_CHROM_FLAT2 to ScaleDegree(2, -1),
            )

        for ((skill, expected) in chain) {
            assertEquals(
                expected,
                SkillGraph.focusDegreeFor(skill),
                "${skill.raw} must introduce ${expected.canonicalLabel} and nothing else",
            )
        }
    }

    @Test
    fun `the chromatic set is cumulative and ends at all twelve pitches`() {
        // "Introduced against the already-mastered diatonic set, never in isolation" (§2.2) - so every
        // node keeps everything before it.
        val sharp4 = degreesOf(SkillIds.M11_CHROM_SHARP4)
        assertTrue(ScaleDegree.ALL_DIATONIC.all { it in sharp4 }, "the diatonic set is always present")

        val full = degreesOf(SkillIds.M11_CHROM_FULL)
        assertEquals(12, full.size, "all twelve degrees are in play by the end")
        assertEquals(
            12,
            full.map { it.semitoneOffset(Mode.MAJOR) }.toSet().size,
            "and they are twelve distinct pitches - the chromatic scale",
        )
        assertEquals((0..11).toSet(), full.map { it.semitoneOffset(Mode.MAJOR) }.toSet())
    }

    @Test
    fun `the consolidation node introduces nothing, so it has no focus degree`() {
        // CHROM_FULL is judged on the whole set rather than on one degree; the focus criterion reports
        // met and the other five decide.
        assertEquals(
            degreesOf(SkillIds.M11_CHROM_FLAT2),
            degreesOf(SkillIds.M11_CHROM_FULL),
            "CHROM_FULL adds nothing - all twelve are in play by CHROM_FLAT2",
        )
        assertNull(SkillGraph.focusDegreeFor(SkillIds.M11_CHROM_FULL))
    }

    @Test
    fun `the introduced degree is sampled often enough for its mastery criterion to mean something`() {
        // The measured claim behind SkillGraph.degreeWeightsFor. Uniform sampling across twelve degrees
        // yields about 2 attempts per degree in a 30-item window, and the focus criterion needs 5. This
        // asserts the weighting actually clears that bar rather than assuming it does.
        for (skill in listOf(SkillIds.M11_CHROM_SHARP4, SkillIds.M11_CHROM_FLAT2)) {
            val focus = SkillGraph.focusDegreeFor(skill)!!
            // What docs/03-CURRICULUM.md §5.4's balance cap actually permits for one degree here - the
            // same ceiling MasteryEvaluator applies. At 8 active degrees that is the spec's full 5; at
            // 12 it is 3, because no weighting can beat the cap.
            val active = SkillGraph.activeDegreesFor(skill).size
            val required = minOf(FOCUS_ATTEMPTS_REQUIRED, (MASTERY_WINDOW * 1.5 / active).toInt())
            var history = GenerationHistory()
            var focusCount = 0
            repeat(MASTERY_WINDOW) { i ->
                val result = M2ItemGenerator.generate(skill, allAxes, seed = 4_000L + i, history = history)
                history = result.updatedHistory
                if (result.item.targetDegree == focus) focusCount++
            }
            assertTrue(
                focusCount >= required,
                "${skill.raw} gave ${focus.canonicalLabel} only $focusCount of $MASTERY_WINDOW attempts; " +
                    "the focus mastery criterion needs at least $required at $active active degrees",
            )
        }
    }

    @Test
    fun `weighting is confined to M11 - every other node still samples uniformly`() {
        // The property that keeps the Stage 2.0 golden corpus valid. M2 and M10 nodes widen by one
        // degree too, and must not start weighting it.
        assertTrue(SkillGraph.degreeWeightsFor(SkillIds.M2_DEG_SET_2).isEmpty())
        assertTrue(SkillGraph.degreeWeightsFor(SkillIds.M2_FULL_DIATONIC).isEmpty())
        assertTrue(SkillGraph.degreeWeightsFor(SkillIds.M10_MIN_HARMONIC).isEmpty())
        assertTrue(SkillGraph.degreeWeightsFor(SkillIds.M11_CHROM_SHARP4).isNotEmpty())
    }

    @Test
    fun `a chromatic target sounds between the diatonic notes it sits between`() {
        // The actual skill (§2.2): ♯4 is only meaningful as "not 4, not 5". Its pitch must genuinely
        // fall between them.
        val sharp4 = ScaleDegree(4, 1).semitoneOffset(Mode.MAJOR)
        assertTrue(sharp4 > ScaleDegree(4).semitoneOffset(Mode.MAJOR))
        assertTrue(sharp4 < ScaleDegree(5).semitoneOffset(Mode.MAJOR))

        var history = GenerationHistory()
        repeat(40) { i ->
            val result =
                M2ItemGenerator.generate(SkillIds.M11_CHROM_FULL, allAxes, seed = 7_000L + i, history = history)
            history = result.updatedHistory
            val item = result.item
            assertTrue(
                item.targetDegree in degreesOf(SkillIds.M11_CHROM_FULL),
                "${item.targetDegree.canonicalLabel} is outside the node's set",
            )
            assertEquals(Mode.MAJOR, item.mode, "M11 is chromatic degrees *in major*")
        }
    }

    @Test
    fun `chromatic generation is deterministic`() {
        val a = M2ItemGenerator.generate(SkillIds.M11_CHROM_FLAT6, allAxes, seed = 12L).item
        val b = M2ItemGenerator.generate(SkillIds.M11_CHROM_FLAT6, allAxes, seed = 12L).item
        assertEquals(a, b)
    }

    private companion object {
        const val MASTERY_WINDOW = 30
        const val FOCUS_ATTEMPTS_REQUIRED = 5
    }
}
