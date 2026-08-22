package com.tonic.core.audio.route

import com.tonic.core.model.rhythm.AudioOutputRoute
import kotlinx.coroutines.flow.StateFlow

/**
 * Reports where audio is currently playing out, and updates when that changes —
 * docs/40-PHASE-4-SPEC.md §4.2 and §4.3.
 *
 * An interface for the same reason [com.tonic.core.audio.capture.MicrophoneSource] is one: the
 * implementation is the part no JVM test can exercise. What is connected to a phone, and which output
 * wins when several are, is a fact about hardware. Everything that acts on the answer —
 * [com.tonic.core.model.rhythm.ProductionGate], the calibration slot a constant is stored under, the
 * screen that explains a block — is a pure function of the route this reports, and is tested by
 * handing it each route in turn.
 *
 * **A `StateFlow` rather than a getter**, because §4.3 requires acting on the *change*: "Plugging in
 * headphones changes the offset. Detect the route change and either re-calibrate or invalidate the
 * stored constant." A value that has to be polled is a value that gets read once at the start of an
 * item and believed for the rest of it, which is exactly the case that mis-scores.
 */
public interface OutputRouteMonitor {
    /**
     * The current route. Starts at [AudioOutputRoute.UNKNOWN] and stays there if the platform reports
     * nothing usable — never optimistically [AudioOutputRoute.SPEAKER], because "we have not looked"
     * and "we looked and it is the speaker" must not block and allow respectively by accident.
     */
    public val route: StateFlow<AudioOutputRoute>
}
