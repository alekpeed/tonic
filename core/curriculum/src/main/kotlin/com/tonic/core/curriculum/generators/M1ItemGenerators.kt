package com.tonic.core.curriculum.generators

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.ItemTiming
import com.tonic.core.model.music.TimbreId
import kotlin.random.Random

/** The two item types unique to `M1` (the rest are shared with `M0` — see [M0ItemGenerators]). */
object M1ItemGenerators {
    private val DEFAULT_TIMING =
        ItemTiming(referenceDurationMs = 500, gapAfterReferenceMs = 300, targetDurationMs = 500)
    private val SAFE_MIDI_RANGE = 48..84

    /** `M1.CONTOUR`: a 3-note figure that rises, falls, or turns. */
    fun generateContour(
        skill: SkillId,
        minStepCents: Double,
        timbre: TimbreId,
        seed: Long,
    ): Item.ContourItem {
        val random = Random(seed)
        val shape = AnswerAlphabet.Contour.labels[random.nextInt(AnswerAlphabet.Contour.labels.size)]
        val start = random.nextInt(SAFE_MIDI_RANGE.first + 4, SAFE_MIDI_RANGE.last - 4)
        val stepSemitones = (minStepCents / 100.0).let { it.toInt().coerceAtLeast(1) }

        val figure =
            when (shape) {
                AnswerAlphabet.Contour.UP -> listOf(start, start + stepSemitones, start + 2 * stepSemitones)
                AnswerAlphabet.Contour.DOWN -> listOf(start, start - stepSemitones, start - 2 * stepSemitones)
                AnswerAlphabet.Contour.UP_DOWN -> listOf(start, start + stepSemitones, start)
                AnswerAlphabet.Contour.DOWN_UP -> listOf(start, start - stepSemitones, start)
                else -> error("unreachable: unexpected contour label $shape")
            }

        return Item.ContourItem(
            skill = skill,
            figureMidi = figure,
            timbre = timbre,
            timing = DEFAULT_TIMING,
            seed = seed,
        )
    }

    /**
     * `M1.STEP_LEAP`: was that a small move (<=2 semitones) or a big move
     * (leap). `firstMidi` reserves enough margin from both ends of
     * [SAFE_MIDI_RANGE] for the largest possible leap (12 semitones) in
     * either direction, so `secondMidi` never needs clamping — clamping it
     * instead could silently shrink a leap into a step and corrupt the
     * item's own correct answer.
     */
    fun generateStepLeap(
        skill: SkillId,
        timbre: TimbreId,
        seed: Long,
    ): Item.StepLeapItem {
        val random = Random(seed)
        val maxInterval = 12
        val firstMidi = random.nextInt(SAFE_MIDI_RANGE.first + maxInterval, SAFE_MIDI_RANGE.last - maxInterval + 1)
        val isStep = random.nextBoolean()
        val interval = if (isStep) random.nextInt(1, 3) else random.nextInt(4, maxInterval + 1)
        val direction = if (random.nextBoolean()) 1 else -1
        val secondMidi = firstMidi + direction * interval

        return Item.StepLeapItem(
            skill = skill,
            firstMidi = firstMidi,
            secondMidi = secondMidi,
            timbre = timbre,
            timing = DEFAULT_TIMING,
            seed = seed,
        )
    }
}
