package com.tonic.core.audio.route

import android.media.AudioDeviceInfo
import com.tonic.core.model.rhythm.AudioOutputRoute

/**
 * Turns the platform's list of connected output devices into the one
 * [AudioOutputRoute] the app is playing through — docs/40-PHASE-4-SPEC.md §4.2.
 *
 * Pure, and takes plain `Int`s rather than `AudioDeviceInfo` objects, so the classification and the
 * priority rule are unit-testable on the JVM. Every `AudioDeviceInfo.TYPE_*` referenced here is a
 * compile-time constant, so nothing in this file touches an Android class at runtime. The part that
 * needs a device is asking `AudioManager` what is connected — that is
 * [AudioManagerOutputRouteMonitor]'s job and it is one call.
 */
internal object AudioDeviceRouting {
    /**
     * Which route a single connected output device represents, or null if it is not a route media
     * plays out of at all.
     *
     * The null case is load-bearing, not tidiness. `AudioManager.getDevices` lists every connected
     * output, and on an ordinary phone that always includes `TYPE_TELEPHONY` — the voice-call path —
     * and often `TYPE_REMOTE_SUBMIX`. Neither is where music goes. Classifying them as "some
     * unrecognized external output" would make [activeRoute] return
     * [AudioOutputRoute.OTHER] on every phone, forever, and block tapping for everyone.
     */
    fun routeForDeviceType(type: Int): AudioOutputRoute? =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            -> AudioOutputRoute.SPEAKER

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE,
            -> AudioOutputRoute.WIRED

            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            -> AudioOutputRoute.USB

            // Every Bluetooth transport, classic and LE. The LE constants are API 31/33 additions and
            // are referenced by name rather than by number on purpose: they are compile-time ints, so
            // they inline and cost nothing on an API 26 device, and a magic 26/27/30 in this file would
            // be unreadable and unverifiable. Lint reports InlinedApi here, which is the correct and
            // harmless report for exactly this pattern.
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            -> AudioOutputRoute.BLUETOOTH

            // Not a media output. See the KDoc: treating these as external outputs would block tapping
            // on every phone.
            AudioDeviceInfo.TYPE_TELEPHONY,
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
            -> null

            // A real output this code does not know — HDMI, a dock, a hearing aid, a cast target, a
            // type added to Android after this was written. Blocked by AudioOutputRoute.OTHER rather
            // than guessed at.
            else -> AudioOutputRoute.OTHER
        }

    /**
     * The route the app is playing through, given every connected output device type.
     *
     * @param outputDeviceTypes `AudioDeviceInfo.getType()` for each device
     *   `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` returned.
     * @return the winning route, or [AudioOutputRoute.UNKNOWN] if nothing usable was reported.
     *
     * A priority rule rather than a query, because on API 26 there is no call that says "and *this* is
     * the one audio is going to." The order below is **fail-safe, not a claim about Android's routing
     * table**: an external output outranks the built-in speaker, and among external outputs the ones
     * that cannot be timed on outrank the ones that can. So when the rule is wrong, it is wrong in the
     * direction of blocking a learner who could have tapped and telling them why — never in the
     * direction of scoring a learner whose sound is 200 ms late.
     *
     * ⚠️ **The rule is unverified on hardware.** What a real phone lists, and which device actually
     * wins when several are connected, is a device fact. Two better sources exist and both need a
     * device to evaluate: `AudioTrack.getRoutedDevice()`, which reports where a live track is actually
     * going, and API 31's `AudioManager.getAudioDevicesForAttributes()`. Stage 4.1 has a device in hand
     * and should cross-check this against `getRoutedDevice()` before the calibration constant it
     * chooses starts depending on it.
     */
    fun activeRoute(outputDeviceTypes: List<Int>): AudioOutputRoute {
        val routes = outputDeviceTypes.mapNotNull(::routeForDeviceType)
        return PRIORITY.firstOrNull { it in routes } ?: AudioOutputRoute.UNKNOWN
    }

    /** Highest priority first. See [activeRoute] for why this order and not another. */
    private val PRIORITY =
        listOf(
            AudioOutputRoute.BLUETOOTH,
            AudioOutputRoute.OTHER,
            AudioOutputRoute.USB,
            AudioOutputRoute.WIRED,
            AudioOutputRoute.SPEAKER,
        )
}
