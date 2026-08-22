package com.tonic.core.model.rhythm

/**
 * The correspondence between a frame of audio and the moment it is heard —
 * docs/40-PHASE-4-SPEC.md §4.1, point 1.
 *
 * Rhythm's whole difficulty starts here. Everything before Phase 4 could treat "the app called
 * `play()`" as "the sound happened," because nothing was ever measured against it. A tap has to be
 * measured against the sound the learner actually heard, and between the `play()` call and the ear lie
 * the mixer, the device's output path, and the buffer the HAL is holding. On a warm path that is tens
 * of milliseconds; §4.1's own arithmetic makes 60 ms about an eighth of a beat at 120 BPM, which is
 * enough to score a perfectly-timed learner as consistently rushing.
 *
 * One reading anchors the two clocks: at [framePosition], the audio being heard was the frame at that
 * index, and the wall-clock instant was [presentationNanos]. Every other frame follows by arithmetic,
 * because frames advance at exactly [sampleRate] per second by construction.
 *
 * **Pure, and in `:core:model` on purpose.** The Android side of this is one call to
 * `AudioTrack.getTimestamp()`, which returns a frame index and a nanosecond timestamp and nothing
 * else. Keeping the arithmetic here means the conversion a tap's score depends on is JVM-testable,
 * and the untestable part is reduced to "did the platform hand back a reading" — the same split
 * docs/30-PHASE-3-SPEC.md made around microphone capture.
 *
 * ⚠️ **How far this can be trusted is unmeasured.** docs/40-PHASE-4-SPEC.md §10 q2 asks outright
 * whether Android's reported output timing is trustworthy enough to measure the offset rather than
 * merely detect the transport, and nothing in this repository can answer it. The arithmetic below is
 * correct given a correct reading; whether readings are correct is a device question, and until it is
 * answered §4.3's calibration — which derives the offset empirically from the learner's own taps —
 * is what the scoring actually rests on. This is the cross-check, not the source of truth.
 *
 * @property framePosition the frame index that was being presented at [presentationNanos]. Frames are
 *   counted from the start of the track, exactly as `AudioTrack` counts them.
 * @property presentationNanos the wall-clock instant [framePosition] was heard, on the same monotonic
 *   clock that timestamps taps. Mixing clocks here — one reading from `nanoTime`, another from
 *   `currentTimeMillis` — would produce a difference that looks like latency and is not.
 * @property sampleRate frames per second of the track this reading came from.
 */
public data class OutputTimebase(
    public val framePosition: Long,
    public val presentationNanos: Long,
    public val sampleRate: Int,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(framePosition >= 0) { "framePosition must not be negative, was $framePosition" }
    }

    /**
     * When [frame] is (or was) heard, on the same clock as [presentationNanos].
     *
     * Extrapolates in both directions: a frame before [framePosition] resolves to an instant in the
     * past, which is exactly what is needed to answer "when did the count-in's first click sound"
     * from a reading taken part-way through the pattern.
     */
    public fun nanosForFrame(frame: Long): Long =
        presentationNanos + Math.floorDiv((frame - framePosition) * NANOS_PER_SECOND, sampleRate.toLong())

    /**
     * The frame being heard at [nanos] — the inverse of [nanosForFrame].
     *
     * Rounds to the *nearest* frame rather than the preceding one, so that the two functions compose:
     * a frame index converted to nanoseconds and back returns the frame it started from. Flooring
     * would not, because a frame boundary in nanoseconds is almost never a whole number — at 48 kHz a
     * frame is 20833.3 ns, and the truncated value lands a few nanoseconds *before* the boundary, so
     * flooring gives back the frame below. The error is 20 microseconds and inaudible; the broken
     * round trip is the kind of thing that surfaces much later as an off-by-one nobody can place.
     */
    public fun frameForNanos(nanos: Long): Long =
        framePosition +
            Math.floorDiv(
                (nanos - presentationNanos) * sampleRate + NANOS_PER_SECOND / 2,
                NANOS_PER_SECOND,
            )

    /**
     * How old this reading is at [nowNanos]. Negative if [nowNanos] precedes it.
     *
     * Exposed rather than turned into an `isStale` predicate, because the threshold is a device fact
     * nobody here has measured. A reading drifts as the device's clocks diverge, and how fast is
     * §10 q2's question. Stage 4.1 sets the number from a device; until then a caller that needs a
     * bound should say so in its own terms rather than inherit one invented here.
     */
    public fun ageNanos(nowNanos: Long): Long = nowNanos - presentationNanos

    public companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
