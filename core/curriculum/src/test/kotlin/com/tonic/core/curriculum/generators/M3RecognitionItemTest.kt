package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.RhythmMode
import com.tonic.core.model.rhythm.ClickAccent
import com.tonic.core.model.rhythm.RhythmFigure
import com.tonic.core.model.rhythm.RhythmQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The recognition half of `M3` — docs/40-PHASE-4-SPEC.md §3.3 and §5.1, Stage 4.4.
 *
 * Two shapes of question, because `M3.DOWNBEAT` genuinely asks something else: the other three ask
 * "which of these did you just hear", and that one asks where the bar turned over.
 */
class M3RecognitionItemTest {
    private val patternChoiceNodes =
        listOf(SkillIds.M3_BEAT_DIV_RECOG, SkillIds.M3_SUBDIV_RECOG, SkillIds.M3_SYNCOPATION_RECOG)

    private fun levels(vararg pairs: Pair<DifficultyAxis, Int>) = pairs.toMap()

    private fun generate(
        skill: SkillId,
        axes: Map<DifficultyAxis, Int> = emptyMap(),
        seed: Long = 11L,
    ) = M3ItemGenerator.generate(skill, axes, seed)

    @Test
    fun `every recognition node asks a recognition question`() {
        for (skill in patternChoiceNodes + SkillIds.M3_DOWNBEAT) {
            val item = generate(skill)
            assertEquals(RhythmMode.RECOGNITION, item.mode, "$skill should be a recognition node")
            assertTrue(item.answerAlphabet.labels.isNotEmpty(), "$skill must offer something to answer with")
        }
    }

    @Test
    fun `distractors differ from the answer at exactly one beat`() {
        // The design that makes the confusion matrix meaningful: a learner who picks wrongly has
        // mistaken one specific figure for another, and that pair is what gets recorded. Distractors
        // generated independently would differ in several places at once and the pair would be
        // undefined - which is how this started, and why two of three choices could share a label.
        for (skill in patternChoiceNodes) {
            for (seed in 1L..25L) {
                val q = generate(skill, levels(DifficultyAxis.TIMING_TOLERANCE to 3), seed).question
                assertIs<RhythmQuestion.WhichPattern>(q)
                val answer = q.choices[q.answerIndex]
                val beats = answer.bars * answer.meter.beatsPerBar
                for ((index, choice) in q.choices.withIndex()) {
                    if (index == q.answerIndex) continue
                    val differing =
                        (0 until beats).count {
                            RhythmFigure.signatureAt(choice, it) != RhythmFigure.signatureAt(answer, it)
                        }
                    assertEquals(1, differing, "$skill seed $seed: a distractor differs at $differing beats")
                }
            }
        }
    }

    @Test
    fun `every choice carries a distinct figure label`() {
        // Without this the attempt log cannot say which rhythm the learner picked, and the matrix
        // accumulates cells that mean nothing.
        for (skill in patternChoiceNodes) {
            for (seed in 1L..25L) {
                val q = generate(skill, levels(DifficultyAxis.TIMING_TOLERANCE to 3), seed).question
                assertIs<RhythmQuestion.WhichPattern>(q)
                assertEquals(
                    q.figureSignatures.size,
                    q.figureSignatures.toSet().size,
                    "$skill seed $seed: duplicate labels ${q.figureSignatures}",
                )
            }
        }
    }

    @Test
    fun `the figures offered come from the node's own alphabet`() {
        // SkillGraph declares what a node teaches, and the mastery evaluator judges coverage against
        // that declaration. A generator offering a figure outside it would make coverage unreachable.
        for (skill in patternChoiceNodes) {
            val alphabet = SkillGraph.activeFiguresFor(skill)
            for (seed in 1L..25L) {
                val q = generate(skill, seed = seed).question
                assertIs<RhythmQuestion.WhichPattern>(q)
                assertTrue(
                    q.figureSignatures.all { it in alphabet },
                    "$skill seed $seed offered ${q.figureSignatures}, alphabet is $alphabet",
                )
            }
        }
    }

    @Test
    fun `a recog node offers distinct patterns to choose between`() {
        // Two identical choices would have no right answer at all.
        for (skill in patternChoiceNodes) {
            for (seed in 1L..30L) {
                val q = generate(skill, levels(DifficultyAxis.RHYTHMIC_DENSITY to 2), seed).question
                assertIs<RhythmQuestion.WhichPattern>(q, "$skill should ask which pattern")
                val onsets = q.choices.map { it.onsetTicks }
                assertEquals(onsets.size, onsets.toSet().size, "$skill offered duplicate choices at seed $seed")
            }
        }
    }

    @Test
    fun `the answer is one of the choices, and the label points at it`() {
        for (skill in patternChoiceNodes) {
            for (seed in 1L..25L) {
                val item = generate(skill, seed = seed)
                val q = item.question
                assertIs<RhythmQuestion.WhichPattern>(q)
                assertEquals(q.choices[q.answerIndex], item.pattern, "$skill's answer is not the item's pattern")
                // The label is the *figure* the answer sounds at the beat the choices diverge at, not
                // its position - docs/40-PHASE-4-SPEC.md §8. A position means a different rhythm in
                // every item, so a confusion matrix over positions would say nothing.
                assertEquals(q.figureSignatures[q.answerIndex], item.correctLabel)
                assertEquals(q.answerIndex + 1, q.positionOf(item.correctLabel), "the screen must still find it")
            }
        }
    }

    @Test
    fun `the answer does not sit in the same position every time`() {
        // A node whose answer is always first teaches a strategy rather than a skill - the same reason
        // M9 draws its major/minor answer from a coin.
        val positions =
            (1L..60L).map {
                val q = generate(SkillIds.M3_SUBDIV_RECOG, seed = it).question
                assertIs<RhythmQuestion.WhichPattern>(q)
                q.answerIndex
            }
        assertTrue(positions.toSet().size > 1, "the answer was always at position ${positions.first()}")
    }

    @Test
    fun `tolerance level widens the choice, up to what the node teaches`() {
        // A recognition item has no timing tolerance of its own, so the axis reads as breadth of choice
        // instead - otherwise the scheduler would be moving a level that changed nothing for half the
        // module. Three is the ceiling: §3.3's own wording is "which of these three patterns".
        //
        // But a node can only offer as many rhythms as it teaches. M3.SUBDIV_RECOG's alphabet is
        // exactly `ta` and `ta-ka-di-mi` - beat against subdivision, which is the whole node - so it
        // stays at two however high the axis goes. Syncopation teaches four figures and does reach
        // three. Inventing a third figure for a two-figure node would change what the node teaches.
        fun choices(
            skill: SkillId,
            level: Int,
        ) = (1L..20L).map {
            val q = generate(skill, levels(DifficultyAxis.TIMING_TOLERANCE to level), it).question
            assertIs<RhythmQuestion.WhichPattern>(q)
            q.choices.size
        }

        assertTrue(choices(SkillIds.M3_SYNCOPATION_RECOG, 0).all { it == 2 }, "level 0 should offer two")
        assertTrue(choices(SkillIds.M3_SYNCOPATION_RECOG, 3).all { it == 3 }, "level 3 should offer three")
        assertTrue(
            choices(SkillIds.M3_SUBDIV_RECOG, 3).all { it == 2 },
            "a two-figure node cannot offer three rhythms",
        )
    }

    @Test
    fun `no node offers more choices than it has figures`() {
        for (skill in patternChoiceNodes) {
            val alphabet = SkillGraph.activeFiguresFor(skill)
            for (seed in 1L..20L) {
                val q = generate(skill, levels(DifficultyAxis.TIMING_TOLERANCE to 3), seed).question
                assertIs<RhythmQuestion.WhichPattern>(q)
                assertTrue(
                    q.choices.size <= alphabet.size,
                    "$skill offered ${q.choices.size} choices from an alphabet of ${alphabet.size}",
                )
            }
        }
    }

    @Test
    fun `downbeat items ask where one was, among the beats heard`() {
        for (seed in 1L..40L) {
            val item = generate(SkillIds.M3_DOWNBEAT, levels(DifficultyAxis.PATTERN_LENGTH to 1), seed)
            val q = item.question
            assertIs<RhythmQuestion.WhichBeatIsOne>(q, "M3.DOWNBEAT should ask which beat is one")
            assertEquals(item.pattern.bars * item.meter.beatsPerBar, q.beatsHeard)
            assertTrue(q.downbeatPosition in 1..q.beatsHeard)
        }
    }

    @Test
    fun `the downbeat is not always the first beat heard`() {
        // The whole design. §3.4 trains "hearing where one is" in music that does not announce it; an
        // item that always began on the downbeat would have the same answer every time.
        val positions =
            (1L..60L).map {
                val q = generate(SkillIds.M3_DOWNBEAT, seed = it).question
                assertIs<RhythmQuestion.WhichBeatIsOne>(q)
                q.downbeatPosition
            }
        assertTrue(positions.toSet().size > 1, "the downbeat was always at ${positions.first()}")
        assertTrue(positions.any { it != 1 }, "playback always started on the downbeat")
        // And it *is* sometimes the first, so "never the first" is not a strategy either.
        assertTrue(positions.any { it == 1 }, "the downbeat was never the first beat in 60 seeds")
    }

    @Test
    fun `a downbeat item never accents the downbeat`() {
        // The accent is the answer. A metronome that stresses "one" has handed it over before the
        // question is asked.
        for (level in 0..3) {
            for (seed in 1L..20L) {
                val item = generate(SkillIds.M3_DOWNBEAT, levels(DifficultyAxis.METRONOME_FADE to level), seed)
                assertTrue(
                    item.metronomePlan.clicks.none { it.accent == ClickAccent.DOWNBEAT },
                    "fade $level at seed $seed accents the downbeat on a DOWNBEAT item",
                )
            }
        }
    }

    @Test
    fun `other rhythm nodes keep their downbeat accent`() {
        // The removal is specific to M3.DOWNBEAT, not a general loss of the accent - a metronome with
        // no downbeat is a metronome that stops telling anyone where the bar is.
        val item = generate(SkillIds.M3_BEAT_DIV_RECOG, levels(DifficultyAxis.METRONOME_FADE to 1))
        assertTrue(item.metronomePlan.clicks.any { it.accent == ClickAccent.DOWNBEAT })
    }

    @Test
    fun `recognition items are deterministic, like every other item`() {
        for (skill in patternChoiceNodes + SkillIds.M3_DOWNBEAT) {
            val axes = levels(DifficultyAxis.RHYTHMIC_DENSITY to 2, DifficultyAxis.TIMING_TOLERANCE to 2)
            assertEquals(generate(skill, axes, 99L), generate(skill, axes, 99L), "$skill is not reproducible")
        }
    }

    @Test
    fun `the graph agrees with the generator about which nodes are heard`() {
        // One source of truth. A generator that decided this for itself would drift from the graph the
        // scheduler reads, and the two would disagree about what kind of item a node produces.
        for (node in SkillGraph.m3Nodes) {
            val expected = SkillGraph.rhythmModeFor(node.id)
            assertEquals(expected, generate(node.id).mode, "${node.id} disagrees with the graph")
        }
    }
}
