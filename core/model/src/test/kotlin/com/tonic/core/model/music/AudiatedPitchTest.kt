package com.tonic.core.model.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AudiatedPitch]'s arithmetic — docs/30-PHASE-3-SPEC.md §5.4.
 *
 * The type is small, and every one of these cases is a way the sung record could quietly become a
 * lie about the learner. Measuring from the wrong reference, or failing to fold an octave, produces a
 * number that is not obviously wrong at a glance and would be believed by anyone reading the attempt
 * log later — which is the only reason this evidence is collected at all.
 */
class AudiatedPitchTest {
    private val one = ScaleDegree(1)
    private val three = ScaleDegree(3)
    private val five = ScaleDegree(5)

    private fun resolved(
        degree: ScaleDegree,
        cents: Double = 0.0,
    ) = SungAnswer.Resolved(degree = degree, centsFromDegree = cents, frequencyHz = 440.0)

    @Test
    fun `holding the named degree exactly is zero cents out`() {
        val audiated = AudiatedPitch.from(resolved(three), statedDegree = three, mode = Mode.MAJOR)

        assertEquals(0, audiated.centsFromStated)
        assertTrue(audiated.heldStatedDegree)
    }

    /**
     * The measurement §5.4 actually wants, and the one a naive implementation gets wrong.
     *
     * `SungResponseAnalyzer` reports distance from the *nearest* degree, which here is a tidy 12 cents
     * from `5`. Recorded as-is, a learner who audiated the wrong note entirely would appear in the log
     * to have sung almost perfectly. Measured from the degree that was named, the same voice reads as
     * 312 cents out, which is what happened.
     */
    @Test
    fun `holding a different degree is measured from the one that was named`() {
        val audiated = AudiatedPitch.from(resolved(five, cents = 12.0), statedDegree = three, mode = Mode.MAJOR)

        assertEquals(312, audiated.centsFromStated)
        assertFalse(audiated.heldStatedDegree)
        assertEquals(five, audiated.degree)
    }

    @Test
    fun `a degree below the named one reads as negative`() {
        val audiated = AudiatedPitch.from(resolved(one), statedDegree = three, mode = Mode.MAJOR)

        assertEquals(-400, audiated.centsFromStated)
        assertFalse(audiated.heldStatedDegree)
    }

    /**
     * §7's `sung_octave_agnostic`, which defaults to on: the same degree in any octave is the same answer.
     *
     * `7` sits 1100 cents above `1`, so a learner asked for `1` who sings the `7` *below* it — a step
     * down, and the likeliest miss there is — must read as 100 cents flat rather than 1100 sharp. Without
     * the fold this is the most extreme wrong number the type can produce, and it would land on the
     * learner whose ear was closest to right.
     */
    @Test
    fun `an answer near the octave folds to the short way round`() {
        val audiated = AudiatedPitch.from(resolved(ScaleDegree(7)), statedDegree = one, mode = Mode.MAJOR)

        assertEquals(-100, audiated.centsFromStated)
    }

    /**
     * The reason [AudiatedPitch.heldStatedDegree] is degree identity rather than a cent threshold.
     *
     * `M12.PREDICT_TRIAD` draws from `{1, 3, 5}`, so the nearest alphabet member can be a whole tone
     * away and a resolved answer can legitimately carry 150 cents of error while still being the right
     * degree. A closeness test would call this learner wrong about which note they held, when what
     * actually happened is that they held the right one imprecisely — and §3 mitigation 1 forbids that
     * confusion in as many words: this app tests hearing, not vocal accuracy.
     */
    @Test
    fun `an imprecise voice on the right degree still counts as holding it`() {
        val audiated = AudiatedPitch.from(resolved(three, cents = 150.0), statedDegree = three, mode = Mode.MAJOR)

        assertTrue(audiated.heldStatedDegree)
        assertEquals(150, audiated.centsFromStated)
    }

    /** Minor carries its flattening in the degree, so `♭3` is a distinct target rather than a reinterpreted `3`. */
    @Test
    fun `minor degrees are measured against their own semitone positions`() {
        val flatThree = ScaleDegree(3, -1)
        val audiated = AudiatedPitch.from(resolved(flatThree), statedDegree = five, mode = Mode.MINOR)

        assertEquals(-400, audiated.centsFromStated)
        assertFalse(audiated.heldStatedDegree)
    }
}
