package com.tonic.core.curriculum.generators

import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.items.ReferencePlan
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.TimbreId
import kotlin.random.Random

/**
 * Turns a `CADENCE_FADE` level into a concrete [ReferencePlan] — the
 * decision docs/06-AUDIO-ENGINE.md §5 explicitly assigns to `:core:curriculum`,
 * not `:core:audio`: "the audio layer must not decide *what* the reference
 * is — it only decides how it sounds." The level -> structure table is
 * docs/02-PEDAGOGY.md §3 combined with docs/06-AUDIO-ENGINE.md §5's
 * render-detail table.
 *
 * L6/L7 return an empty plan here deliberately: "key established once at
 * block start" (L6) and "the block's inter-item gap growing linearly across
 * the block" (L7) are block-level scheduling concerns, not per-item
 * content — that's `SessionComposer`'s job (Stage 4/6), not this builder's.
 */
object ReferencePlanBuilder {
    fun build(
        cadenceFadeLevel: CadenceFadeLevel,
        keyPitchClass: PitchClass,
        mode: Mode,
        tonicMidi: Int,
        timbre: TimbreId,
        chordDurationMs: Long,
        gapAfterReferenceMs: Long,
        targetDurationMs: Long,
        seed: Long,
    ): ReferencePlan =
        when (cadenceFadeLevel) {
            CadenceFadeLevel.L0 ->
                ReferencePlan(
                    cadenceFadeLevel,
                    cadenceProgression(keyPitchClass, mode, tonicMidi, timbre, chordDurationMs, seed),
                )

            CadenceFadeLevel.L1 ->
                ReferencePlan(
                    cadenceFadeLevel,
                    cadenceProgression(keyPitchClass, mode, tonicMidi, timbre, chordDurationMs, seed),
                    // "Full cadence, then 2-3 items answered before it repeats" - docs/03-CURRICULUM.md §5.3 gives a
                    // range, not an exact count; 3 is this implementation's fixed choice within that range.
                    reusableForItems = 3,
                )

            CadenceFadeLevel.L2 -> {
                val dominant =
                    triadChord(keyPitchClass, mode, tonicMidi, rootDegreeStep = 5, timbre, chordDurationMs, seed)
                val tonic =
                    triadChord(
                        keyPitchClass,
                        mode,
                        tonicMidi,
                        rootDegreeStep = 1,
                        timbre,
                        chordDurationMs,
                        seed + 1,
                    )
                ReferencePlan(cadenceFadeLevel, listOf(dominant, tonic))
            }

            CadenceFadeLevel.L3 -> {
                // "~800ms" is stated as a fixed duration in docs/06-AUDIO-ENGINE.md §5, not tempo-scaled.
                val tonic =
                    triadChord(keyPitchClass, mode, tonicMidi, rootDegreeStep = 1, timbre, durationMs = 800, seed)
                ReferencePlan(cadenceFadeLevel, listOf(tonic))
            }

            CadenceFadeLevel.L4 -> {
                // Spans the whole item (gap + target) - there's no preceding sequential reference at this
                // level. SynthEngine.renderItem treats a DroneEvent as an underlay, not sequential content.
                val totalSpanMs = gapAfterReferenceMs + targetDurationMs
                ReferencePlan(
                    cadenceFadeLevel,
                    listOf(
                        ReferenceElement.DroneEvent(
                            midi = tonicMidi,
                            durationMs = totalSpanMs,
                            timbre = timbre,
                            relativeDb = -18.0,
                        ),
                    ),
                )
            }

            CadenceFadeLevel.L5 ->
                ReferencePlan(
                    cadenceFadeLevel,
                    listOf(
                        ReferenceElement.ToneEvent(midi = tonicMidi, durationMs = 400, timbre = timbre),
                        // "a silent gap (default 1500 ms)" - docs/06-AUDIO-ENGINE.md §5. Fixed default, not tempo-scaled.
                        ReferenceElement.Silence(durationMs = 1500),
                    ),
                )

            CadenceFadeLevel.L6, CadenceFadeLevel.L7 -> ReferencePlan(cadenceFadeLevel, emptyList())
        }

    private fun cadenceProgression(
        key: PitchClass,
        mode: Mode,
        tonicMidi: Int,
        timbre: TimbreId,
        chordDurationMs: Long,
        seed: Long,
    ): List<ReferenceElement> =
        listOf(1, 4, 5, 1).mapIndexed { i, degreeStep ->
            triadChord(key, mode, tonicMidi, degreeStep, timbre, chordDurationMs, seed + i)
        }

    /** Root-position triad built on the given scale step (1-indexed; steps beyond 7 wrap up an octave). */
    private fun triadChord(
        key: PitchClass,
        mode: Mode,
        tonicMidi: Int,
        rootDegreeStep: Int,
        timbre: TimbreId,
        durationMs: Long,
        seed: Long,
    ): ReferenceElement.ChordEvent {
        val notes =
            listOf(rootDegreeStep, rootDegreeStep + 2, rootDegreeStep + 4).map { step ->
                midiForScaleStep(tonicMidi, step, mode)
            }
        val rng = Random(seed)
        // Per-voice onset jitter so chords don't sound synthetically fused - docs/06-AUDIO-ENGINE.md §5,
        // "seeded, not random."
        val jitter = notes.indices.map { if (it == 0) 0L else rng.nextLong(0, 9) }
        return ReferenceElement.ChordEvent(notes, durationMs, timbre, jitter)
    }

    /**
     * MIDI note for scale step [step] (1-indexed, may exceed 7 to reach into
     * the next octave — e.g. a triad rooted on degree 4 needs steps 4, 6, 8).
     */
    private fun midiForScaleStep(
        tonicMidi: Int,
        step: Int,
        mode: Mode,
    ): Int {
        val zeroIndexed = step - 1
        val degreeInOctave = Math.floorMod(zeroIndexed, 7) + 1
        val octaveOffset = Math.floorDiv(zeroIndexed, 7) * 12
        return tonicMidi + ScaleDegree(degreeInOctave).semitoneOffset(mode) + octaveOffset
    }
}
