package com.tonic.core.audio.player

import kotlin.math.ceil

/**
 * Decides when an `AudioTrack` has actually *finished sounding*, from successive
 * `playbackHeadPosition` readings.
 *
 * This exists because `AudioTrack.write` in `MODE_STREAM` returns once the frames are copied into
 * the track's buffer, not once they have been heard. Releasing the track at that moment discards
 * whatever had not yet reached the speaker, which is silent on a cold audio path and a brief
 * fragment on a warm one. Playback is only over when the playback head has advanced through every
 * frame that was written, so that — not the write loop — is what gates teardown.
 *
 * Kept as a pure class with no Android types so the decision can be unit-tested on the JVM; the
 * caller supplies the head positions and performs the waiting.
 *
 * Deliberately **not** a stall detector. An earlier design gave up when the head stopped advancing
 * for a few polls, which is indistinguishable from the hundred-or-so milliseconds the audio HAL
 * takes to spin up on the first note of a session — exactly the case that must not be truncated.
 * The only escape hatch is [maxPolls], sized from the clip's own duration plus a warm-up margin.
 */
internal class PlayoutMonitor(
    private val totalFrames: Int,
    private val maxPolls: Int,
) {
    private var polls = 0

    /** Feeds one `playbackHeadPosition` reading (frames played since the track started). */
    fun observe(headPositionFrames: Int): Verdict {
        if (headPositionFrames >= totalFrames) return Verdict.DONE
        polls++
        return if (polls >= maxPolls) Verdict.GIVE_UP else Verdict.KEEP_WAITING
    }

    enum class Verdict {
        /** Every written frame has been played. Safe to stop and release. */
        DONE,

        /** The head never got there in the time the clip could possibly need. Tear down rather than hang. */
        GIVE_UP,

        /** Still sounding — wait one more poll interval. */
        KEEP_WAITING,
    }

    companion object {
        /**
         * Covers the audio HAL's cold-start latency before the head first moves, plus the tail the
         * device buffers past the last written frame. Generous on purpose: overshooting costs a
         * silent wait no one hears, undershooting cuts off the stimulus.
         */
        const val WARMUP_MARGIN_MS = 750.0

        /** How many polls of [pollIntervalMs] a clip of [durationMs] could legitimately need. */
        fun pollsFor(
            durationMs: Double,
            pollIntervalMs: Long,
        ): Int = ceil((durationMs + WARMUP_MARGIN_MS) / pollIntervalMs).toInt().coerceAtLeast(1)
    }
}
