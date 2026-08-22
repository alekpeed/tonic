package com.tonic.core.audio.route

import android.media.AudioDeviceInfo
import com.tonic.core.model.rhythm.AudioOutputRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * docs/40-PHASE-4-SPEC.md §4.2 — classifying the platform's connected outputs, and picking the one
 * audio is going through.
 *
 * Every `AudioDeviceInfo.TYPE_*` below is a compile-time constant, so these run on the JVM with no
 * device and no Robolectric. What they cannot establish is what a real phone reports: that is a device
 * question, recorded on [AudioDeviceRouting.activeRoute] and owed at Stage 4.1.
 */
class AudioDeviceRoutingTest {
    @Test
    fun `built-in outputs are the speaker route`() {
        assertEquals(
            AudioOutputRoute.SPEAKER,
            AudioDeviceRouting.routeForDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
        )
        assertEquals(
            AudioOutputRoute.SPEAKER,
            AudioDeviceRouting.routeForDeviceType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE),
        )
    }

    @Test
    fun `analog outputs are the wired route`() {
        for (type in listOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE,
        )) {
            assertEquals(AudioOutputRoute.WIRED, AudioDeviceRouting.routeForDeviceType(type), "type $type")
        }
    }

    @Test
    fun `every bluetooth transport is the bluetooth route, classic and LE alike`() {
        // §4.2 disqualifies Bluetooth by its latency, not by its profile name. An LE headset that fell
        // through to OTHER would still be blocked, but the learner would be told the wrong reason; one
        // that fell through to SPEAKER would be scored.
        for (type in listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
        )) {
            assertEquals(AudioOutputRoute.BLUETOOTH, AudioDeviceRouting.routeForDeviceType(type), "type $type")
        }
    }

    @Test
    fun `usb outputs are their own route`() {
        for (type in listOf(
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
        )) {
            assertEquals(AudioOutputRoute.USB, AudioDeviceRouting.routeForDeviceType(type), "type $type")
        }
    }

    @Test
    fun `non-media outputs are not routes at all`() {
        // The case that would otherwise block tapping on every phone ever made: getDevices lists the
        // telephony path on ordinary handsets, and treating it as an unrecognized external output would
        // make it outrank the speaker forever.
        assertNull(AudioDeviceRouting.routeForDeviceType(AudioDeviceInfo.TYPE_TELEPHONY))
        assertNull(AudioDeviceRouting.routeForDeviceType(AudioDeviceInfo.TYPE_REMOTE_SUBMIX))
    }

    @Test
    fun `an unrecognized output is OTHER, not a guess`() {
        assertEquals(AudioOutputRoute.OTHER, AudioDeviceRouting.routeForDeviceType(AudioDeviceInfo.TYPE_HDMI))
        assertEquals(AudioOutputRoute.OTHER, AudioDeviceRouting.routeForDeviceType(Int.MAX_VALUE))
    }

    @Test
    fun `a plain phone with nothing plugged in is the speaker`() {
        val phone =
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_TELEPHONY,
            )
        assertEquals(AudioOutputRoute.SPEAKER, AudioDeviceRouting.activeRoute(phone))
    }

    @Test
    fun `bluetooth wins over everything, because everything else would be scored`() {
        // getDevices lists connected devices, and the built-in speaker is always one of them. If the
        // reducer preferred the speaker, a learner on earbuds would be scored against a sound arriving
        // 200 ms after the app thinks it did - the exact failure §4.2 is written to prevent.
        val paired =
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_TELEPHONY,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            )
        assertEquals(AudioOutputRoute.BLUETOOTH, AudioDeviceRouting.activeRoute(paired))
    }

    @Test
    fun `wired and usb outrank the speaker, and bluetooth outranks both`() {
        val wired = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADSET)
        assertEquals(AudioOutputRoute.WIRED, AudioDeviceRouting.activeRoute(wired))

        val usb = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_USB_HEADSET)
        assertEquals(AudioOutputRoute.USB, AudioDeviceRouting.activeRoute(usb))

        assertEquals(
            AudioOutputRoute.BLUETOOTH,
            AudioDeviceRouting.activeRoute(usb + AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
        )
    }

    @Test
    fun `an unrecognized external output outranks the speaker`() {
        // Fail-safe, and the direction is the point: when this rule guesses wrong, it blocks a learner
        // who could have tapped and tells them why, rather than scoring one whose sound is late.
        val docked = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_HDMI)
        assertEquals(AudioOutputRoute.OTHER, AudioDeviceRouting.activeRoute(docked))
    }

    @Test
    fun `nothing reported is UNKNOWN, never an optimistic default`() {
        assertEquals(AudioOutputRoute.UNKNOWN, AudioDeviceRouting.activeRoute(emptyList()))
        // A list containing only non-media paths is the same case: we looked, and learned nothing about
        // where music goes.
        assertEquals(AudioOutputRoute.UNKNOWN, AudioDeviceRouting.activeRoute(listOf(AudioDeviceInfo.TYPE_TELEPHONY)))
    }
}
