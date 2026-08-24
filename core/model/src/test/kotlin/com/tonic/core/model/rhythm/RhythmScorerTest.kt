package com.tonic.core.model.rhythm

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md's Stage 4.3 acceptance, item by item: "Pure function of inputs,
 * byte-identical on replay. Windows never overlap. Extra/missed taps distinguished."
 */
class RhythmScorerTest {
    private val beatMs = 600.0
    private val meter = Meter.FOUR_FOUR

    /** One bar of plain beats: events at 0, 600, 1200, 1800 ms. */
    private val plainBar =
        RhythmPattern(meter, bars = 1, onsetTicks = (0 until 4).map { it * Meter.TICKS_PER_BEAT })

    private fun score(
        taps: List<Double>,
        pattern: RhythmPattern = plainBar,
        toleranceLevel: Int = 0,
        calibrationOffsetMs: Double = 0.0,
    ) = RhythmScorer.scoreRelative(pattern, taps, beatMs, toleranceLevel, calibrationOffsetMs)

    @Test
    fun `a perfect performance matches every event and adds nothing`() {
        val s = score(listOf(0.0, 600.0, 1200.0, 1800.0))
        assertEquals(4, s.matchedCount)
        assertEquals(0, s.missedCount)
        assertEquals(0, s.extraCount)
        assertEquals(1.0, s.patternAccuracy)
        assertTrue(s.isCorrect)
    }

    @Test
    fun `a missed tap and an extra tap are counted separately`() {
        // §6.2: "Extra taps and missing taps are both errors, and are recorded distinctly - they mean
        // different things pedagogically." A learner adding sounds heard a different rhythm; one
        // dropping a sound may simply have hesitated.
        val missed = score(listOf(0.0, 600.0, 1800.0))
        assertEquals(1, missed.missedCount)
        assertEquals(0, missed.extraCount)

        val extra = score(listOf(0.0, 300.0, 600.0, 1200.0, 1800.0))
        assertEquals(0, extra.missedCount)
        assertEquals(1, extra.extraCount)
        assertEquals(listOf(300.0), extra.extraTaps)
    }

    @Test
    fun `a missed event reports no asynchrony rather than a zero`() {
        val s = score(listOf(0.0, 600.0, 1800.0))
        assertNull(s.matches[2].asynchronyMs, "a missed event has no asynchrony, not an asynchrony of nothing")
        assertNotNull(s.matches[3].asynchronyMs)
    }

    @Test
    fun `tapping continuously does not score as perfect`() {
        // The failure a naive accuracy would allow: every event covered, and the flurry between them
        // free. Measuring against whichever is larger - events expected or taps given - closes it.
        val flurry = (0..24).map { it * 100.0 }
        val s = score(flurry)
        assertTrue(s.patternAccuracy < 0.25, "a continuous tapper scored ${s.patternAccuracy}")
        assertTrue(!s.isCorrect)
    }

    @Test
    fun `windows never overlap, so one tap can never match two events`() {
        // §6.2's third bullet, at the tempo and density where it bites: sixteenths at a fast tempo. Two
        // events 107 ms apart, and level 0's quarter-beat window is 107 ms wide unadjusted.
        val fastBeat = 60_000.0 / 140
        val sixteenths = RhythmPattern(meter, bars = 1, onsetTicks = (0 until 16).map { it * 3 })
        val halfWidth = ToleranceWindows.halfWidthMs(0, fastBeat, sixteenths)
        val gap = 3 * (fastBeat / Meter.TICKS_PER_BEAT)
        assertTrue(halfWidth * 2 <= gap + 1e-9, "windows of $halfWidth overlap across a gap of $gap")

        // And the consequence that matters: a tap between two events is credited to exactly one.
        val midpoint = gap / 2
        val s = RhythmScorer.scoreRelative(sixteenths, listOf(midpoint), fastBeat, 0)
        assertTrue(s.matchedCount <= 1, "one tap matched ${s.matchedCount} events")
    }

    @Test
    fun `a stray early tap does not shift every later pairing`() {
        // Why matching takes the closest pair first rather than sweeping left to right. Under a sweep,
        // the stray at 250 ms would consume the slot the 600 ms event needed and every later pairing
        // would shift, turning one mistake into a whole pattern scored wrong.
        val s = score(listOf(250.0, 600.0, 1200.0, 1800.0), toleranceLevel = 0)
        assertEquals(3, s.matchedCount)
        assertEquals(1, s.extraCount)
        assertEquals(listOf(250.0), s.extraTaps)
        // The one genuinely missed event is the downbeat, not an arbitrary later one.
        assertNull(s.matches[0].asynchronyMs)
        assertNotNull(s.matches[1].asynchronyMs)
    }

    @Test
    fun `a tighter tolerance rejects what a looser one accepts`() {
        // §6.1: timing is "informational at low levels, gating only via TIMING_TOLERANCE at higher
        // ones". 60 ms is a tenth of a beat here: inside level 0's window, outside level 3's.
        val late = listOf(60.0, 660.0, 1260.0, 1860.0)
        assertTrue(score(late, toleranceLevel = 0).isCorrect, "level 0 should be forgiving")
        assertTrue(!score(late, toleranceLevel = 3).isCorrect, "level 3 should not accept a tenth of a beat")
    }

    @Test
    fun `a uniformly late learner is inside even the tightest window at a comfortable tempo`() {
        // §9 simulation 2: the learner who perceives correctly and taps uniformly 40 ms late "must
        // still master". At 100 BPM they do so without calibration even mattering - the tightest
        // window is a twelfth of a 600 ms beat, which is 50 ms, and 40 is inside it.
        //
        // Worth stating rather than assuming, because it is the quiet reason a calibration bug could
        // pass unnoticed: at the tempo most practice happens, a plausible offset is already absorbed by
        // the tolerance. The next test is where it stops being.
        val late = listOf(40.0, 640.0, 1240.0, 1840.0)
        assertTrue(score(late, toleranceLevel = 3).isCorrect)
    }

    @Test
    fun `at a fast tempo the same learner needs calibration to pass at all`() {
        // The window is a fraction of the beat (§6.2), so it shrinks in milliseconds as the tempo
        // rises. At 160 BPM a beat is 375 ms and the tightest window is 31 ms, so the same 40 ms offset
        // that was comfortably inside it at 100 BPM now falls outside every event. This is what
        // §4.3's calibration is actually for, and simulation 2 fails here without it.
        val fastBeat = 60_000.0 / 160
        val events = (0 until 4).map { it * fastBeat }
        val late = events.map { it + 40.0 }

        val uncorrected = RhythmScorer.scoreRelative(plainBar, late, fastBeat, toleranceLevel = 3)
        assertTrue(!uncorrected.isCorrect, "40 ms should be outside a 31 ms window")
        assertEquals(4, uncorrected.missedCount)

        val corrected =
            RhythmScorer.scoreRelative(plainBar, late, fastBeat, toleranceLevel = 3, calibrationOffsetMs = 40.0)
        assertTrue(corrected.isCorrect, "calibration must absorb a constant offset entirely")
        corrected.matches.forEach { assertTrue(abs(it.asynchronyMs ?: 99.0) < 0.001) }
    }

    @Test
    fun `drift is a trend, not a magnitude`() {
        // §5.3 criterion 3, and the whole reason it is phrased that way: "a constant offset is a
        // calibration artifact while a growing offset is a real timekeeping failure."
        val constant = score(listOf(30.0, 630.0, 1230.0, 1830.0))
        assertEquals(0.0, constant.driftSlope ?: 1.0, 0.0001, "a uniform offset is not drift")

        val rushing = score(listOf(0.0, 580.0, 1140.0, 1680.0))
        val slope = rushing.driftSlope
        assertNotNull(slope)
        assertTrue(slope < 0, "a learner speeding up should drift negative, got $slope")
    }

    @Test
    fun `drift needs at least two matched events`() {
        assertNull(score(listOf(0.0)).driftSlope, "a trend through one point is not a trend")
        assertNull(score(emptyList()).driftSlope)
    }

    @Test
    fun `drift in milliseconds per beat is readable, and scales with the beat`() {
        val rushing = score(listOf(0.0, 580.0, 1140.0, 1680.0))
        val perBeat = rushing.driftMsPerBeat(beatMs)
        assertNotNull(perBeat)
        assertEquals(rushing.driftSlope!! * beatMs, perBeat, 0.0001)
    }

    @Test
    fun `no taps at all is a complete miss, not a crash`() {
        val s = score(emptyList())
        assertEquals(0, s.matchedCount)
        assertEquals(4, s.missedCount)
        assertEquals(0.0, s.patternAccuracy)
        assertTrue(!s.isCorrect)
    }

    @Test
    fun `the same recorded taps score identically, forever`() {
        // §4.4: "Given the same recorded taps, scoring must produce byte-identical results forever."
        val taps = listOf(12.0, 604.0, 1191.0, 1802.0, 2100.0)
        assertEquals(score(taps, toleranceLevel = 2), score(taps, toleranceLevel = 2))
    }

    @Test
    fun `the order taps arrive in does not change the score`() {
        // Input dispatch delivers touches in order in practice. "In practice" is the kind of assumption
        // that produces a scoring difference visible only on one device.
        val ordered = listOf(0.0, 600.0, 1200.0, 1800.0)
        assertEquals(score(ordered), score(ordered.reversed()))
        assertEquals(score(ordered), score(ordered.shuffled(kotlin.random.Random(7))))
    }

    @Test
    fun `nothing in scoring reads a clock`() {
        // Asserted the only way a test can: run it twice with real time passing in between and require
        // the results to be equal. A clock read anywhere in the chain would show up here as a
        // difference, and §4.4 forbids one outright.
        val taps = listOf(5.0, 595.0, 1210.0, 1795.0)
        val first = score(taps, toleranceLevel = 1)
        var spin = 0L
        repeat(200_000) { spin += it.toLong() }
        val second = score(taps, toleranceLevel = 1)
        assertEquals(first, second, "scoring produced a different result the second time (spin $spin)")
    }
}
