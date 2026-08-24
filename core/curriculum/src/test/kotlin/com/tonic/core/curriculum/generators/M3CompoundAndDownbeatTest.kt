package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.RhythmFigure
import com.tonic.core.model.rhythm.RhythmQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 4.6 — compound meter, and the `M3.DOWNBEAT` defect it fixes.
 *
 * `M3.DOWNBEAT` shipped at Stage 4.4 unanswerable: plain identical beats, the metronome's downbeat
 * accent removed because the accent would be the answer, and a rotation drawn by the generator that
 * never reached the audio. The learner heard N indistinguishable beats and the recorded answer
 * described a rotation nobody had heard. What these assert is that the sound now carries the
 * information the question asks for.
 */
class M3CompoundAndDownbeatTest {
    private fun axes(
        length: Int = 1,
        density: Int = 2,
    ) = mapOf(
        DifficultyAxis.PATTERN_LENGTH to length,
        DifficultyAxis.RHYTHMIC_DENSITY to density,
        DifficultyAxis.METRONOME_FADE to 0,
        DifficultyAxis.TEMPO_DEVIATION to 0,
        DifficultyAxis.TIMING_TOLERANCE to 0,
        DifficultyAxis.TIMBRE_VARIETY to 0,
    )

    @Test
    fun `a downbeat item's bar shape repeats, so there is something to hear it by`() {
        // The information the question needs: the bar's figure comes round once a bar. Without it
        // there is nothing to find, which is what was wrong before.
        for (seed in 1L..40L) {
            val item = M3ItemGenerator.generate(SkillIds.M3_DOWNBEAT, axes(length = 1), seed)
            val perBar = item.meter.ticksPerBar
            val bars = item.pattern.bars
            if (bars < 2) continue

            val byBar =
                (0 until bars).map { bar ->
                    item.pattern.onsetTicks
                        .filter { it / perBar == bar }
                        .map { it % perBar }
                        .toSet()
                }
            assertEquals(
                1,
                byBar.toSet().size,
                "every bar must sound the same figure, or there is no period to find: $byBar",
            )
        }
    }

    @Test
    fun `a downbeat item is not always answered with the first beat`() {
        // A node whose answer is never anything but 1 teaches "always press 1". The rotation is drawn
        // from the seed and is sometimes zero, which is a real case rather than one to exclude.
        val answers =
            (1L..60L).map { seed ->
                val item = M3ItemGenerator.generate(SkillIds.M3_DOWNBEAT, axes(length = 2), seed)
                (item.question as RhythmQuestion.WhichBeatIsOne).downbeatPosition
            }
        assertTrue(answers.toSet().size > 1, "the answer never moved: ${answers.toSet()}")
    }

    @Test
    fun `a compound item divides the beat in three, and names it ta-ki-da`() {
        // §3.1's case for Takadimi, as a property of the generated item: three equal parts of one
        // beat, named by syllables that describe the beat rather than a note value.
        val item = M3ItemGenerator.generate(SkillIds.M3_COMPOUND, axes(), seed = 7L)
        assertTrue(item.meter.isCompound, "M3.COMPOUND must be in a compound meter")

        val figures =
            (0 until item.pattern.bars * item.meter.beatsPerBar)
                .map { RhythmFigure.signatureAt(item.pattern, it) }
                .toSet()
        assertTrue(
            figures.any { it == "ta-ki-da" },
            "a compound item should somewhere fill a beat in three, got $figures",
        )
        assertTrue(
            figures.all { it == "ta" || it == "ta-ki-da" || it == RhythmFigure.SILENT },
            "no figure outside the node's alphabet: $figures",
        )
    }

    @Test
    fun `every onset lands on a whole tick in compound meter`() {
        // The determinism requirement, at the division that would break it. A third of a beat is not
        // representable as a Double; twelve ticks to the beat makes it exact.
        for (seed in 1L..30L) {
            val item = M3ItemGenerator.generate(SkillIds.M3_COMPOUND, axes(length = 3), seed)
            val step = Meter.TICKS_PER_BEAT / Meter.COMPOUND
            assertTrue(
                item.pattern.onsetTicks.all { it % step == 0 },
                "an onset fell between ticks at seed $seed: ${item.pattern.onsetTicks}",
            )
        }
    }

    @Test
    fun `compound and downbeat items are deterministic`() {
        // CLAUDE.md §5, non-negotiable: same seed, same item.
        for (skill in listOf(SkillIds.M3_COMPOUND, SkillIds.M3_DOWNBEAT)) {
            for (seed in 1L..20L) {
                assertEquals(
                    M3ItemGenerator.generate(skill, axes(length = 2), seed),
                    M3ItemGenerator.generate(skill, axes(length = 2), seed),
                    "$skill at seed $seed",
                )
            }
        }
    }
}
