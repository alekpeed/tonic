package com.tonic.feature.practice.engine

import com.tonic.core.audio.focus.AudioInterruptionEvent
import com.tonic.core.audio.focus.AudioInterruptions
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Stands in for [com.tonic.core.audio.focus.AudioFocusManager], which can't be constructed off-device
 * (it resolves `AUDIO_SERVICE` from a real `Context`). Lets a test emit the exact platform events
 * docs/06-AUDIO-ENGINE.md §8 enumerates and assert what the loop does about them.
 */
class FakeAudioInterruptions : AudioInterruptions {
    private val _events =
        MutableSharedFlow<AudioInterruptionEvent>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val events: SharedFlow<AudioInterruptionEvent> = _events.asSharedFlow()

    var focusRequestCount = 0
        private set
    var focusReleaseCount = 0
        private set

    val holdsFocus: Boolean get() = focusRequestCount > focusReleaseCount

    override fun requestFocus(): Boolean {
        focusRequestCount++
        return true
    }

    override fun releaseFocus() {
        focusReleaseCount++
    }

    /** Suspends until the engine's collector has actually subscribed, then emits — no dropped events, no sleeps. */
    suspend fun emit(event: AudioInterruptionEvent) {
        _events.subscriptionCount.first { it > 0 }
        _events.emit(event)
    }
}
