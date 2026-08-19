package com.tonic.core.audio.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mandatory interruption handling, docs/06-AUDIO-ENGINE.md §8. Every event
 * this can emit maps to an exact required behavior; see [AudioInterruptionEvent].
 *
 * **Unverified on a real device** — see the note on
 * [com.tonic.core.audio.player.AudioTrackPlayer]; the same caveat applies here.
 */
@Singleton
class AudioFocusManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AudioInterruptions {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        private val _events =
            MutableSharedFlow<AudioInterruptionEvent>(
                extraBufferCapacity = 8,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        override val events: SharedFlow<AudioInterruptionEvent> = _events.asSharedFlow()

        private var focusRequest: AudioFocusRequest? = null
        private var noisyReceiverRegistered = false

        private val focusChangeListener =
            AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                        _events.tryEmit(AudioInterruptionEvent.TransientLoss)

                    AudioManager.AUDIOFOCUS_LOSS ->
                        _events.tryEmit(AudioInterruptionEvent.PermanentLoss)

                    // Deliberately treated the same as a transient loss: "do NOT duck. Ducking changes the
                    // loudness of a stimulus mid-item, which corrupts the trial. Pause instead."
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        _events.tryEmit(AudioInterruptionEvent.TransientLoss)

                    AudioManager.AUDIOFOCUS_GAIN ->
                        _events.tryEmit(AudioInterruptionEvent.FocusRegained)
                }
            }

        private val becomingNoisyReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receivedContext: Context,
                    intent: Intent,
                ) {
                    if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                        _events.tryEmit(AudioInterruptionEvent.BecomingNoisy)
                    }
                }
            }

        override fun requestFocus(): Boolean {
            val request =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    ).setOnAudioFocusChangeListener(focusChangeListener)
                    .setWillPauseWhenDucked(true)
                    .build()
            focusRequest = request
            val result = audioManager.requestAudioFocus(request)

            if (!noisyReceiverRegistered) {
                context.registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                noisyReceiverRegistered = true
            }

            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        override fun releaseFocus() {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
            if (noisyReceiverRegistered) {
                runCatching { context.unregisterReceiver(becomingNoisyReceiver) }
                noisyReceiverRegistered = false
            }
        }
    }

/** One interruption, and exactly what it means to do about it — docs/06-AUDIO-ENGINE.md §8. */
sealed interface AudioInterruptionEvent {
    /** Call, notification, or a duck request treated as one: pause immediately, discard the current item, restore on regain. */
    data object TransientLoss : AudioInterruptionEvent

    /** End the session cleanly and persist resume state. */
    data object PermanentLoss : AudioInterruptionEvent

    data object FocusRegained : AudioInterruptionEvent

    /** Headphones unplugged: pause, discard the current item. */
    data object BecomingNoisy : AudioInterruptionEvent
}
