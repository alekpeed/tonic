package com.tonic.core.audio.synth

import com.tonic.core.audio.timbre.TimbreBank
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.music.Tuning

/**
 * Renders a [ReferencePlan] + target into PCM — docs/06-AUDIO-ENGINE.md §5:
 * "The audio layer must not decide *what* the reference is — it only
 * decides how it sounds." Every function here is pure `FloatArray`/[PcmBuffer]
 * in and out (docs/04-ARCHITECTURE.md §3), so it's unit-testable on the JVM
 * with no device — only [com.tonic.core.audio.player.AudioTrackPlayer] touches Android.
 */
object SynthEngine {
    /** Target RMS every rendered note is normalized to — the mechanism behind cross-timbre loudness matching. */
    const val TARGET_RMS = 0.15

    private val ADSR_BY_TIMBRE =
        mapOf(
            TimbreId.PURE to AdsrEnvelope(attackMs = 8, decayMs = 40, sustainLevel = 0.85f, releaseMs = 40),
            TimbreId.SOFT to AdsrEnvelope(attackMs = 15, decayMs = 80, sustainLevel = 0.8f, releaseMs = 60),
            // PLUCK's decay is intrinsic to its per-harmonic exponential decay (see TimbreBank.renderPluck);
            // the envelope here only guards the onset/offset samples against clicks.
            TimbreId.PLUCK to AdsrEnvelope(attackMs = 5, decayMs = 5, sustainLevel = 1.0f, releaseMs = 30),
            TimbreId.REED to AdsrEnvelope(attackMs = 40, decayMs = 30, sustainLevel = 0.9f, releaseMs = 60),
        )

    /** One enveloped, loudness-normalized note. This is the unit both reference tones and the target are built from. */
    fun renderNote(
        midi: Int,
        timbre: TimbreId,
        durationMs: Long,
        sampleRate: Int = PcmBuffer.DEFAULT_SAMPLE_RATE,
        seed: Long = 0L,
        a4Hz: Double = Tuning.DEFAULT_A4_HZ,
    ): PcmBuffer {
        val durationSamples = PcmBuffer.msToSamples(durationMs, sampleRate)
        val freq = Tuning.midiToHz(midi, a4Hz)
        val raw = TimbreBank.render(timbre, freq, durationSamples, sampleRate, seed)
        val envelope = ADSR_BY_TIMBRE.getValue(timbre).render(durationSamples, sampleRate)
        val enveloped = FloatArray(durationSamples) { raw[it] * envelope[it] }
        val normalized = Dsp.normalizeToRms(enveloped, TARGET_RMS)
        return PcmBuffer(Dsp.softLimit(normalized), sampleRate)
    }

    /** One [ReferenceElement], as audio. [seed] only matters for elements whose voices are PLUCK. */
    fun renderElement(
        element: ReferenceElement,
        sampleRate: Int = PcmBuffer.DEFAULT_SAMPLE_RATE,
        seed: Long = 0L,
    ): PcmBuffer =
        when (element) {
            is ReferenceElement.Silence ->
                PcmBuffer.silence(
                    PcmBuffer.msToSamples(element.durationMs, sampleRate),
                    sampleRate,
                )

            is ReferenceElement.ToneEvent ->
                renderNote(element.midi, element.timbre, element.durationMs, sampleRate, seed)

            is ReferenceElement.DroneEvent -> {
                val note = renderNote(element.midi, element.timbre, element.durationMs, sampleRate, seed)
                val gain = Dsp.dbToLinear(element.relativeDb).toFloat()
                PcmBuffer(FloatArray(note.samples.size) { note.samples[it] * gain }, sampleRate)
            }

            is ReferenceElement.ChordEvent -> {
                val durationSamples = PcmBuffer.msToSamples(element.durationMs, sampleRate)
                val voices =
                    element.midiNotes.mapIndexed { i, midi ->
                        val jitterMs = element.voiceJitterMs.getOrElse(i) { 0L }
                        val jitterSamples = PcmBuffer.msToSamples(jitterMs, sampleRate).coerceIn(0, durationSamples)
                        // Render the voice SHORTER by the jitter amount so its own release still tapers to
                        // exact zero before the chord's total duration is up — shifting a full-length note
                        // right and truncating its tail would cut it off mid-release and click at the
                        // chord's boundary with whatever comes next.
                        val voiceDurationSamples = durationSamples - jitterSamples
                        val voiceDurationMs = (voiceDurationSamples.toLong() * 1000L) / sampleRate
                        val note = renderNote(midi, element.timbre, voiceDurationMs, sampleRate, seed + i)
                        FloatArray(durationSamples) { n ->
                            val src = n - jitterSamples
                            if (src in note.samples.indices) note.samples[src] else 0f
                        }
                    }
                PcmBuffer(Dsp.softLimit(Dsp.mix(voices)), sampleRate)
            }
        }

    /** Concatenates a plan's non-drone elements in order. Drones are rendered separately as an underlay — see [renderItem]. */
    fun renderReferencePlan(
        plan: ReferencePlan,
        sampleRate: Int = PcmBuffer.DEFAULT_SAMPLE_RATE,
        seed: Long = 0L,
    ): PcmBuffer {
        val sequential = plan.elements.filterNot { it is ReferenceElement.DroneEvent }
        val rendered = sequential.mapIndexed { i, element -> renderElement(element, sampleRate, seed + 1000L * i) }
        return PcmBuffer(concat(rendered.map { it.samples }), sampleRate)
    }

    /**
     * The full item, per docs/06-AUDIO-ENGINE.md §7: "Full item audio is
     * rendered to a single contiguous buffer before playback begins." A
     * `CADENCE_FADE` L4 drone (docs/02-PEDAGOGY.md §3) is meant to sustain
     * *underneath the entire item*, not just before it — so any [ReferenceElement.DroneEvent]
     * in the plan is rendered as a separate underlay spanned across the
     * whole buffer and mixed in, rather than concatenated sequentially like
     * everything else.
     */
    fun renderItem(
        referencePlan: ReferencePlan,
        gapAfterReferenceMs: Long,
        targetMidi: Int,
        targetTimbre: TimbreId,
        targetDurationMs: Long,
        sampleRate: Int = PcmBuffer.DEFAULT_SAMPLE_RATE,
        seed: Long = 0L,
    ): PcmBuffer {
        val sequenceBuffer = renderReferencePlan(referencePlan, sampleRate, seed)
        val gapSilence = FloatArray(PcmBuffer.msToSamples(gapAfterReferenceMs, sampleRate))
        val targetBuffer = renderNote(targetMidi, targetTimbre, targetDurationMs, sampleRate, seed + 999_999L)

        var timeline = concat(listOf(sequenceBuffer.samples, gapSilence, targetBuffer.samples))

        val drones = referencePlan.elements.filterIsInstance<ReferenceElement.DroneEvent>()
        for ((i, drone) in drones.withIndex()) {
            val underlay = renderElement(drone.copy(durationMs = drone.durationMs), sampleRate, seed + 2000L * (i + 1))
            val fitted = FloatArray(timeline.size) { n -> if (n < underlay.samples.size) underlay.samples[n] else 0f }
            timeline = Dsp.mix(listOf(timeline, fitted))
        }

        return PcmBuffer(Dsp.softLimit(timeline), sampleRate)
    }

    private fun concat(buffers: List<FloatArray>): FloatArray {
        val total = buffers.sumOf { it.size }
        val out = FloatArray(total)
        var offset = 0
        for (buf in buffers) {
            System.arraycopy(buf, 0, out, offset, buf.size)
            offset += buf.size
        }
        return out
    }
}
