package com.tonic.core.audio.route

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.tonic.core.model.rhythm.AudioOutputRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OutputRouteMonitor] over `AudioManager` — docs/40-PHASE-4-SPEC.md §4.2.
 *
 * Deliberately thin. It asks the platform which output devices are connected, hands the type codes to
 * [AudioDeviceRouting], and publishes the answer; every decision that follows from the answer lives in
 * pure code elsewhere. That split is what keeps the untestable surface down to "did `getDevices`
 * return something," which is the only part a device is genuinely needed for.
 *
 * Registered for the process lifetime as a `@Singleton`. An `AudioDeviceCallback` costs nothing while
 * idle, and the alternative — subscribing when a rhythm screen opens — would mean the first route read
 * of every session races the callback that populates it.
 *
 * **Unverified on a real device**, like [com.tonic.core.audio.player.AudioTrackPlayer] and
 * [com.tonic.core.audio.focus.AudioFocusManager] before it. The unit-test `android.jar` returns stubs,
 * so nothing here has been observed running. What specifically wants checking with hardware in hand is
 * recorded on [AudioDeviceRouting.activeRoute]: what a phone actually lists, and whether the priority
 * rule picks the same device `AudioTrack.getRoutedDevice()` reports.
 */
@Singleton
class AudioManagerOutputRouteMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : OutputRouteMonitor {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _route = MutableStateFlow(AudioOutputRoute.UNKNOWN)
        override val route: StateFlow<AudioOutputRoute> = _route.asStateFlow()

        private val deviceCallback =
            object : AudioDeviceCallback() {
                // Both directions re-read the whole list rather than adding or removing the devices
                // handed to them. The callback reports a delta, and reconstructing state from deltas
                // means one missed event leaves the route permanently wrong — on a value that decides
                // whether a learner's taps are scored.
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
            }

        init {
            // The main looper, not the caller's thread: registerAudioDeviceCallback needs a Handler,
            // and this object is constructed by Hilt wherever the first injection point happens to be.
            audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            // registerAudioDeviceCallback delivers the current device list to the callback immediately
            // on some versions and not others. Reading once here makes the initial value defined
            // either way, rather than depending on which behavior this device has.
            refresh()
        }

        private fun refresh() {
            val types =
                runCatching {
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
                }.getOrElse {
                    // A failure to read the route is exactly the UNKNOWN case: it blocks production and
                    // says so, rather than falling through to a guess. Not swallowed silently in the
                    // sense CLAUDE.md §6 forbids — the failure has a designated, user-visible state.
                    emptyList()
                }
            _route.value = AudioDeviceRouting.activeRoute(types)
        }
    }
