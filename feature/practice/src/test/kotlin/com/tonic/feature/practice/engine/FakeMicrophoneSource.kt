package com.tonic.feature.practice.engine

import com.tonic.core.audio.capture.CapturedAudio
import com.tonic.core.audio.capture.MicrophoneSource

/**
 * A microphone that returns whatever audio a test hands it — the boundary
 * [com.tonic.core.audio.capture.MicrophoneSource] exists to create.
 *
 * Defaults to unavailable, so every test written before Phase 3 describes a tap-only learner without
 * having to say so, which is also the state a real learner is in until they opt in
 * (docs/30-PHASE-3-SPEC.md §6.1).
 *
 * [nextCapture] is deliberately synthesized audio supplied by the caller rather than anything this
 * class invents. A fake that generated its own "voice" would be asserting on its own idea of what a
 * sung note sounds like; handing it `SynthEngine` output at a known frequency means the test is
 * measuring the real analyzer against real audio.
 */
class FakeMicrophoneSource(
    override var isAvailable: Boolean = false,
) : MicrophoneSource {
    /** Returned by the next [record]. Empty by default, which resolves to "unclear" and re-prompts. */
    var nextCapture: CapturedAudio = CapturedAudio.empty(SAMPLE_RATE)

    /** Every window length asked for, so a test can assert capture is bounded rather than open-ended. */
    val requestedWindowsMs = mutableListOf<Long>()

    override suspend fun record(maxDurationMs: Long): CapturedAudio {
        requestedWindowsMs += maxDurationMs
        return nextCapture
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
