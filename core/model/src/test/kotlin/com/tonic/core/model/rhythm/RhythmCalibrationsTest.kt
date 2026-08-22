package com.tonic.core.model.rhythm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * docs/40-PHASE-4-SPEC.md §4.3: "Calibration is per output route, not global. Store separately for
 * speaker and wired output."
 *
 * The requirement is easy to state and easy to lose — a single stored constant would satisfy every
 * other test in this package and silently apply the speaker's latency to a headset. These are the
 * assertions that would notice.
 */
class RhythmCalibrationsTest {
    private val speakerConstant = RhythmCalibration(offsetMs = 62.0, spreadMs = 11.0, tapsUsed = 14)
    private val wiredConstant = RhythmCalibration(offsetMs = 18.5, spreadMs = 9.0, tapsUsed = 14)

    @Test
    fun `nothing is calibrated to begin with`() {
        val empty = RhythmCalibrations()
        assertEquals(emptySet(), empty.calibratedSlots)
        assertNull(empty.forRoute(AudioOutputRoute.SPEAKER))
        assertEquals(
            ProductionReadiness.Blocked(BlockReason.NOT_CALIBRATED),
            ProductionGate.evaluate(AudioOutputRoute.SPEAKER, empty.calibratedSlots),
        )
    }

    @Test
    fun `calibrating the speaker leaves the wired route uncalibrated`() {
        val book = RhythmCalibrations().with(CalibrationSlot.SPEAKER, speakerConstant)
        assertEquals(speakerConstant, book.forRoute(AudioOutputRoute.SPEAKER))
        assertNull(book.forRoute(AudioOutputRoute.WIRED))
        assertEquals(setOf(CalibrationSlot.SPEAKER), book.calibratedSlots)
    }

    @Test
    fun `the two constants coexist without overwriting each other`() {
        // The failure this guards against is a "with" that copies the wrong field, which would look
        // correct in every single-slot test and lose a measurement the moment both exist.
        val book =
            RhythmCalibrations()
                .with(CalibrationSlot.SPEAKER, speakerConstant)
                .with(CalibrationSlot.WIRED, wiredConstant)
        assertEquals(speakerConstant, book.forSlot(CalibrationSlot.SPEAKER))
        assertEquals(wiredConstant, book.forSlot(CalibrationSlot.WIRED))
        assertEquals(CalibrationSlot.entries.toSet(), book.calibratedSlots)
    }

    @Test
    fun `usb reads the wired constant`() {
        // The grouping documented as an assumption on AudioOutputRoute.USB. Asserted so that if a device
        // shows it is wrong, this fails and the decision is revisited rather than the mapping drifting.
        val book = RhythmCalibrations().with(CalibrationSlot.WIRED, wiredConstant)
        assertEquals(wiredConstant, book.forRoute(AudioOutputRoute.USB))
    }

    @Test
    fun `a route production cannot run on has no constant, however much is stored`() {
        val book =
            RhythmCalibrations()
                .with(CalibrationSlot.SPEAKER, speakerConstant)
                .with(CalibrationSlot.WIRED, wiredConstant)
        assertNull(book.forRoute(AudioOutputRoute.BLUETOOTH))
        assertNull(book.forRoute(AudioOutputRoute.OTHER))
        assertNull(book.forRoute(AudioOutputRoute.UNKNOWN))
    }

    @Test
    fun `a fully calibrated learner on a usable route is ready to tap`() {
        val book = RhythmCalibrations().with(CalibrationSlot.SPEAKER, speakerConstant)
        assertEquals(
            ProductionReadiness.Ready,
            ProductionGate.evaluate(AudioOutputRoute.SPEAKER, book.calibratedSlots),
        )
        // And plugging in headphones puts them straight back to needing one - §4.3's whole point.
        assertEquals(
            ProductionReadiness.Blocked(BlockReason.NOT_CALIBRATED),
            ProductionGate.evaluate(AudioOutputRoute.WIRED, book.calibratedSlots),
        )
    }
}
