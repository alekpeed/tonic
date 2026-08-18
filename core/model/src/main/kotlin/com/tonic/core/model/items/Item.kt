package com.tonic.core.model.items

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.TimbreId

/**
 * One generated exercise, of whichever skill produced it. Every generator
 * across M0/M1/M2 is a pure function `(skillId, params, seed) -> Item`
 * (CLAUDE.md §5) — the [seed] on every variant is what makes a session
 * fully replayable (docs/04-ARCHITECTURE.md §4).
 */
sealed interface Item {
    val skill: SkillId
    val answerAlphabet: AnswerAlphabet
    val seed: Long

    /** `M0.PITCH_DIR` / `M1.HIGH_LOW`: two tones, sequential, same timbre. Was the second higher or lower? */
    data class PitchDirectionItem(
        override val skill: SkillId,
        val firstMidi: Int,
        val secondCentsOffset: Double,
        val timbre: TimbreId,
        val timing: ItemTiming,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.HigherLower
    }

    /** `M0.SAME_DIFF` / `M1.SAME_DIFF`: two tones, sometimes identical. */
    data class SameDifferentItem(
        override val skill: SkillId,
        val firstMidi: Int,
        val centsOffset: Double,
        val isCatchTrial: Boolean,
        val timbre: TimbreId,
        val timing: ItemTiming,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.SameDifferent
    }

    /** `M0.TONAL_MEMORY`: a short tone sequence, then a replay with at most one note altered. */
    data class TonalMemoryItem(
        override val skill: SkillId,
        val sequenceMidi: List<Int>,
        val alteredIndex: Int?,
        val alterationCents: Double,
        val timbre: TimbreId,
        val timing: ItemTiming,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.SameDifferent
    }

    /** `M1.CONTOUR`: a 3-note figure — did it rise, fall, or turn. */
    data class ContourItem(
        override val skill: SkillId,
        val figureMidi: List<Int>,
        val timbre: TimbreId,
        val timing: ItemTiming,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.Contour
    }

    /** `M1.STEP_LEAP`: was that a small move or a big move. */
    data class StepLeapItem(
        override val skill: SkillId,
        val firstMidi: Int,
        val secondMidi: Int,
        val timbre: TimbreId,
        val timing: ItemTiming,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.StepLeap
    }

    /**
     * `M0.AMUSIA_SCREEN`: an original, app-composed phrase, intact or with
     * one pitch displaced out of key. See docs/02-PEDAGOGY.md §8 — the
     * result of this item type is never shown to the user as a diagnosis.
     */
    data class AmusiaScreenItem(
        override val skill: SkillId,
        val phraseMidi: List<Int>,
        val isAltered: Boolean,
        val alteredIndex: Int?,
        val alterationSemitones: Int,
        val timbre: TimbreId,
        val timing: ItemTiming,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.IntactAltered
    }

    /**
     * `M2.*`: the core loop. A key is established per [referencePlan], a
     * target note sounds, the user names its scale degree. See
     * docs/03-CURRICULUM.md §5.1/§5.4.
     */
    data class FunctionalRecognitionItem(
        override val skill: SkillId,
        val key: PitchClass,
        val mode: Mode,
        val targetDegree: ScaleDegree,
        val targetMidi: Int,
        val referencePlan: ReferencePlan,
        val timbre: TimbreId,
        val referenceTimbre: TimbreId,
        val timing: ItemTiming,
        val activeDegrees: List<ScaleDegree>,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.ScaleDegrees(activeDegrees)
    }
}
