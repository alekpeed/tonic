package com.tonic.core.audio.capture

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The capture boundary's value semantics, and the placeholder's one promise.
 *
 * [CapturedAudio] overrides equality by hand because `FloatArray` gives a data class reference
 * equality, which would make two identical captures unequal and quietly break any test comparing
 * them — including the very tests the boundary exists to enable. That override is real logic and is
 * pinned here rather than trusted.
 */
class CapturedAudioTest {
    @Test
    fun `identical captures are equal and hash alike`() {
        val a = CapturedAudio(floatArrayOf(0.1f, -0.2f, 0.3f), sampleRate = 48_000)
        val b = CapturedAudio(floatArrayOf(0.1f, -0.2f, 0.3f), sampleRate = 48_000)

        assertEquals(a, b, "same samples, same rate - reference identity must not matter")
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a, a)
    }

    @Test
    fun `captures differing in samples or rate are not equal`() {
        val base = CapturedAudio(floatArrayOf(0.1f, -0.2f), sampleRate = 48_000)

        assertNotEquals(base, CapturedAudio(floatArrayOf(0.1f, 0.2f), sampleRate = 48_000))
        assertNotEquals(base, CapturedAudio(floatArrayOf(0.1f, -0.2f), sampleRate = 44_100))
        assertFalse(base.equals("not a capture"))
    }

    @Test
    fun `empty describes itself as empty and a real capture does not`() {
        assertTrue(CapturedAudio.empty(48_000).isEmpty)
        assertFalse(CapturedAudio(floatArrayOf(0f), 48_000).isEmpty)
    }

    /**
     * The placeholder's contract — docs/30-PHASE-3-SPEC.md §6.1 makes "no microphone" an ordinary
     * state, and [UnavailableMicrophoneSource] must present it that way: unavailable, and empty if
     * called anyway, which downstream resolves to "unclear" rather than to a wrong answer.
     */
    @Test
    fun `the unavailable source reports unavailable and captures nothing`() =
        runBlocking {
            val source = UnavailableMicrophoneSource()

            assertFalse(source.isAvailable)
            assertTrue(source.record(maxDurationMs = 3_000).isEmpty)
        }
}
