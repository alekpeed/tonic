package com.tonic.core.audio.synth

import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * docs/10-TESTING.md §6: "No clicks | max sample-to-sample delta | below
 * threshold (catches click-producing envelope bugs)." Checked both for a
 * single note and across the element-concatenation boundaries inside
 * [SynthEngine.renderItem] — that's specifically where a click could sneak
 * in if a segment doesn't taper to true zero before the next one starts.
 */
class ClickDetectionTest {
    private val sampleRate = PcmBuffer.DEFAULT_SAMPLE_RATE

    // A real click is a near-instantaneous jump; this threshold is generous relative to the smooth
    // envelope ramps and oscillator content, so anything above it is a genuine discontinuity.
    private val maxDeltaThreshold = 0.35f

    private fun maxDelta(samples: FloatArray): Float {
        var max = 0f
        for (i in 1 until samples.size) {
            val d = kotlin.math.abs(samples[i] - samples[i - 1])
            if (d > max) max = d
        }
        return max
    }

    @Test
    fun `single notes have no discontinuities`() {
        for (timbre in TimbreId.entries) {
            for (midi in listOf(40, 69, 96)) {
                val buffer = SynthEngine.renderNote(midi, timbre, durationMs = 500, sampleRate, seed = 3L)
                val delta = maxDelta(buffer.samples)
                assertTrue(delta < maxDeltaThreshold, "$timbre midi=$midi max delta=$delta")
            }
        }
    }

    @Test
    fun `a full item with a chord cadence reference has no discontinuities at segment boundaries`() {
        val plan =
            ReferencePlan(
                cadenceFadeLevel = CadenceFadeLevel.L0,
                elements =
                    listOf(
                        ReferenceElement.ChordEvent(listOf(60, 64, 67), 300, TimbreId.SOFT, listOf(0, 3, 6)),
                        ReferenceElement.ChordEvent(listOf(65, 69, 72), 300, TimbreId.SOFT, listOf(0, 4, 7)),
                        ReferenceElement.ChordEvent(listOf(67, 71, 74), 300, TimbreId.SOFT, listOf(0, 2, 5)),
                        ReferenceElement.ChordEvent(listOf(60, 64, 67), 300, TimbreId.SOFT, listOf(0, 3, 6)),
                    ),
            )
        val item =
            SynthEngine.renderItem(
                referencePlan = plan,
                gapAfterReferenceMs = 150,
                targetMidi = 64,
                targetTimbre = TimbreId.SOFT,
                targetDurationMs = 500,
                sampleRate = sampleRate,
                seed = 5L,
            )
        val delta = maxDelta(item.samples)
        assertTrue(delta < maxDeltaThreshold, "full item max delta=$delta")
    }

    @Test
    fun `a drone underlay does not introduce a discontinuity`() {
        val plan =
            ReferencePlan(
                cadenceFadeLevel = CadenceFadeLevel.L4,
                elements =
                    listOf(
                        ReferenceElement.DroneEvent(
                            midi = 60,
                            durationMs = 1400,
                            timbre = TimbreId.SOFT,
                            relativeDb = -18.0,
                        ),
                    ),
            )
        val item =
            SynthEngine.renderItem(
                referencePlan = plan,
                gapAfterReferenceMs = 0,
                targetMidi = 67,
                targetTimbre = TimbreId.PURE,
                targetDurationMs = 800,
                sampleRate = sampleRate,
                seed = 7L,
            )
        val delta = maxDelta(item.samples)
        assertTrue(delta < maxDeltaThreshold, "drone-underlaid item max delta=$delta")
    }
}
