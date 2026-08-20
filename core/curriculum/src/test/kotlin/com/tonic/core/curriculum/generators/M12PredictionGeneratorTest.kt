package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `M12` — docs/20-PHASE-2-SPEC.md §2.3. The item that runs backwards. */
class M12PredictionGeneratorTest {
    private fun axes(
        gap: Int = 0,
        deviation: Int = 0,
    ) = mapOf(DifficultyAxis.PREDICT_GAP to gap, DifficultyAxis.PREDICT_DEVIATION to deviation)

    private fun items(
        skill: SkillId,
        count: Int,
        gap: Int = 0,
        deviation: Int = 0,
        seedBase: Long = 3_000L,
    ) = buildList {
        var history = GenerationHistory()
        repeat(count) { i ->
            val result = M12ItemGenerator.generate(skill, axes(gap, deviation), seed = seedBase + i, history = history)
            history = result.updatedHistory
            add(result.item)
        }
    }

    @Test
    fun `generation is deterministic`() {
        val a = M12ItemGenerator.generate(SkillIds.M12_PREDICT_DIATONIC, axes(), seed = 77L).item
        val b = M12ItemGenerator.generate(SkillIds.M12_PREDICT_DIATONIC, axes(), seed = 77L).item
        assertEquals(a, b)
    }

    @Test
    fun `the gap lengths are the spec's four, in order`() {
        // docs/20-PHASE-2-SPEC.md §2.3's table, verbatim. Pinned because these are the axis: a wrong
        // number here is not a styling detail, it is a different exercise.
        assertEquals(listOf(1_000L, 2_000L, 3_500L, 5_000L), (0..3).map(M12ItemGenerator::gapMs))
        for (level in 0..3) {
            val item = M12ItemGenerator.generate(SkillIds.M12_PREDICT_TRIAD, axes(gap = level), seed = 5L).item
            assertEquals(M12ItemGenerator.gapMs(level), item.gapBeforeSoundedNoteMs)
        }
    }

    @Test
    fun `matched and mismatched items are close to balanced`() {
        // A drifting ratio would reward guessing the commoner answer, and while d-prime is invariant to
        // response bias, PredictionMasteryEvaluator's 85% accuracy criterion is not.
        val generated = items(SkillIds.M12_PREDICT_DIATONIC, count = 200)
        val matched = generated.count { it.matches }
        assertTrue(
            abs(matched - generated.size / 2) <= generated.size / 5,
            "$matched of ${generated.size} items matched - too far from even to keep accuracy honest",
        )
    }

    @Test
    fun `a matching item sounds exactly the pitch it named`() {
        for (item in items(SkillIds.M12_PREDICT_DIATONIC, count = 60).filter { it.matches }) {
            assertEquals(item.statedMidi, item.soundedMidi)
            assertEquals(0.0, item.soundedCentsOffset)
            assertEquals(AnswerAlphabet.MatchDirection.MATCHED, item.correctLabel)
        }
    }

    @Test
    fun `each deviation level departs the way the spec says it does`() {
        // §2.3: adjacent diatonic degree -> same degree wrong octave -> chromatic neighbor -> same
        // degree detuned 30 cents. Each is checked against what it actually produced, because "the
        // deviation got harder" is the only thing this axis means and there is no other way to see it.
        val level0 = items(SkillIds.M12_PREDICT_DIATONIC, 80, deviation = 0).filterNot { it.matches }
        assertTrue(level0.isNotEmpty())
        for (item in level0) {
            assertEquals(0.0, item.soundedCentsOffset, "level 0 substitutes a note, it does not bend one")
            val sounded = item.soundedMidi - (item.statedMidi - item.statedDegree.semitoneOffset(item.mode))
            assertTrue(
                item.activeDegrees.any { it.semitoneOffset(item.mode) == sounded },
                "level 0's substitute must be a degree the learner has a button for, not an unseen note",
            )
        }

        val level1 = items(SkillIds.M12_PREDICT_DIATONIC, 80, deviation = 1).filterNot { it.matches }
        assertTrue(level1.isNotEmpty())
        for (item in level1) {
            assertEquals(12, abs(item.soundedMidi - item.statedMidi), "level 1 is the right note, wrong octave")
        }

        val level2 = items(SkillIds.M12_PREDICT_DIATONIC, 80, deviation = 2).filterNot { it.matches }
        assertTrue(level2.isNotEmpty())
        for (item in level2) {
            assertEquals(1, abs(item.soundedMidi - item.statedMidi), "level 2 is a semitone away")
        }

        val level3 = items(SkillIds.M12_PREDICT_DIATONIC, 80, deviation = 3).filterNot { it.matches }
        assertTrue(level3.isNotEmpty())
        for (item in level3) {
            assertEquals(item.statedMidi, item.soundedMidi, "level 3 is the right note, bent")
            assertEquals(30.0, abs(item.soundedCentsOffset))
        }
    }

    @Test
    fun `a detuned deviation still resolves to a direction rather than collapsing to matched`() {
        // The trap in level 3: the MIDI numbers are equal, so a naive comparison would call a bent note
        // a match and score every honest answer wrong. correctLabel compares in cents for this reason.
        val bent = items(SkillIds.M12_PREDICT_DIATONIC, 80, deviation = 3).filterNot { it.matches }
        assertTrue(bent.isNotEmpty())
        for (item in bent) {
            assertTrue(
                item.correctLabel != AnswerAlphabet.MatchDirection.MATCHED,
                "a 30-cent bend of ${item.statedDegree.canonicalLabel} was scored as a match",
            )
            val expected =
                if (item.soundedCentsOffset < 0) {
                    AnswerAlphabet.MatchDirection.TOO_LOW
                } else {
                    AnswerAlphabet.MatchDirection.TOO_HIGH
                }
            assertEquals(expected, item.correctLabel)
        }
    }

    @Test
    fun `every node states a degree from its own pool, in its own mode`() {
        val expected =
            mapOf(
                SkillIds.M12_PREDICT_TRIAD to (ScaleDegree.TONIC_TRIAD to Mode.MAJOR),
                SkillIds.M12_PREDICT_DIATONIC to (ScaleDegree.ALL_DIATONIC to Mode.MAJOR),
                SkillIds.M12_PREDICT_MINOR to (ScaleDegree.ALL_NATURAL_MINOR to Mode.MINOR),
                SkillIds.M12_PREDICT_CHROMATIC to (ScaleDegree.ALL_CHROMATIC to Mode.MAJOR),
            )
        for ((skill, spec) in expected) {
            val (pool, mode) = spec
            assertEquals(pool, SkillGraph.activeDegreesFor(skill))
            for (item in items(skill, count = 40)) {
                assertEquals(mode, item.mode)
                assertTrue(item.statedDegree in pool, "${item.statedDegree.canonicalLabel} is outside $skill's pool")
                assertEquals(item.statedMidi - item.statedDegree.semitoneOffset(mode), 60 + item.key.value)
            }
        }
    }

    @Test
    fun `the key is fully established on every item`() {
        // M12 does not use CADENCE_FADE at all (docs/20-PHASE-2-SPEC.md §4). A faded reference would
        // make a wrong answer ambiguous between "could not audiate" and "lost the key", which is the
        // confound docs/07-ADAPTIVE-ENGINE.md §3 exists to prevent.
        for (item in items(SkillIds.M12_PREDICT_DIATONIC, count = 30)) {
            assertTrue(
                item.referencePlan.elements.isNotEmpty(),
                "an audiation item with no key established is asking the learner to hold nothing",
            )
        }
    }

    @Test
    fun `only the introductory node forgives a wrong direction`() {
        // docs/20-PHASE-2-SPEC.md §8.1 decision 3, as a curriculum fact rather than a UI one.
        assertTrue(!SkillGraph.scoresDirection(SkillIds.M12_PREDICT_TRIAD))
        assertTrue(SkillGraph.scoresDirection(SkillIds.M12_PREDICT_DIATONIC))
        assertTrue(SkillGraph.scoresDirection(SkillIds.M12_PREDICT_MINOR))
        assertTrue(SkillGraph.scoresDirection(SkillIds.M12_PREDICT_CHROMATIC))
    }

    @Test
    fun `prediction nodes are scheduled over prediction axes only`() {
        // The property that keeps an M12 node from being handed CADENCE_FADE - an axis its items do not
        // have, whose staircase would have been measuring nothing.
        for (skill in SkillIds.M12_NODES_IN_ORDER) {
            assertEquals(DifficultyAxis.Scope.PREDICTION, SkillGraph.scopeFor(skill))
        }
        for (skill in listOf(SkillIds.M2_DEG_SET_1, SkillIds.M10_MIN_NATURAL, SkillIds.M11_CHROM_FULL)) {
            assertEquals(DifficultyAxis.Scope.RECOGNITION, SkillGraph.scopeFor(skill))
        }
    }
}
