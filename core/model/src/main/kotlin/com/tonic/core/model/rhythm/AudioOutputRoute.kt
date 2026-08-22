package com.tonic.core.model.rhythm

/**
 * Where the app's audio is currently coming out — docs/40-PHASE-4-SPEC.md §4.2.
 *
 * This matters to exactly one thing, and only from Phase 4 onward: whether a tap can be timed against
 * a sound. Every module before rhythm asks "what did you hear," and a hundred milliseconds of
 * transport latency changes nothing about the answer. Rhythm production asks "when did you hear it,"
 * and there the route is the difference between a measurable offset and an unmeasurable one.
 *
 * Kept in `:core:model` as a plain enum rather than in `:core:audio` next to the `AudioManager` code
 * that produces it, because the *policy* — see [ProductionGate] — is a pure decision that a JVM test
 * has to be able to drive. What needs a device is reading the route, not reasoning about it. Same
 * boundary docs/30-PHASE-3-SPEC.md drew around microphone capture, for the same reason.
 */
public enum class AudioOutputRoute(
    /** What this route means for timing. See [RouteTiming] — exactly one of its two cases applies. */
    public val timing: RouteTiming,
) {
    /** The device's own speaker. Usable, and the route most learners will start on. */
    SPEAKER(RouteTiming.Calibratable(CalibrationSlot.SPEAKER)),

    /** A 3.5 mm headset, or the analog side of a headset adapter. */
    WIRED(RouteTiming.Calibratable(CalibrationSlot.WIRED)),

    /**
     * USB headphones or a USB audio interface.
     *
     * Shares [CalibrationSlot.WIRED] rather than having a slot of its own. ⚠️ **That grouping is an
     * assumption, not a measurement** — a USB DAC's latency is its own, and nothing from first
     * principles says it matches the analog path. It is grouped because §4.3 names two stored
     * constants, speaker and wired, and inventing a third before anyone has measured a USB device
     * would be adding schema on a guess. Stage 4.1 is where a device settles it; if the grouping does
     * not hold, this gets its own slot and the settings key that goes with it.
     */
    USB(RouteTiming.Calibratable(CalibrationSlot.WIRED)),

    /**
     * Any Bluetooth transport — A2DP, SCO, or LE.
     *
     * §4.2: 100–300 ms of added latency, varying by codec and by device pair, and not reliably
     * queryable. That is not "less accurate." At an eighth of a beat or more, drifting between
     * sessions, there is no constant for calibration to find.
     */
    BLUETOOTH(RouteTiming.Unusable(BlockReason.BLUETOOTH_OUTPUT)),

    /**
     * Something real but unrecognized — HDMI, a cast target, a hearing aid, a device type Android
     * added after this was written.
     *
     * Blocked rather than assumed usable. The set of audio device types grows with every platform
     * release, and a route this code has never heard of is precisely the one whose latency nobody here
     * has thought about.
     */
    OTHER(RouteTiming.Unusable(BlockReason.UNSUPPORTED_ROUTE)),

    /**
     * Nothing has been read yet, or the query failed.
     *
     * Distinct from [OTHER] on purpose: [OTHER] means "we looked and did not recognize it," this means
     * "we have not looked." They block identically and they should be reported differently, because
     * one of them is a bug and the other is a Tuesday. Blocking rather than defaulting to usable is
     * the whole point of having the case: letting a learner tap costs nothing if we were wrong to
     * block, and costs their belief that the app can hear them if we were wrong to allow.
     */
    UNKNOWN(RouteTiming.Unusable(BlockReason.ROUTE_UNKNOWN)),
    ;

    /** Whether tapping exercises can be scored on this route at all. Recognition is never gated (§7.5). */
    public val allowsProduction: Boolean get() = timing is RouteTiming.Calibratable
}

/**
 * What a route means for timing: either there is a calibration constant that applies to it, or there
 * is a reason it can never be tapped on.
 *
 * A sealed interface rather than a nullable slot plus a nullable reason, because those two fields
 * would have to agree — exactly one non-null, always — and nothing would enforce it. Here the
 * invariant is the type, and [ProductionGate]'s `when` over it has no unreachable branch to get
 * quietly wrong.
 */
public sealed interface RouteTiming {
    /** Timeable. [slot] is the stored constant that applies; §4.3 requires it be per-route, not global. */
    public data class Calibratable(
        public val slot: CalibrationSlot,
    ) : RouteTiming

    /** Not timeable, ever, on this route. [reason] is what the learner is told. */
    public data class Unusable(
        public val reason: BlockReason,
    ) : RouteTiming
}

/**
 * Which calibration constant a route uses — docs/40-PHASE-4-SPEC.md §4.3: "Calibration is per output
 * route, not global. Store separately for speaker and wired output."
 *
 * Separate constants because plugging in headphones changes the offset, and one global constant would
 * silently apply the speaker's number to the headset path. That is worse than no calibration: an
 * uncalibrated learner is blocked and told why, a wrongly-calibrated one is scored confidently wrong.
 */
public enum class CalibrationSlot {
    SPEAKER,
    WIRED,
}
