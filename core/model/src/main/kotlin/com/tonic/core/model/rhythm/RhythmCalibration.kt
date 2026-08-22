package com.tonic.core.model.rhythm

/**
 * One learner's measured timing constant for one output route — docs/40-PHASE-4-SPEC.md §4.3.
 *
 * What it corrects for is not one thing but two, inseparably: the device's output latency (the gap
 * between `play()` and the ear) and the learner's own sensorimotor offset (the gap between hearing a
 * beat and their finger arriving). §4.1 is explicit that both are unknown and that only their
 * *difference* matters, which is exactly what tapping along measures. Nothing here tries to separate
 * them, and nothing downstream needs them separated.
 *
 * @property offsetMs the systematic offset, signed, in milliseconds. **Positive means the learner taps
 *   late.** The constant is `median(tap − beat)`, so removing it means subtracting — see
 *   [TapTimeline.relativeToPatternMs], which is the one place it is applied.
 * @property spreadMs how consistent they were, as the median absolute deviation of the asynchronies.
 *   **Diagnostic, never a pass mark.** §4.3, in those words: "A large spread means either an
 *   inconsistent user or an unstable device path; either way, tolerance windows should widen rather
 *   than the user being failed." A calibration is never rejected for a wide spread, and [Calibrator]
 *   asserts that rather than trusting it.
 * @property tapsUsed how many taps the median was taken over, after the warm-up discard. Recorded so a
 *   constant derived from the bare minimum is distinguishable from one derived from a full run — the
 *   two deserve different confidence and nothing else would show the difference.
 */
public data class RhythmCalibration(
    public val offsetMs: Double,
    public val spreadMs: Double,
    public val tapsUsed: Int,
)

/**
 * Every stored calibration constant, one per [CalibrationSlot] — docs/40-PHASE-4-SPEC.md §4.3:
 * "Calibration is per output route, not global."
 *
 * Null means never measured on that route, which is a real and common state: it is where every learner
 * starts, and where they return the first time they plug in headphones. [ProductionGate] turns it into
 * a block with an explanation rather than a silent zero, because a missing constant applied as zero is
 * indistinguishable from a perfectly-calibrated device and wrong by the whole output latency.
 */
public data class RhythmCalibrations(
    public val speaker: RhythmCalibration? = null,
    public val wired: RhythmCalibration? = null,
) {
    /** The constant stored for [slot], or null if that route has never been calibrated. */
    public fun forSlot(slot: CalibrationSlot): RhythmCalibration? =
        when (slot) {
            CalibrationSlot.SPEAKER -> speaker
            CalibrationSlot.WIRED -> wired
        }

    /**
     * The constant that applies on [route], or null — either because the route has not been calibrated
     * or because it is one production cannot run on at all.
     *
     * Deliberately collapses those two cases to null. A caller that needs to tell them apart asks
     * [ProductionGate], which is the one place that distinction is made and phrased for a learner.
     */
    public fun forRoute(route: AudioOutputRoute): RhythmCalibration? =
        when (val timing = route.timing) {
            is RouteTiming.Calibratable -> forSlot(timing.slot)
            is RouteTiming.Unusable -> null
        }

    /** What [ProductionGate.evaluate] needs: the slots a constant actually exists for. */
    public val calibratedSlots: Set<CalibrationSlot>
        get() =
            CalibrationSlot.entries
                .filterTo(mutableSetOf()) { forSlot(it) != null }

    /** This book with [slot]'s constant replaced. Storage is per slot; measuring one never touches another. */
    public fun with(
        slot: CalibrationSlot,
        calibration: RhythmCalibration,
    ): RhythmCalibrations =
        when (slot) {
            CalibrationSlot.SPEAKER -> copy(speaker = calibration)
            CalibrationSlot.WIRED -> copy(wired = calibration)
        }
}
