package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ItemTiming
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.music.Tuning
import kotlin.random.Random

/**
 * `M0` (and reused for the structurally identical `M1` remediation)
 * generators — docs/03-CURRICULUM.md §3/§4. Difficulty (interval size in
 * cents, sequence length, ...) is threaded in as an explicit parameter,
 * decided by the staircase (`:core:engine`, Stage 4) — these functions
 * don't decide *how hard*, only *what the item is* at a given difficulty,
 * matching CLAUDE.md §5's pure `(skillId, params, seed) -> Item` contract.
 */
object M0ItemGenerators {
    private val DEFAULT_TIMING =
        ItemTiming(referenceDurationMs = 500, gapAfterReferenceMs = 300, targetDurationMs = 500)
    private val SAFE_MIDI_RANGE = 48..84

    /** `M0.PITCH_DIR` / `M1.HIGH_LOW` — docs/03-CURRICULUM.md §3/§4. */
    fun generatePitchDirection(
        skill: SkillId,
        differenceCents: Double,
        timbre: TimbreId,
        seed: Long,
    ): Item.PitchDirectionItem {
        require(differenceCents > 0) { "differenceCents must be positive; direction is encoded separately" }
        val random = Random(seed)
        val firstMidi = random.nextInt(SAFE_MIDI_RANGE.first, SAFE_MIDI_RANGE.last + 1)
        val direction = if (random.nextBoolean()) 1 else -1
        return Item.PitchDirectionItem(
            skill = skill,
            firstMidi = firstMidi,
            secondCentsOffset = direction * differenceCents,
            timbre = timbre,
            timing = DEFAULT_TIMING,
            seed = seed,
        )
    }

    /**
     * `M0.SAME_DIFF` / `M1.SAME_DIFF`. [differenceCents] is only used when
     * this trial isn't a catch trial — docs/03-CURRICULUM.md §3: "50% catch
     * trials with truly identical tones," scored by d-prime rather than raw
     * accuracy so an indiscriminate "different" answerer isn't scored as
     * sensitive.
     */
    fun generateSameDifferent(
        skill: SkillId,
        differenceCents: Double,
        timbre: TimbreId,
        seed: Long,
        catchTrialProbability: Double = 0.5,
    ): Item.SameDifferentItem {
        val random = Random(seed)
        val firstMidi = random.nextInt(SAFE_MIDI_RANGE.first, SAFE_MIDI_RANGE.last + 1)
        val isCatchTrial = random.nextDouble() < catchTrialProbability
        return Item.SameDifferentItem(
            skill = skill,
            firstMidi = firstMidi,
            centsOffset = if (isCatchTrial) 0.0 else differenceCents,
            isCatchTrial = isCatchTrial,
            timbre = timbre,
            timing = DEFAULT_TIMING,
            seed = seed,
        )
    }

    /**
     * `M0.TONAL_MEMORY`: a short sequence, replayed with at most one note
     * possibly altered. Same same/different catch-trial structure as
     * [generateSameDifferent] — [sameProbability] is the chance the replay
     * is unaltered.
     */
    fun generateTonalMemory(
        skill: SkillId,
        sequenceLength: Int,
        alterationCents: Double,
        timbre: TimbreId,
        seed: Long,
        sameProbability: Double = 0.5,
    ): Item.TonalMemoryItem {
        require(sequenceLength in 2..8) { "sequenceLength out of the documented 2..5+ range: $sequenceLength" }
        val random = Random(seed)
        val sequence =
            generateSequence(random.nextInt(SAFE_MIDI_RANGE.first, SAFE_MIDI_RANGE.last + 1)) { prev ->
                (prev + random.nextInt(-4, 5)).coerceIn(SAFE_MIDI_RANGE)
            }.take(sequenceLength).toList()

        val isSame = random.nextDouble() < sameProbability
        val alteredIndex = if (isSame) null else random.nextInt(sequence.size)

        return Item.TonalMemoryItem(
            skill = skill,
            sequenceMidi = sequence,
            alteredIndex = alteredIndex,
            alterationCents = if (isSame) 0.0 else alterationCents,
            timbre = timbre,
            timing = DEFAULT_TIMING,
            seed = seed,
        )
    }

    /**
     * `M0.AMUSIA_SCREEN`: an original, app-composed phrase, intact or with
     * one pitch displaced out of key. [isAltered]/[alterationSemitones] are
     * decided by the caller, which owns the 8-intact/8-altered balance
     * across the 16-item screen (docs/03-CURRICULUM.md §3) — this function
     * only builds one phrase.
     */
    fun generateAmusiaScreen(
        skill: SkillId,
        isAltered: Boolean,
        alterationSemitones: Int,
        timbre: TimbreId,
        seed: Long,
    ): Item.AmusiaScreenItem {
        require(alterationSemitones in 1..3) { "alterationSemitones must be 1..3 per docs/03-CURRICULUM.md §3" }
        val random = Random(seed)
        val keyTonic = random.nextInt(SAFE_MIDI_RANGE.first, SAFE_MIDI_RANGE.first + 12)
        val degreeSteps = intArrayOf(1, 3, 2, 4, 5, 3) // a short, deliberately simple original diatonic figure
        val phrase =
            degreeSteps.map { step ->
                keyTonic +
                    ScaleDegree(step).semitoneOffset(com.tonic.core.model.music.Mode.MAJOR)
            }

        val alteredIndex = if (isAltered) random.nextInt(1, phrase.size) else null // never alter the opening tonic
        val finalPhrase =
            if (alteredIndex != null) {
                phrase.mapIndexed { i, midi -> if (i == alteredIndex) midi + alterationSemitones else midi }
            } else {
                phrase
            }

        return Item.AmusiaScreenItem(
            skill = skill,
            phraseMidi = finalPhrase,
            isAltered = isAltered,
            alteredIndex = alteredIndex,
            alterationSemitones = alterationSemitones,
            timbre = timbre,
            timing = DEFAULT_TIMING,
            seed = seed,
        )
    }

    /** Not used by the generators above directly, but shared with M0/M1 diagnostic scoring: cents -> Hz helper. */
    fun centsToRatio(cents: Double): Double = Tuning.offsetByCents(1.0, cents)
}
