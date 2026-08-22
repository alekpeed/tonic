package com.tonic.core.model.rhythm

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * docs/40-PHASE-4-SPEC.md §4.1: the arithmetic that turns "the frame at index N" into "the moment the
 * learner heard it."
 *
 * These tests establish that the conversion is right *given a correct reading*. They say nothing about
 * whether the platform's readings are correct — §10 q2, which needs a device. Two separate claims, and
 * conflating them is how a green suite comes to stand for something it never checked
 * (docs/21-HANDOFF.md §2).
 */
class OutputTimebaseTest {
    private val sampleRate = 48_000
    private val nanosPerSecond = 1_000_000_000L

    private fun timebase(
        framePosition: Long = 96_000,
        presentationNanos: Long = 5_000_000_000L,
    ) = OutputTimebase(framePosition, presentationNanos, sampleRate)

    @Test
    fun `the anchor frame resolves to the anchor instant`() {
        val base = timebase()
        assertEquals(base.presentationNanos, base.nanosForFrame(base.framePosition))
    }

    @Test
    fun `one second of frames is one second of nanoseconds`() {
        val base = timebase()
        assertEquals(
            base.presentationNanos + nanosPerSecond,
            base.nanosForFrame(base.framePosition + sampleRate),
        )
    }

    @Test
    fun `frames before the reading extrapolate backwards`() {
        // The case the whole class exists for: a reading taken part-way through a pattern has to answer
        // "when did the count-in's first click sound", which is in the past.
        val base = timebase()
        assertEquals(
            base.presentationNanos - 2 * nanosPerSecond,
            base.nanosForFrame(base.framePosition - 2 * sampleRate),
        )
    }

    @Test
    fun `frame and instant round-trip in both directions`() {
        val base = timebase()
        for (offset in listOf(-96_000L, -4_801L, -1L, 0L, 1L, 4_801L, 96_000L)) {
            val frame = base.framePosition + offset
            assertEquals(frame, base.frameForNanos(base.nanosForFrame(frame)), "round trip failed at $offset")
        }
    }

    @Test
    fun `frameForNanos lands within a frame of the true position`() {
        val base = timebase()
        // 250 ms after the reading is 12000 frames at 48 kHz, exactly.
        assertEquals(base.framePosition + 12_000, base.frameForNanos(base.presentationNanos + 250_000_000L))
    }

    @Test
    fun `a millisecond of audio is a millisecond wherever the anchor sits`() {
        // Independence from the anchor, which is what makes a stale-but-valid reading still usable:
        // two timebases from different moments of the same track agree about durations.
        val early = timebase(framePosition = 4_800, presentationNanos = 1_000_000_000L)
        val late = timebase(framePosition = 480_000, presentationNanos = 11_000_000_000L)
        val elapsedEarly = early.nanosForFrame(4_800 + 48) - early.nanosForFrame(4_800)
        val elapsedLate = late.nanosForFrame(480_000 + 48) - late.nanosForFrame(480_000)
        assertEquals(elapsedEarly, elapsedLate)
        assertTrue(abs(elapsedEarly - 1_000_000L) <= 1, "a millisecond of frames should be a millisecond")
    }

    @Test
    fun `age is measured from the reading, signed`() {
        val base = timebase()
        assertEquals(0L, base.ageNanos(base.presentationNanos))
        assertEquals(nanosPerSecond, base.ageNanos(base.presentationNanos + nanosPerSecond))
        assertEquals(-nanosPerSecond, base.ageNanos(base.presentationNanos - nanosPerSecond))
    }

    @Test
    fun `a nonsensical reading is rejected rather than carried`() {
        // AudioTrackPlayer guards against constructing one of these on the audio thread. This is the
        // other half of that contract: the type refuses values that would silently produce garbage
        // timings rather than a visible failure.
        assertFailsWith<IllegalArgumentException> { OutputTimebase(0, 0, sampleRate = 0) }
        assertFailsWith<IllegalArgumentException> { OutputTimebase(0, 0, sampleRate = -48_000) }
        assertFailsWith<IllegalArgumentException> {
            OutputTimebase(
                framePosition = -1,
                presentationNanos = 0,
                sampleRate,
            )
        }
    }
}
