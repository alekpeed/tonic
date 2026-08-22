package com.tonic.core.model.music

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * docs/30-PHASE-3-SPEC.md Stage 3.1: "Octave-agnostic verified", "Ambiguity-band decision implemented
 * per §5.2", and "'Unclear' never scores as wrong" — the half of those that is pure music theory.
 */
class DegreeResolverTest {
    private val tonicC = PitchClass(0)
    private val fullDiatonic = ScaleDegree.ALL_DIATONIC

    /** Frequency of a pitch [semitones] above the given tonic, in the octave starting at MIDI [octaveBase]. */
    private fun hzAboveTonic(
        semitones: Int,
        octaveBase: Int = 60,
        centsOffset: Double = 0.0,
    ): Double = Tuning.offsetByCents(Tuning.midiToHz(octaveBase + semitones), centsOffset)

    @Test
    fun `resolves each diatonic degree to itself`() {
        for (degree in fullDiatonic) {
            val answer =
                DegreeResolver.resolve(
                    frequencyHz = hzAboveTonic(degree.semitoneOffset(Mode.MAJOR)),
                    tonic = tonicC,
                    mode = Mode.MAJOR,
                    alphabet = fullDiatonic,
                )
            assertIs<SungAnswer.Resolved>(answer, "degree $degree was not resolved")
            assertEquals(degree, answer.degree)
            assertTrue(abs(answer.centsFromDegree) < 1.0, "expected an exact hit, got ${answer.centsFromDegree}")
        }
    }

    /**
     * §3 mitigation 3: singing a degree in whatever octave is comfortable must be correct, because
     * forcing a register tests vocal range rather than hearing. Checked two octaves either side of the
     * reference — wider than any real voice would stray, precisely so that no octave window can be
     * lurking.
     */
    @Test
    fun `is octave agnostic across four octaves`() {
        for (degree in fullDiatonic) {
            for (octaveBase in listOf(36, 48, 60, 72, 84)) {
                val answer =
                    DegreeResolver.resolve(
                        frequencyHz = hzAboveTonic(degree.semitoneOffset(Mode.MAJOR), octaveBase),
                        tonic = tonicC,
                        mode = Mode.MAJOR,
                        alphabet = fullDiatonic,
                    )
                assertIs<SungAnswer.Resolved>(answer)
                assertEquals(degree, answer.degree, "degree $degree misread when sung from MIDI $octaveBase")
            }
        }
    }

    /**
     * The wrap case, pinned because getting it wrong manufactures a *plausible* error rather than an
     * obvious one. A learner asked for the tonic who lands 40 cents flat sits near the top of the octave.
     * Compared without wrapping, the nearest degree is 7 — and 7↔1 is a real, expected confusion pair
     * (docs/07-ADAPTIVE-ENGINE.md §4), so the bug would deposit convincing fake entries into the
     * confusion matrix and look like a genuine diagnosis of the learner.
     */
    @Test
    fun `a flat tonic stays the tonic instead of wrapping to seven`() {
        val answer =
            DegreeResolver.resolve(
                frequencyHz = hzAboveTonic(0, centsOffset = -40.0),
                tonic = tonicC,
                mode = Mode.MAJOR,
                alphabet = fullDiatonic,
            )
        assertIs<SungAnswer.Resolved>(answer)
        assertEquals(ScaleDegree(1), answer.degree)
        assertTrue(answer.centsFromDegree < 0.0, "a flat note should report negative cents")
        assertTrue(abs(answer.centsFromDegree + 40.0) < 1.0, "expected about -40 cents, got ${answer.centsFromDegree}")
    }

    /** The same wrap in the other direction: sharp of 7 is still 7, not the tonic. */
    @Test
    fun `a sharp leading tone stays the leading tone`() {
        val answer =
            DegreeResolver.resolve(
                frequencyHz = hzAboveTonic(11, centsOffset = 40.0),
                tonic = tonicC,
                mode = Mode.MAJOR,
                alphabet = fullDiatonic,
            )
        assertIs<SungAnswer.Resolved>(answer)
        assertEquals(ScaleDegree(7), answer.degree)
        assertTrue(abs(answer.centsFromDegree - 40.0) < 1.0)
    }

    /**
     * The ambiguity band, decided as re-prompt rather than confirm — see [UnclearReason.AMBIGUOUS_BETWEEN_DEGREES].
     * Degrees 3 and 4 sit one semitone apart in major, so the exact midpoint is 50 cents from each.
     */
    @Test
    fun `refuses a pitch sitting exactly between two adjacent degrees`() {
        val answer =
            DegreeResolver.resolve(
                frequencyHz = hzAboveTonic(4, centsOffset = 50.0),
                tonic = tonicC,
                mode = Mode.MAJOR,
                alphabet = fullDiatonic,
            )
        assertIs<SungAnswer.Unclear>(answer, "a coin-flip between 3 and 4 must not be silently rounded")
        assertEquals(UnclearReason.AMBIGUOUS_BETWEEN_DEGREES, answer.reason)
    }

    /**
     * The other side of that band, and the more important one: §3 requires generous tolerance, so the
     * refusal must be rare. A singer 30 cents flat of degree 3 — a real miss, not a coin-flip — is still
     * given their answer.
     */
    @Test
    fun `commits when one degree is clearly nearer, even on an imprecise singer`() {
        val answer =
            DegreeResolver.resolve(
                frequencyHz = hzAboveTonic(4, centsOffset = -30.0),
                tonic = tonicC,
                mode = Mode.MAJOR,
                alphabet = fullDiatonic,
            )
        assertIs<SungAnswer.Resolved>(answer, "a 30-cent miss is imprecision, not ambiguity")
        assertEquals(ScaleDegree(3), answer.degree)
    }

    /**
     * Only the active alphabet is answerable. A learner on `M2.DEG_SET_1` cannot be told they sang a 6,
     * and the widened tolerance that follows from a sparse alphabet is correct rather than a bug: with
     * only 1, 3 and 5 available, everything must land on one of them.
     */
    @Test
    fun `only degrees in the active alphabet are candidates`() {
        val triad = ScaleDegree.TONIC_TRIAD
        val answer =
            DegreeResolver.resolve(
                frequencyHz = hzAboveTonic(3), // an E-flat, which is not in the triad at all
                tonic = tonicC,
                mode = Mode.MAJOR,
                alphabet = triad,
            )
        assertIs<SungAnswer.Resolved>(answer)
        assertEquals(ScaleDegree(3), answer.degree, "nearest triad member to 3 semitones is degree 3")
        assertTrue(abs(answer.centsFromDegree + 100.0) < 1.0, "and it should report being a full semitone flat")
    }

    /** Minor reads the same degree labels against a different set — docs/20-PHASE-2-SPEC.md §2.1. */
    @Test
    fun `resolves natural minor degrees against a minor tonic`() {
        for (degree in ScaleDegree.ALL_NATURAL_MINOR) {
            val answer =
                DegreeResolver.resolve(
                    frequencyHz = hzAboveTonic(degree.semitoneOffset(Mode.MINOR), octaveBase = 57),
                    tonic = PitchClass(9),
                    mode = Mode.MINOR,
                    alphabet = ScaleDegree.ALL_NATURAL_MINOR,
                )
            assertIs<SungAnswer.Resolved>(answer, "minor degree $degree was not resolved")
            assertEquals(degree, answer.degree)
        }
    }

    /** A single-degree alphabet has no runner-up, so nothing can be ambiguous against it. */
    @Test
    fun `a single-degree alphabet always commits`() {
        val answer =
            DegreeResolver.resolve(
                frequencyHz = hzAboveTonic(6),
                tonic = tonicC,
                mode = Mode.MAJOR,
                alphabet = setOf(ScaleDegree(1)),
            )
        assertIs<SungAnswer.Resolved>(answer)
        assertEquals(ScaleDegree(1), answer.degree)
    }
}
