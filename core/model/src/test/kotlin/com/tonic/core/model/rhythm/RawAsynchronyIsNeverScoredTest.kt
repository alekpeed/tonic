package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §6.3, which asks for this test by name.
 *
 * "**Never scored:** raw asynchrony magnitude. It's shown to the user as feedback ('you were a little
 * ahead') and used for drift analysis, but it does not enter the staircase or mastery evaluation
 * directly. Same rule and same rationale as `replayCount` in Phase 1 and `sungCents` in Phase 3 — and
 * unlike those two, **this one needs a real test written, since Phase 3 §7's cited precedent was found
 * not to exist.**"
 *
 * So this is not a precaution, it is a debt the spec records. The rule it protects is §6.1's: a learner
 * who taps the correct rhythm slightly loosely is *right, imprecisely*. If how far inside the window
 * they landed could move a score, the app would be measuring precision while claiming to measure
 * perception — and a learner would be marked down for a skill nobody told them was being assessed.
 *
 * The shape of the assertion is the one that can actually fail: take two performances that differ
 * **only** in asynchrony, both inside the window, and require everything the adaptive engine reads to
 * be identical while the feedback the learner sees differs.
 */
class RawAsynchronyIsNeverScoredTest {
    private val beatMs = 600.0
    private val pattern =
        RhythmPattern(Meter.FOUR_FOUR, bars = 1, onsetTicks = (0 until 4).map { it * Meter.TICKS_PER_BEAT })

    private fun score(
        taps: List<Double>,
        toleranceLevel: Int = 0,
    ) = RhythmScorer.scoreRelative(pattern, taps, beatMs, toleranceLevel)

    private val exact = listOf(0.0, 600.0, 1200.0, 1800.0)
    private val loose = listOf(-38.0, 631.0, 1171.0, 1842.0)

    @Test
    fun `a loose but correct performance scores exactly as a precise one`() {
        val precise = score(exact)
        val sloppy = score(loose)

        assertEquals(precise.isCorrect, sloppy.isCorrect)
        assertEquals(precise.patternAccuracy, sloppy.patternAccuracy)
        assertEquals(precise.matchedCount, sloppy.matchedCount)
        assertEquals(precise.missedCount, sloppy.missedCount)
        assertEquals(precise.extraCount, sloppy.extraCount)
        assertTrue(sloppy.isCorrect, "both performances hit every event inside the window")
    }

    @Test
    fun `the asynchronies themselves do differ, because the learner is shown them`() {
        // The other half of the claim. If this passed by the asynchronies being equal too, the test
        // above would be asserting nothing at all - which is exactly how the Phase 3 precedent §6.3
        // cites turned out not to exist.
        val precise = score(exact).matches.map { it.asynchronyMs }
        val sloppy = score(loose).matches.map { it.asynchronyMs }
        assertNotEquals(precise, sloppy, "the feedback must distinguish what the score does not")
        assertTrue(sloppy.all { it != null && it != 0.0 })
    }

    @Test
    fun `magnitude does not decide correctness anywhere inside the window`() {
        // Swept across the window rather than sampled at two points, so a threshold hidden anywhere
        // inside it fails here. Only the edge should matter, and the edge is the window itself.
        val halfWidth = ToleranceWindows.halfWidthMs(0, beatMs, pattern)
        var offset = -halfWidth + 1.0
        while (offset < halfWidth - 1.0) {
            val shifted = exact.map { it + offset }
            val s = score(shifted)
            assertTrue(s.isCorrect, "an offset of $offset inside a $halfWidth window scored incorrect")
            assertEquals(1.0, s.patternAccuracy, "an offset of $offset changed pattern accuracy")
            offset += 5.0
        }
    }

    @Test
    fun `drift is allowed to read asynchrony, and is the only thing that may`() {
        // §6.3 permits exactly one use beyond feedback: drift analysis. §5.3 criterion 3 is what needs
        // it, and it reads the *trend* rather than the magnitude - which is why a uniformly offset
        // learner drifts zero while a rushing one does not, even though the rushing learner's average
        // error may be smaller.
        val uniformlyLate = score(exact.map { it + 35.0 })
        assertEquals(0.0, uniformlyLate.driftSlope ?: 1.0, 0.0001)

        val rushing = score(listOf(0.0, 585.0, 1155.0, 1710.0))
        assertTrue((rushing.driftSlope ?: 0.0) < 0.0, "a rushing learner must be visible to drift analysis")
        assertTrue(rushing.isCorrect, "and must still be scored on the pattern, which they played correctly")
    }

    @Test
    fun `the recorded score carries the asynchronies for feedback and diagnostics`() {
        // Recorded, per §6.3's first line, and shown as "you were a little ahead". The guarantee is
        // about what *scores*, not about hiding the number from the learner.
        val s = score(loose)
        assertEquals(4, s.matches.size)
        assertTrue(s.matches.all { it.asynchronyMs != null })
        assertTrue(s.toleranceHalfWidthMs > 0.0, "the window applied is recorded, so the attempt can be re-scored")
    }
}
