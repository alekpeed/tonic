package com.tonic.core.curriculum.generators

import com.tonic.core.curriculum.sampling.KeySampler
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ItemTiming
import com.tonic.core.model.items.ModePresentation
import com.tonic.core.model.items.ReferenceElement
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.TimbreId
import kotlin.random.Random

/**
 * `M9.*` item generation — docs/20-PHASE-2-SPEC.md §2.4 and §3. Pure and deterministic given
 * `(skill, seed, history)`, the same contract every other generator holds to (CLAUDE.md §5).
 *
 * The task is: something sounds, is it major or minor. The three nodes strip support away in order —
 * a full cadence states the mode four times, a bare tonic triad states it once, and an unaccompanied
 * melodic fragment states it with no harmony at all.
 *
 * **The minor cadence here is all-natural-minor, `i–iv–v–i`** (docs/20-PHASE-2-SPEC.md §8.1 decision
 * 4). For `M9` specifically that is not merely the node-appropriate default but the only sound choice:
 * with a major V, the dominant chord is *identical in both modes*, so a third of the progression would
 * carry no information about the very thing being asked. A learner could reasonably attend to the V
 * and hear nothing. Natural minor keeps every chord in the progression discriminating.
 */
object M9ItemGenerator {
    fun generate(
        skill: SkillId,
        seed: Long,
        history: GenerationHistory = GenerationHistory(),
    ): M9GenerationResult {
        val random = Random(seed)

        // Mode is the answer, so it must be balanced: a generator that drifted toward one mode would
        // reward a learner for guessing it, and d-prime would still certify them if the imbalance were
        // large enough. Alternating from a seeded coin keeps it even over any window.
        val mode = if (random.nextBoolean()) Mode.MAJOR else Mode.MINOR

        val keyValue = KeySampler.pick(ALL_KEYS, history.recentKeys, random)
        val key = PitchClass(keyValue)
        val tonicMidi = TONIC_MIDI_FLOOR + keyValue
        val timbre = TIMBRES[random.nextInt(TIMBRES.size)]
        val presentation = presentationFor(skill)

        val elements =
            when (presentation) {
                ModePresentation.CADENCE ->
                    ReferencePlanBuilder.keyEstablishment(
                        keyPitchClass = key,
                        mode = mode,
                        tonicMidi = tonicMidi,
                        timbre = timbre,
                        chordDurationMs = CHORD_DURATION_MS,
                        seed = seed,
                    )

                ModePresentation.TONIC_TRIAD -> listOf(tonicTriad(mode, tonicMidi, timbre, seed))

                ModePresentation.MELODIC_FRAGMENT -> melodicFragment(mode, tonicMidi, timbre, random)
            }

        val item =
            Item.ModeIdentificationItem(
                skill = skill,
                key = key,
                mode = mode,
                tonicMidi = tonicMidi,
                presentation = presentation,
                elements = elements,
                timbre = timbre,
                timing =
                    ItemTiming(
                        referenceDurationMs = elements.sumOf { it.durationMs },
                        gapAfterReferenceMs = 0L,
                        targetDurationMs = 0L,
                    ),
                seed = seed,
            )

        return M9GenerationResult(
            item = item,
            updatedHistory = history.copy(recentKeys = (history.recentKeys + keyValue).takeLast(KEY_HISTORY)),
        )
    }

    private fun presentationFor(skill: SkillId): ModePresentation =
        when (skill) {
            SkillIds.M9_MODE_ID_CADENCE -> ModePresentation.CADENCE
            SkillIds.M9_MODE_ID_TRIAD -> ModePresentation.TONIC_TRIAD
            SkillIds.M9_MODE_ID_MELODIC -> ModePresentation.MELODIC_FRAGMENT
            else -> error("Not an M9 skill node: $skill")
        }

    /**
     * The bare tonic triad. Its third is the entire answer — 4 semitones above the root in major, 3 in
     * minor — which is why this node is harder than the cadence and easier than the melody.
     */
    private fun tonicTriad(
        mode: Mode,
        tonicMidi: Int,
        timbre: TimbreId,
        seed: Long,
    ): ReferenceElement.ChordEvent {
        val notes = TRIAD_STEPS.map { step -> tonicMidi + mode.degreeAtStep(step).semitoneOffset(mode) }
        val rng = Random(seed)
        val jitter = notes.indices.map { if (it == 0) 0L else rng.nextLong(0, 9) }
        return ReferenceElement.ChordEvent(notes, TRIAD_DURATION_MS, timbre, jitter)
    }

    /**
     * An unaccompanied fragment, no harmony at all. Every shape below both starts and ends on the
     * tonic and passes through the third, because the third is what distinguishes the modes and a
     * fragment that never sounded it would be unanswerable rather than merely hard — the failure mode
     * docs/07-ADAPTIVE-ENGINE.md §2a exists to prevent.
     */
    private fun melodicFragment(
        mode: Mode,
        tonicMidi: Int,
        timbre: TimbreId,
        random: Random,
    ): List<ReferenceElement> {
        val shape = FRAGMENT_SHAPES[random.nextInt(FRAGMENT_SHAPES.size)]
        return shape.map { step ->
            ReferenceElement.ToneEvent(
                midi = tonicMidi + mode.degreeAtStep(step).semitoneOffset(mode) + if (step > 7) OCTAVE else 0,
                durationMs = FRAGMENT_NOTE_MS,
                timbre = timbre,
            )
        }
    }

    private const val OCTAVE = 12
    private const val TONIC_MIDI_FLOOR = 60
    private const val CHORD_DURATION_MS = 900L
    private const val TRIAD_DURATION_MS = 1_400L
    private const val FRAGMENT_NOTE_MS = 420L
    private const val KEY_HISTORY = 8

    private val ALL_KEYS = (0..11).toList()
    private val TRIAD_STEPS = listOf(1, 3, 5)
    private val TIMBRES = listOf(TimbreId.PURE, TimbreId.SOFT)

    /**
     * Scale steps, 1-indexed. Each rises to the third or fifth and settles back on 1, so the tonic is
     * unambiguous and the third is always heard.
     */
    private val FRAGMENT_SHAPES =
        listOf(
            listOf(1, 2, 3, 2, 1),
            listOf(1, 3, 5, 3, 1),
            listOf(1, 5, 3, 2, 1),
            listOf(1, 3, 2, 3, 1),
        )
}

data class M9GenerationResult(
    val item: Item.ModeIdentificationItem,
    val updatedHistory: GenerationHistory,
)
