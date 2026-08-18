package com.tonic.core.audio.player

import com.tonic.core.audio.synth.PcmBuffer
import kotlinx.coroutines.flow.StateFlow

/**
 * docs/06-AUDIO-ENGINE.md §7. Contract only — [com.tonic.core.audio.player.AudioTrackPlayer]
 * is the one production implementation, kept behind this interface so a
 * different backend (Oboe/NDK, needed for Phase 4's rhythm module per
 * docs/06-AUDIO-ENGINE.md §1) can be swapped in without touching callers.
 */
interface AudioPlayer {
    /** Full item audio, already rendered to one contiguous buffer — see docs/06-AUDIO-ENGINE.md §7. */
    suspend fun play(buffer: PcmBuffer): PlaybackHandle

    /** Stops playback immediately. Used on interruption (docs/06-AUDIO-ENGINE.md §8) — never a soft fade. */
    fun stop()

    val state: StateFlow<PlaybackState>
}

/** A handle to one in-flight [AudioPlayer.play] call. */
interface PlaybackHandle {
    /** Suspends until this playback reaches [PlaybackState.COMPLETED] or is stopped/interrupted. */
    suspend fun awaitCompletion()
}

enum class PlaybackState {
    IDLE,
    PLAYING,
    STOPPED,

    /** Reached the end of the buffer on its own — distinct from [STOPPED], which means something interrupted it. */
    COMPLETED,
}
