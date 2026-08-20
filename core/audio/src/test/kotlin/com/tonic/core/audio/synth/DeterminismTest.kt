package com.tonic.core.audio.synth

import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

/** docs/10-TESTING.md §6 / CLAUDE.md §5: identical seed -> byte-identical buffer. */
class DeterminismTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE

    @Test
    fun `same seed produces byte-identical note buffers`() {
        for (timbre in TimbreId.entries) {
            val a = SynthEngine.renderNote(64, timbre, 400, sampleRate, seed = 42L)
            val b = SynthEngine.renderNote(64, timbre, 400, sampleRate, seed = 42L)
            assertContentEquals(a.samples, b.samples, "$timbre did not reproduce byte-identically")
        }
    }

    @Test
    fun `different seeds produce different pluck buffers`() {
        val a = SynthEngine.renderNote(64, TimbreId.PLUCK, 400, sampleRate, seed = 1L)
        val b = SynthEngine.renderNote(64, TimbreId.PLUCK, 400, sampleRate, seed = 2L)
        assertFalse(
            a.samples.contentEquals(b.samples),
            "different seeds should not produce degenerate identical output",
        )
    }

    @Test
    fun `same seed produces byte-identical full items`() {
        val plan =
            ReferencePlan(
                cadenceFadeLevel = CadenceFadeLevel.L3,
                elements = listOf(ReferenceElement.ChordEvent(listOf(60, 64, 67), 500, TimbreId.SOFT)),
            )
        val a = SynthEngine.renderItem(plan, 200, 64, TimbreId.SOFT, 600, sampleRate, seed = 9L)
        val b = SynthEngine.renderItem(plan, 200, 64, TimbreId.SOFT, 600, sampleRate, seed = 9L)
        assertContentEquals(a.samples, b.samples)
    }
}
