package com.tonic.feature.diagnostic.engine

import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.player.PlaybackHandle
import com.tonic.core.audio.player.PlaybackState
import com.tonic.core.audio.synth.PcmBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No real audio output - there is no device in this environment (same known gap as `:feature:practice`'s Stage 6). Records what would have played. */
class FakeAudioPlayer : AudioPlayer {
    val playedBuffers = mutableListOf<PcmBuffer>()
    var stopCount = 0
        private set

    private val _state = MutableStateFlow(PlaybackState.IDLE)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override suspend fun play(buffer: PcmBuffer): PlaybackHandle {
        playedBuffers += buffer
        _state.value = PlaybackState.PLAYING
        return object : PlaybackHandle {
            override suspend fun awaitCompletion() {
                _state.value = PlaybackState.COMPLETED
            }
        }
    }

    override fun stop() {
        stopCount++
        _state.value = PlaybackState.STOPPED
    }
}
