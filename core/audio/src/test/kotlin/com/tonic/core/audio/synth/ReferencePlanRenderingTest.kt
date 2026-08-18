package com.tonic.core.audio.synth

import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 2 acceptance: "All eight cadence-fade levels
 * render plans of the expected structure and duration." The *decision* of
 * what each level contains is `ReferencePlanBuilder`'s job (Stage 3,
 * `:core:curriculum`) — this tests that `:core:audio` correctly renders
 * each structure from docs/06-AUDIO-ENGINE.md §5's table once handed one,
 * which is the piece that exists at this stage.
 */
class ReferencePlanRenderingTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE
    private val timbre = TimbreId.SOFT
    private val tolerance = 5 // samples, rounding slack across several segments

    private fun ms(samples: Int) = (samples.toLong() * 1000L) / sampleRate

    private fun chord(
        midiNotes: List<Int>,
        durationMs: Long,
    ) = ReferenceElement.ChordEvent(midiNotes, durationMs, timbre)

    @Test
    fun `L0 full cadence renders four chords back to back`() {
        val plan =
            ReferencePlan(
                CadenceFadeLevel.L0,
                listOf(
                    chord(listOf(60, 64, 67), 600),
                    chord(listOf(65, 69, 72), 600),
                    chord(listOf(67, 71, 74), 600),
                    chord(listOf(60, 64, 67), 600),
                ),
            )
        assertPlanDuration(plan, expectedMs = 2400)
    }

    @Test
    fun `L1 is the same structure as L0, flagged reusable`() {
        val plan =
            ReferencePlan(
                CadenceFadeLevel.L1,
                listOf(
                    chord(listOf(60, 64, 67), 600),
                    chord(listOf(65, 69, 72), 600),
                    chord(listOf(67, 71, 74), 600),
                    chord(listOf(60, 64, 67), 600),
                ),
                reusableForItems = 3,
            )
        assertEquals(3, plan.reusableForItems)
        assertPlanDuration(plan, expectedMs = 2400)
    }

    @Test
    fun `L2 is V-I only - two chords`() {
        val plan =
            ReferencePlan(CadenceFadeLevel.L2, listOf(chord(listOf(67, 71, 74), 600), chord(listOf(60, 64, 67), 600)))
        assertEquals(2, plan.elements.size)
        assertPlanDuration(plan, expectedMs = 1200)
    }

    @Test
    fun `L3 is the tonic triad only, about 800ms`() {
        val plan = ReferencePlan(CadenceFadeLevel.L3, listOf(chord(listOf(60, 64, 67), 800)))
        assertEquals(1, plan.elements.size)
        assertPlanDuration(plan, expectedMs = 800)
    }

    @Test
    fun `L4 tonic drone spans the whole item as an underlay, not the sequential timeline`() {
        val plan =
            ReferencePlan(
                CadenceFadeLevel.L4,
                listOf(ReferenceElement.DroneEvent(midi = 60, durationMs = 1600, timbre = timbre, relativeDb = -18.0)),
            )
        // The sequential (non-drone) render is empty - the drone doesn't occupy timeline space up front.
        val sequential = SynthEngine.renderReferencePlan(plan, sampleRate)
        assertEquals(0, sequential.samples.size)

        val item =
            SynthEngine.renderItem(
                plan,
                gapAfterReferenceMs = 0,
                targetMidi = 67,
                targetTimbre = timbre,
                targetDurationMs = 800,
                sampleRate = sampleRate,
                seed = 1L,
            )
        // Item duration is gap(0) + target(800ms) since there's no sequential reference; the drone is mixed under it.
        assertEquals(PcmBuffer.msToSamples(800, sampleRate), item.samples.size)
        assertTrue(Dsp.rms(item.samples) > 0.0)
    }

    @Test
    fun `L5 is a brief tonic flash then a silent gap`() {
        val plan =
            ReferencePlan(
                CadenceFadeLevel.L5,
                listOf(
                    ReferenceElement.ToneEvent(midi = 60, durationMs = 400, timbre = timbre),
                    ReferenceElement.Silence(durationMs = 1500),
                ),
            )
        assertPlanDuration(plan, expectedMs = 1900)
        val rendered = SynthEngine.renderReferencePlan(plan, sampleRate)
        val toneSamples = PcmBuffer.msToSamples(400, sampleRate)
        val gapSegment = rendered.samples.copyOfRange(toneSamples, rendered.samples.size)
        assertEquals(0.0, Dsp.rms(gapSegment))
    }

    @Test
    fun `L6 has nothing per item - an empty plan`() {
        val plan = ReferencePlan(CadenceFadeLevel.L6, emptyList())
        assertPlanDuration(plan, expectedMs = 0)
    }

    @Test
    fun `L7 is also an empty per-item plan - the growing gap is a block-level concern, not per-item content`() {
        val plan = ReferencePlan(CadenceFadeLevel.L7, emptyList())
        assertPlanDuration(plan, expectedMs = 0)
    }

    private fun assertPlanDuration(
        plan: ReferencePlan,
        expectedMs: Long,
    ) {
        val rendered = SynthEngine.renderReferencePlan(plan, sampleRate)
        val expectedSamples = PcmBuffer.msToSamples(expectedMs, sampleRate)
        assertTrue(
            kotlin.math.abs(rendered.samples.size - expectedSamples) <= tolerance,
            "expected ~$expectedSamples samples ($expectedMs ms), got ${rendered.samples.size}",
        )
    }
}
