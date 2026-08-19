package com.tonic.core.audio.focus

import kotlinx.coroutines.flow.SharedFlow

/**
 * Contract only — [AudioFocusManager] is the one production implementation, kept behind this
 * interface for exactly the reason [com.tonic.core.audio.player.AudioPlayer] is: interruption
 * handling (docs/06-AUDIO-ENGINE.md §8) is behavior that *must* be unit-testable, and the practice
 * loop that consumes it is driven from a plain JVM test with no Android framework available. A
 * concrete [AudioFocusManager] can't be constructed there — it resolves `AUDIO_SERVICE` off a real
 * `Context` in its initializer.
 */
interface AudioInterruptions {
    /** Every interruption the platform reports, in order. Hot: events emitted before a collector attaches are dropped. */
    val events: SharedFlow<AudioInterruptionEvent>

    /**
     * Requests audio focus for a practice session and registers the becoming-noisy receiver.
     * Returns false if the platform refused focus — the caller decides what to do about it.
     */
    fun requestFocus(): Boolean

    /** Releases focus and unregisters the becoming-noisy receiver. Safe to call when focus was never held. */
    fun releaseFocus()
}
