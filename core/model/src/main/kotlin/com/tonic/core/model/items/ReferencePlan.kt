package com.tonic.core.model.items

import com.tonic.core.model.music.TimbreId

/**
 * What plays before the target note, derived from a [CadenceFadeLevel] by
 * `:core:curriculum`'s `ReferencePlanBuilder` (Stage 3) and rendered to
 * audio by `:core:audio` (Stage 2). docs/06-AUDIO-ENGINE.md §5: "the audio
 * layer must not decide *what* the reference is — it only decides how it
 * sounds," which is why this type is pure data owned by `:core:model`.
 */
data class ReferencePlan(
    val cadenceFadeLevel: CadenceFadeLevel,
    val elements: List<ReferenceElement>,
    /** Marks the L1 optimization: this plan may be reused for 2–3 subsequent items without re-requesting it. */
    val reusableForItems: Int = 1,
) {
    /**
     * How long this plan's *sequential* part actually sounds, in ms — the chords/tones that play
     * before the gap and target. Drones are excluded: they underlay the whole item rather than
     * preceding it. The practice screen's phase indicator times its "reference" phase from this;
     * an earlier revision assumed the reference lasted one `referenceDurationMs`, which for a
     * four-chord cadence claimed "here comes the question" while three chords were still playing,
     * and claimed "setting up home" over pure silence for plans with no elements at all.
     */
    val sequentialDurationMs: Long
        get() = elements.filterNot { it is ReferenceElement.DroneEvent }.sumOf { it.durationMs }
}

/**
 * One event in a [ReferencePlan]'s timeline. `midiNotes` on [ChordEvent]
 * carries more than one note (block chords); everything else is
 * monophonic.
 */
sealed interface ReferenceElement {
    val durationMs: Long

    data class ChordEvent(
        val midiNotes: List<Int>,
        override val durationMs: Long,
        val timbre: TimbreId,
        /** Per-voice onset jitter in ms so chords don't sound synthetically fused. Seeded, not random — docs/06-AUDIO-ENGINE.md §5. */
        val voiceJitterMs: List<Long> = emptyList(),
    ) : ReferenceElement

    data class ToneEvent(
        val midi: Int,
        override val durationMs: Long,
        val timbre: TimbreId,
    ) : ReferenceElement

    /** L4: tonic root sustained under the entire item, at a fixed dB offset relative to the target. */
    data class DroneEvent(
        val midi: Int,
        override val durationMs: Long,
        val timbre: TimbreId,
        val relativeDb: Double,
    ) : ReferenceElement

    data class Silence(
        override val durationMs: Long,
    ) : ReferenceElement
}
