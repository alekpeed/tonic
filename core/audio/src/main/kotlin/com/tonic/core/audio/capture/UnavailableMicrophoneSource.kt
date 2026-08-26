package com.tonic.core.audio.capture

import javax.inject.Inject

/**
 * The bound [MicrophoneSource] until real capture lands — reports unavailable, records nothing.
 *
 * **This is a placeholder, and it is deliberately one that cannot pretend.** Real capture is Stage
 * 3.0's unfinished half: `AudioRecord` on a dedicated thread, measured latency and CPU, dropout
 * behavior, accuracy on recorded rather than synthesized signals — every one of those is a property of
 * hardware, and docs/21-HANDOFF.md §2 records at length what happens when a simulated stand-in is
 * allowed to produce green checks for them instead.
 *
 * So it says [isAvailable] is false, which is a state the whole feature already handles as ordinary
 * rather than exceptional: docs/30-PHASE-3-SPEC.md §6.1 requires that a learner without microphone
 * permission gets no nagging, no prompts, and no degraded experience anywhere. The sung control is
 * simply absent and the degree ladder is the whole answer surface, exactly as it is today.
 *
 * And if it were ever called anyway, an empty capture resolves to "unclear" and re-prompts — never to
 * a wrong answer. There is no path through this class that records an incorrect attempt.
 *
 * Replacing it is a one-line change to [com.tonic.core.audio.di.AudioBindingsModule], which is the
 * point of the binding being there rather than the class being constructed at a call site.
 */
class UnavailableMicrophoneSource
    @Inject
    constructor() : MicrophoneSource {
        override val isAvailable: Boolean = false

        override suspend fun record(maxDurationMs: Long): CapturedAudio = CapturedAudio.empty(NOMINAL_SAMPLE_RATE)

        private companion object {
            /** Nothing is captured at it; a sample rate is required to describe an empty buffer at all. */
            const val NOMINAL_SAMPLE_RATE = 48_000
        }
    }
