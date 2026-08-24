package com.tonic.core.model

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ItemTiming
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.state.SkillState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The capability Stage 2.0 adds, tested on its own terms. The golden corpus proves Stage 2.0 changed
 * nothing about `M2`; this proves the new surface is actually correct rather than merely present, so
 * Stages 2.2–2.6 build on something verified (docs/20-PHASE-2-SPEC.md).
 */
class Phase2ModelTest {
    @Test
    fun `natural minor degrees carry their flat in the alteration, not in a second table`() {
        // docs/20-PHASE-2-SPEC.md §2.1: natural minor is 1, 2, ♭3, 4, 5, ♭6, ♭7 - and the flattened
        // ones are the diatonic slot plus an alteration, so one label means one pitch relationship in
        // any mode.
        val expected = listOf(0, 2, 3, 5, 7, 8, 10)
        val actual = ScaleDegree.ALL_NATURAL_MINOR.sortedBy { it.degree }.map { it.semitoneOffset(Mode.MINOR) }
        assertEquals(expected, actual)
    }

    @Test
    fun `a degree denotes the same pitch in both modes - only the active set differs`() {
        // The property that makes ♭3-in-minor and ♭3-as-a-chromatic-in-major the same thing.
        for (degree in 1..7) {
            for (alteration in -1..1) {
                val d = ScaleDegree(degree, alteration)
                assertEquals(
                    d.semitoneOffset(Mode.MAJOR),
                    d.semitoneOffset(Mode.MINOR),
                    "$d must denote one pitch relationship regardless of mode",
                )
            }
        }
    }

    @Test
    fun `harmonic and melodic minor are alterations, not modes`() {
        // The raised 7 is labeled 7 and is 11 semitones - the same 7 as in major (§2.1). Were minor a
        // separate table, this would have to be "degree 7 raised" and would collide with major's 7.
        assertEquals(11, ScaleDegree(7).semitoneOffset(Mode.MINOR))
        assertEquals(10, ScaleDegree(7, -1).semitoneOffset(Mode.MINOR))
        assertEquals(9, ScaleDegree(6).semitoneOffset(Mode.MINOR))
        assertEquals(8, ScaleDegree(6, -1).semitoneOffset(Mode.MINOR))
    }

    @Test
    fun `canonicalLabel is byte-identical to the Phase 1 form for every unaltered degree`() {
        // Load-bearing: existing attempt rows stay readable without a migration, and the Stage 2.0
        // golden corpus stays valid.
        for (degree in 1..7) {
            assertEquals(degree.toString(), ScaleDegree(degree).canonicalLabel)
        }
    }

    @Test
    fun `canonicalLabel distinguishes an altered degree from its natural - the collision Phase 1 had`() {
        // Before this existed, labels were degree.toString(), so ♭3 and ♮3 both stored as "3" and would
        // have been conflated in the attempt log and collapsed into one confusion-matrix cell the moment
        // M10 or M11 put both on screen.
        assertEquals("b3", ScaleDegree(3, -1).canonicalLabel)
        assertEquals("3", ScaleDegree(3).canonicalLabel)
        assertEquals("#4", ScaleDegree(4, 1).canonicalLabel)

        val alphabet = AnswerAlphabet.ScaleDegrees(listOf(ScaleDegree(3, -1), ScaleDegree(3)))
        assertEquals(listOf("b3", "3"), alphabet.labels)
        assertEquals(2, alphabet.labels.toSet().size, "two different answers must not share one label")
    }

    @Test
    fun `an alteration beyond a single accidental is rejected`() {
        assertFailsWith<IllegalArgumentException> { ScaleDegree(3, alteration = -2) }
        assertFailsWith<IllegalArgumentException> { ScaleDegree(3, alteration = 2) }
    }

    @Test
    fun `the chromatic introduction order is pull-strength ordered and all five are distinct pitches`() {
        // docs/20-PHASE-2-SPEC.md §2.2 - ♯4 first because the tritone pulls hardest, ♭2 last.
        val order = ScaleDegree.CHROMATIC_INTRODUCTION_ORDER
        assertEquals(5, order.size)
        assertEquals(ScaleDegree(4, 1), order.first())
        assertEquals(ScaleDegree(2, -1), order.last())
        val semitones = order.map { it.semitoneOffset(Mode.MAJOR) }
        assertEquals(semitones.size, semitones.toSet().size, "each chromatic degree is a distinct pitch")
        // None of them collides with an unaltered diatonic degree - that is what makes them chromatic.
        val diatonic = ScaleDegree.ALL_DIATONIC.map { it.semitoneOffset(Mode.MAJOR) }.toSet()
        assertTrue(semitones.none { it in diatonic }, "a chromatic degree must sit between the diatonic ones")
    }

    @Test
    fun `prediction axes are invisible to a recognition node and vice versa`() {
        // The property that makes adding two axes a no-op for M2 rather than a change to it.
        val recognitionState = SkillState.initial(SkillIds.M2_DEG_SET_1)
        assertEquals(DifficultyAxis.RECOGNITION_AXES.toSet(), recognitionState.axisLevels.keys)
        assertTrue(DifficultyAxis.PREDICTION_AXES.none { it in recognitionState.axisLevels.keys })

        val predictionState =
            SkillState.initial(SkillIds.M2_DEG_SET_1, scope = DifficultyAxis.Scope.PREDICTION)
        assertEquals(DifficultyAxis.PREDICTION_AXES.toSet(), predictionState.axisLevels.keys)
    }

    @Test
    fun `a prediction item resolves direction, including a detuned near-match`() {
        assertEquals(AnswerAlphabet.MatchDirection.MATCHED, predictionItem(sounded = 60).correctLabel)
        assertEquals(AnswerAlphabet.MatchDirection.TOO_HIGH, predictionItem(sounded = 62).correctLabel)
        assertEquals(AnswerAlphabet.MatchDirection.TOO_LOW, predictionItem(sounded = 59).correctLabel)

        // PREDICT_DEVIATION level 3: the right degree, bent. The integer MIDI matches, so a naive
        // comparison would call this MATCHED - it is the case that forces cents-level comparison.
        val detunedSharp = predictionItem(sounded = 60, cents = 30.0)
        assertEquals(AnswerAlphabet.MatchDirection.TOO_HIGH, detunedSharp.correctLabel)
        assertFalse(detunedSharp.matches)
        assertEquals(AnswerAlphabet.MatchDirection.TOO_LOW, predictionItem(sounded = 60, cents = -30.0).correctLabel)
    }

    @Test
    fun `direction collapses to a binary for the introductory node and for d-prime`() {
        // docs/20-PHASE-2-SPEC.md §8.1 decision 3: at M12.PREDICT_TRIAD either direction scores simply
        // as "detected a mismatch," and d-prime is computed over this same collapse.
        assertTrue(AnswerAlphabet.MatchDirection.matchedVsNot(AnswerAlphabet.MatchDirection.MATCHED))
        assertFalse(AnswerAlphabet.MatchDirection.matchedVsNot(AnswerAlphabet.MatchDirection.TOO_LOW))
        assertFalse(AnswerAlphabet.MatchDirection.matchedVsNot(AnswerAlphabet.MatchDirection.TOO_HIGH))
        assertEquals(3, AnswerAlphabet.MatchDirection.labels.size)
    }

    private fun predictionItem(
        sounded: Int,
        cents: Double = 0.0,
    ): Item.PredictionItem =
        Item.PredictionItem(
            skill = SkillIds.M2_DEG_SET_1,
            key = PitchClass(0),
            mode = Mode.MAJOR,
            statedDegree = ScaleDegree(1),
            statedMidi = 60,
            soundedMidi = sounded,
            soundedCentsOffset = cents,
            referencePlan = ReferencePlan(CadenceFadeLevel.L0, emptyList()),
            timbre = TimbreId.PURE,
            referenceTimbre = TimbreId.PURE,
            timing = ItemTiming(referenceDurationMs = 600, gapAfterReferenceMs = 500, targetDurationMs = 900),
            gapBeforeSoundedNoteMs = 2_000L,
            activeDegrees = ScaleDegree.TONIC_TRIAD.toList(),
            seed = 1L,
        )
}
