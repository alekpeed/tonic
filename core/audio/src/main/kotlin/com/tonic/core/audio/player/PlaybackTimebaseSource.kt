package com.tonic.core.audio.player

import com.tonic.core.model.rhythm.OutputTimebase
import kotlinx.coroutines.flow.StateFlow

/**
 * Reports when the audio now playing is actually being heard —
 * docs/40-PHASE-4-SPEC.md §4.1, point 1.
 *
 * Separate from [AudioPlayer] rather than a property on it, and that is deliberate. Ten callers across
 * two feature modules play audio and none of them care what time it is; only rhythm production does.
 * Widening [AudioPlayer] would have made every one of those callers, and both test fakes, carry a
 * concept that means nothing to them — and docs/04-ARCHITECTURE.md's whole argument for interfaces
 * here is that a caller should depend on what it uses.
 *
 * The one production [AudioPlayer] also implements this, so a consumer that needs both injects both
 * and gets one object.
 */
public interface PlaybackTimebaseSource {
    /**
     * The most recent reading for the playback in progress, or null when nothing is playing or the
     * platform has not produced one yet.
     *
     * Null is the ordinary early state, not an error: `AudioTrack.getTimestamp` reports nothing until
     * enough frames have actually reached the output, which is exactly the interval in which the
     * device's own latency is still settling. A caller that needs a timebase waits for a non-null
     * value rather than treating the absence as zero — treating it as zero would place every event at
     * the moment `play()` was called, which is the error §4.1 exists to describe.
     */
    public val timebase: StateFlow<OutputTimebase?>
}
