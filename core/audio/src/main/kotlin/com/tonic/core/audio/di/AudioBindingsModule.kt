package com.tonic.core.audio.di

import com.tonic.core.audio.capture.MicrophoneSource
import com.tonic.core.audio.capture.UnavailableMicrophoneSource
import com.tonic.core.audio.focus.AudioFocusManager
import com.tonic.core.audio.focus.AudioInterruptions
import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.player.AudioTrackPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds each `:core:audio` contract to its one production implementation - docs/04-ARCHITECTURE.md §3. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AudioBindingsModule {
    @Binds
    abstract fun bindAudioPlayer(impl: AudioTrackPlayer): AudioPlayer

    @Binds
    abstract fun bindAudioInterruptions(impl: AudioFocusManager): AudioInterruptions

    /**
     * Microphone capture, currently bound to the implementation that reports itself unavailable —
     * see [UnavailableMicrophoneSource] for why a placeholder rather than a simulated capture, and
     * what replacing it involves.
     */
    @Binds
    abstract fun bindMicrophoneSource(impl: UnavailableMicrophoneSource): MicrophoneSource
}
