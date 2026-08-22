package com.tonic.core.model.rhythm

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §4.3, the six numbered steps and the four design requirements under them.
 *
 * What these establish is that the constant is computed correctly from a given run. What they cannot
 * establish is §9's Stage 4.1 criterion — that the median is "stable across repeated runs on one
 * device" — which is a fact about hardware and a human hand, and is owed.
 */
class CalibratorTest {
    private val start = 20_000_000_000L
    private val beatMs = 500.0

    /** [count] steady beats at 120 BPM, as heard instants. */
    private fun beats(count: Int): List<Long> = (0 until count).map { start + (it * beatMs * 1_000_000).toLong() }

    /** One tap per beat, each [offsetMs] from it, skipping the beats the calibrator discards. */
    private fun tapsOn(
        beatList: List<Long>,
        offsetMs: Double,
        skip: Int = Calibrator.DEFAULT_DISCARD_BEATS,
    ): List<TapEvent> = beatList.drop(skip).map { TapEvent(it + (offsetMs * 1_000_000).toLong()) }

    private fun measured(outcome: CalibrationOutcome): RhythmCalibration {
        assertIs<CalibrationOutcome.Measured>(outcome, "expected a usable calibration, got $outcome")
        return outcome.calibration
    }

    @Test
    fun `a consistently late tapper measures as consistently late`() {
        val b = beats(16)
        val calibration = measured(Calibrator.measure(tapsOn(b, 40.0), b))
        assertTrue(abs(calibration.offsetMs - 40.0) < 0.5, "expected about 40 ms, got ${calibration.offsetMs}")
        assertTrue(calibration.spreadMs < 0.5, "a metronomic tapper should have almost no spread")
    }

    @Test
    fun `anticipation measures negative`() {
        // Synchronizing with a beat, people tend to land slightly before it. A calibrator that could
        // only express lateness would push every one of them onto the wrong side of the beat.
        val b = beats(16)
        val calibration = measured(Calibrator.measure(tapsOn(b, -35.0), b))
        assertTrue(abs(calibration.offsetMs + 35.0) < 0.5, "expected about -35 ms, got ${calibration.offsetMs}")
    }

    @Test
    fun `one distracted tap does not move the constant`() {
        // §4.3: "Median, not mean. One distracted tap must not skew the constant." A mean over this run
        // lands near 55 ms; the median must not notice.
        val b = beats(16)
        val taps = tapsOn(b, 30.0).toMutableList()
        taps[4] = TapEvent(taps[4].monotonicNanos + 230_000_000L)
        val calibration = measured(Calibrator.measure(taps, b))
        assertTrue(abs(calibration.offsetMs - 30.0) < 1.0, "expected about 30 ms, got ${calibration.offsetMs}")
    }

    @Test
    fun `the unstable first beats are excluded`() {
        // §4.3 step 3. The first two taps here are wildly early, as someone still finding the pulse is.
        // If they were counted the constant would be dragged toward a guess rather than a habit.
        val b = beats(16)
        val warmup = b.take(Calibrator.DEFAULT_DISCARD_BEATS).map { TapEvent(it - 180_000_000L) }
        val calibration = measured(Calibrator.measure(warmup + tapsOn(b, 25.0), b))
        assertTrue(abs(calibration.offsetMs - 25.0) < 1.0, "expected about 25 ms, got ${calibration.offsetMs}")
    }

    @Test
    fun `a scattered tapper is still calibrated, never failed`() {
        // §4.3: "Spread is diagnostic, not scored... tolerance windows should widen rather than the user
        // being failed." This is the whole requirement, and it is the one a naive quality check breaks.
        val b = beats(20)
        val jitter = listOf(-90.0, 70.0, -60.0, 95.0, -75.0, 55.0, -85.0, 80.0, -50.0, 65.0, -70.0, 60.0, 0.0, 40.0)
        val taps =
            b.drop(Calibrator.DEFAULT_DISCARD_BEATS).mapIndexed { i, beat ->
                TapEvent(beat + (jitter[i % jitter.size] * 1_000_000).toLong())
            }
        val calibration = measured(Calibrator.measure(taps, b))
        assertTrue(calibration.spreadMs > 40.0, "expected a wide spread to be reported, got ${calibration.spreadMs}")
    }

    @Test
    fun `too few taps fails rather than producing a constant from nothing`() {
        val b = beats(16)
        val outcome = Calibrator.measure(tapsOn(b, 30.0).take(3), b)
        assertEquals(CalibrationOutcome.Failed(CalibrationFailure.NOT_ENOUGH_TAPS), outcome)
    }

    @Test
    fun `no taps at all fails`() {
        assertEquals(
            CalibrationOutcome.Failed(CalibrationFailure.NOT_ENOUGH_TAPS),
            Calibrator.measure(emptyList(), beats(16)),
        )
    }

    @Test
    fun `a run with no metronome to match against fails`() {
        assertEquals(
            CalibrationOutcome.Failed(CalibrationFailure.NOT_ENOUGH_TAPS),
            Calibrator.measure(tapsOn(beats(16), 30.0), beatNanos = emptyList()),
        )
    }

    @Test
    fun `off-beat tapping is rejected, not averaged into a wrong constant`() {
        // Someone tapping between the beats. Note *which* guard catches this: on a steady grid every tap
        // is within half a beat of some beat, so attribution succeeds - it attributes them to the
        // following beat, at about -249 ms. What rejects the run is the plausibility bound, because no
        // combination of device latency and human anticipation puts a tap a quarter-second early.
        //
        // This is the real form of the failure §4.3 describes as an offset "larger than a beat". It
        // cannot appear as that literally, because attribution always picks the nearer beat, so it
        // appears as an offset approaching half a beat instead - which is what the fractional bound is
        // sized for. Storing this constant would mis-score every item afterwards.
        val b = beats(16)
        val taps = b.drop(Calibrator.DEFAULT_DISCARD_BEATS).map { TapEvent(it + 251_000_000L) }
        val outcome = Calibrator.measure(taps, b)
        assertIs<CalibrationOutcome.Failed>(outcome)
        assertEquals(CalibrationFailure.OFFSET_IMPLAUSIBLE, outcome.reason)
    }

    @Test
    fun `an implausible offset is rejected and reported, never stored`() {
        // §4.3's sanity bound. 200 ms late at this tempo is inside the absolute ceiling but is 40% of
        // the beat, which means the taps were attributed to the wrong beats.
        val b = beats(16)
        val outcome = Calibrator.measure(tapsOn(b, 220.0), b)
        assertIs<CalibrationOutcome.Failed>(outcome)
        assertEquals(CalibrationFailure.OFFSET_IMPLAUSIBLE, outcome.reason)
        assertTrue(outcome.rejected != null, "a rejected measurement should still be reported for the re-prompt")
    }

    @Test
    fun `the relative bound catches at slow tempi what the absolute bound cannot`() {
        // At 40 BPM a beat is 1500 ms, so 200 ms is comfortably plausible; at 200 BPM a beat is 300 ms
        // and the same 200 ms is most of it. One fixed millisecond ceiling cannot express both.
        val slow = (0 until 16).map { start + (it * 1500.0 * 1_000_000).toLong() }
        assertIs<CalibrationOutcome.Measured>(Calibrator.measure(tapsOn(slow, 200.0), slow))

        val fast = (0 until 20).map { start + (it * 300.0 * 1_000_000).toLong() }
        val outcome = Calibrator.measure(tapsOn(fast, 140.0), fast)
        assertIs<CalibrationOutcome.Failed>(outcome)
        assertEquals(CalibrationFailure.OFFSET_IMPLAUSIBLE, outcome.reason)
    }

    @Test
    fun `a missed tap costs one sample, not a whole beat of error`() {
        // The reason for nearest-beat matching rather than pairing tap k with beat k. Under index
        // matching, dropping one tap shifts every later pairing by a beat and yields a constant that is
        // wrong by 500 ms while looking perfectly ordinary.
        val b = beats(20)
        val taps = tapsOn(b, 35.0).toMutableList()
        taps.removeAt(5)
        val calibration = measured(Calibrator.measure(taps, b))
        assertTrue(abs(calibration.offsetMs - 35.0) < 1.0, "expected about 35 ms, got ${calibration.offsetMs}")
        assertEquals(taps.size, calibration.tapsUsed)
    }

    @Test
    fun `the same recorded run measures the same every time`() {
        // §4.4 at this layer: no clock is read inside, so a run replays identically and a constant that
        // came out wrong can be re-derived from the taps that produced it.
        val b = beats(16)
        val taps = tapsOn(b, 42.5)
        assertEquals(Calibrator.measure(taps, b), Calibrator.measure(taps, b))
    }
}
