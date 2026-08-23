package com.tonic.core.audio.di

import com.tonic.core.audio.capture.AudioRecordMicrophoneSource
import com.tonic.core.audio.capture.MicrophoneSource
import com.tonic.core.audio.capture.UnavailableMicrophoneSource
import com.tonic.core.audio.focus.AudioFocusManager
import com.tonic.core.audio.focus.AudioInterruptions
import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.player.AudioTrackPlayer
import com.tonic.core.audio.player.PlaybackTimebaseSource
import com.tonic.core.audio.route.AudioManagerOutputRouteMonitor
import com.tonic.core.audio.route.OutputRouteMonitor
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

    /**
     * Output timing, for Phase 4's tap scoring — docs/40-PHASE-4-SPEC.md §4.1.
     *
     * [AudioTrackPlayer] has implemented this since Stage 4.0 and nothing bound it, so injecting it
     * would have failed at Hilt's compile step rather than at runtime — but only once something tried.
     * That is the same shape as the microphone binding this module already carries a note about, and
     * the same correction: bind a contract from the stage that writes it, not the stage that first
     * consumes it. Bound to the same instance as [bindAudioPlayer], which is what makes the reading
     * describe the playback the caller started.
     */
    @Binds
    abstract fun bindPlaybackTimebaseSource(impl: AudioTrackPlayer): PlaybackTimebaseSource

    @Binds
    abstract fun bindAudioInterruptions(impl: AudioFocusManager): AudioInterruptions

    /**
     * Microphone capture, bound to real `AudioRecord` capture since 2026-08-22.
     *
     * It was bound to [UnavailableMicrophoneSource] for three stages, which is how the sung response
     * came to be built, tested and shipped behind a boundary nothing could cross — see
     * [AudioRecordMicrophoneSource]'s KDoc for what that cost and why the reasoning was wrong.
     * [UnavailableMicrophoneSource] is kept: it is what a build should bind if capture ever has to be
     * disabled wholesale, and it is the shape every caller is still required to handle, because a
     * learner who declines the permission is in exactly that state.
     */
    @Binds
    abstract fun bindMicrophoneSource(impl: AudioRecordMicrophoneSource): MicrophoneSource

    /**
     * Output-route reporting, for Phase 4's production gate - docs/40-PHASE-4-SPEC.md §4.2.
     *
     * Bound from the stage it is written in rather than the stage it is first consumed in. That is the
     * direct correction of docs/21-HANDOFF.md §4.1: three Phase 3 stages shipped complete and green
     * behind a binding that reported "unavailable" forever, and nothing caught it because every test
     * used a fake. A production binding that exists is a thing a device can be pointed at.
     */
    @Binds
    abstract fun bindOutputRouteMonitor(impl: AudioManagerOutputRouteMonitor): OutputRouteMonitor
}
