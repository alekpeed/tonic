package com.tonic.feature.practice.engine

import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.player.PlaybackHandle
import com.tonic.core.audio.player.PlaybackState
import com.tonic.core.audio.synth.PcmBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No real audio output - there is no device in this environment (docs/09-BUILD-PLAN.md Stage 6's known gap). Records what would have played, and when. */
class FakeAudioPlayer : AudioPlayer {
    val playedBuffers = mutableListOf<PcmBuffer>()
    val playedAtNanos = mutableListOf<Long>()
    var stopCount = 0
        private set

    /**
     * How long [PlaybackHandle.awaitCompletion] takes, in milliseconds. Zero - the default, and what
     * every test before Phase 4 wanted - completes instantly.
     *
     * Rhythm needs the other behavior. `PracticeViewModel` opens its tap window for exactly as long as
     * the backing plays, so a playback that finishes in the same tick it started leaves no interval in
     * which a tap can arrive: the window opens and closes between two statements and every test of it
     * would record zero taps and pass for the wrong reason.
     */
    var playbackDurationMs: Long = 0

    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override suspend fun play(buffer: PcmBuffer): PlaybackHandle {
        playedBuffers += buffer
        playedAtNanos += System.nanoTime()
        _state.value = PlaybackState.PLAYING
        val holdMs = playbackDurationMs
        return object : PlaybackHandle {
            override suspend fun awaitCompletion() {
                if (holdMs > 0) kotlinx.coroutines.delay(holdMs)
                _state.value = PlaybackState.COMPLETED
            }
        }
    }

    override fun stop() {
        stopCount++
        _state.value = PlaybackState.STOPPED
    }
}
