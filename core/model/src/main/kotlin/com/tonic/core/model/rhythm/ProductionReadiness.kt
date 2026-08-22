package com.tonic.core.model.rhythm

/**
 * Whether a tapping (production) exercise may run right now — docs/40-PHASE-4-SPEC.md §4.2, §4.3 and
 * §9 simulation 6.
 *
 * Recognition items are never gated by this. §7.5 makes that a requirement rather than a convenience:
 * "recognition nodes must form a complete, coherent path through every rhythmic concept," so a learner
 * on Bluetooth, or one who has not calibrated, still has somewhere to go. This type says only that
 * *tapping* is unavailable, never that rhythm is.
 */
public sealed interface ProductionReadiness {
    /** The route can be timed against, and a calibration constant exists for it. */
    public data object Ready : ProductionReadiness

    /**
     * Tapping cannot be scored. [reason] is what the learner is told, in the plain-language form §4.2
     * requires — "an actual mode change," not a dismissible warning that leaves a broken experience
     * behind it.
     */
    public data class Blocked(
        public val reason: BlockReason,
    ) : ProductionReadiness
}

/**
 * Why tapping is unavailable.
 *
 * Each of these is a distinct thing to say to a learner and a distinct thing for them to do about it,
 * which is why it is an enum rather than a boolean. Collapsing them would produce the one message that
 * fits none of the cases: "tapping is unavailable."
 *
 * ⚠️ **No user-facing string exists for any of these yet.** The screen that shows them is Stage 4.5.
 * docs/21-HANDOFF.md §4.1 is the standing warning about exactly this shape of gap — three Phase 3
 * stages shipped fully tested behind a door no learner could open — so what is owed is not another
 * test of the policy below, which is covered, but the test that asks whether a learner in a blocked
 * state can reach an explanation and read it. That test belongs with the screen, and is recorded as
 * owed rather than left to be noticed.
 */
public enum class BlockReason {
    /** Bluetooth output (§4.2). The learner's move is to switch route, or to practice recognition. */
    BLUETOOTH_OUTPUT,

    /** A route recognized as one this app cannot time against — HDMI, a cast target, a hearing aid. */
    UNSUPPORTED_ROUTE,

    /**
     * The route has not been read yet, or reading it failed. Transient, and distinct from
     * [UNSUPPORTED_ROUTE] because the learner's move is to wait rather than to change their setup.
     */
    ROUTE_UNKNOWN,

    /**
     * A usable route with no calibration constant stored for it — §9 simulation 6: "production must be
     * blocked with an explanation, not silently mis-scored."
     *
     * The common case, not an edge case. It is the state every learner is in before their first
     * production item, and the state they return to the first time they use a route they have not used
     * before. The move is to run calibration, which is one screen away.
     */
    NOT_CALIBRATED,
}

/**
 * The pure decision behind every production block — docs/40-PHASE-4-SPEC.md §4.2, §4.3, §9 sim 6.
 *
 * A function of its arguments and nothing else: no `AudioManager`, no settings read, no clock. That is
 * what lets §9's simulation 6 be a JVM test rather than a device check.
 */
public object ProductionGate {
    /**
     * @param route what the device is currently playing out of.
     * @param calibratedSlots the slots a calibration constant is stored for. Empty before the learner
     *   has ever calibrated, growing by one the first time they calibrate on a new kind of route.
     * @return [ProductionReadiness.Ready], or the single most relevant reason it is not.
     *
     * Route first, calibration second, deliberately. A learner on Bluetooth who has never calibrated is
     * in two blocked states at once, and telling them to calibrate would send them to run a measurement
     * that cannot succeed on the route they are on. The reason calibration cannot fix wins.
     */
    public fun evaluate(
        route: AudioOutputRoute,
        calibratedSlots: Set<CalibrationSlot>,
    ): ProductionReadiness =
        when (val timing = route.timing) {
            is RouteTiming.Unusable -> ProductionReadiness.Blocked(timing.reason)
            is RouteTiming.Calibratable ->
                if (timing.slot in calibratedSlots) {
                    ProductionReadiness.Ready
                } else {
                    ProductionReadiness.Blocked(BlockReason.NOT_CALIBRATED)
                }
        }
}
