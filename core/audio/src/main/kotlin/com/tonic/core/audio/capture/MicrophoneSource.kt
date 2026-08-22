package com.tonic.core.audio.capture

/**
 * Captures one bounded window of microphone audio — docs/30-PHASE-3-SPEC.md §5.2 step 1.
 *
 * An interface because the implementation is the one part of the sung response that cannot be
 * verified without a device (§8 Stage 3.0: accuracy on recorded signals, off-main-thread operation,
 * dropouts, measured latency and CPU are all properties of real capture). Everything downstream —
 * framing, confidence, the sustained median, degree resolution, scoring, mastery — is a pure function
 * of the buffer this returns, and is tested against synthesized audio at exactly known frequencies.
 * Putting the boundary here is what keeps that true: a fake source makes the whole answer path
 * testable on the JVM, and the untestable part is reduced to "did `AudioRecord` fill the array."
 *
 * **Nothing captured here is ever persisted.** §7, in those words: "Captured audio exists in memory
 * for the duration of one attempt and is discarded. Nothing recorded, nothing cached, nothing
 * exported." An implementation that writes a buffer to disk — even a temp file, even for debugging —
 * violates the promise the microphone permission was granted on.
 */
interface MicrophoneSource {
    /**
     * True when this source can actually record: permission granted and hardware available.
     *
     * Checked rather than assumed because §6.1 makes denial a normal state, not an error — "denying or
     * revoking permission silently disables singing. No nagging, no repeat prompts, no degraded
     * experience elsewhere." A caller that finds this false offers the tap ladder and says nothing.
     */
    val isAvailable: Boolean

    /**
     * Records for at most [maxDurationMs] and returns the mono samples captured.
     *
     * Bounded by construction: there is no `start`/`stop` pair, because a capture that can be left
     * running is a capture that can be left running. The window is the whole API.
     *
     * Suspends for the duration. Implementations do the blocking read off the main thread; the caller
     * is a coroutine that has already put the exercise into its answer state.
     *
     * @return the captured window, or an empty [CapturedAudio] if recording could not start at all.
     *   Never throws for an ordinary denial — that is [isAvailable]'s job to report first.
     */
    suspend fun record(maxDurationMs: Long): CapturedAudio
}

/**
 * One window of captured audio, in memory, for the lifetime of one attempt.
 *
 * Mono float samples in `[-1, 1]`, matching what
 * [com.tonic.core.audio.pitch.SungResponseAnalyzer] reads and what
 * [com.tonic.core.audio.synth.PcmBuffer] produces — so a test can hand the analyzer synthesized audio
 * through exactly the type real capture would deliver.
 */
data class CapturedAudio(
    val samples: FloatArray,
    val sampleRate: Int,
) {
    val isEmpty: Boolean get() = samples.isEmpty()

    // FloatArray gives data classes reference equality, which would make two identical captures
    // unequal and quietly break any test comparing them. Spelled out rather than left to the default.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedAudio) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRate

    companion object {
        /** Recording could not start, or produced nothing. Resolves to "unclear", never to a wrong answer. */
        fun empty(sampleRate: Int): CapturedAudio = CapturedAudio(FloatArray(0), sampleRate)
    }
}
