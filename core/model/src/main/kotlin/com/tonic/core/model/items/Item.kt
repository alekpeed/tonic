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
        /**
         * For an item whose own [referencePlan] is silent (an L1 group item, an L6/L7 block item): the
         * key establishment that Replay plays in front of the target, so a learner who lost hold of
         * "home" always has a way back. The default presentation stays silent — that retention demand
         * is the level's whole point — and every replay is recorded via `Attempt.replayCount`, so
         * leaning on this is visible in diagnostics rather than penalized (docs/08-UI-SPEC.md §4:
         * replay is "always available, unlimited, unpenalized"). Null when the item's own plan already
         * establishes the key.
         */
        val homeReminder: ReferencePlan? = null,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.ScaleDegrees(activeDegrees)
    }

    /**
     * `M12.*`: audiation. The inverse of [FunctionalRecognitionItem] — the learner is *told* which
     * degree is coming, holds it in their head across a silent gap, and then judges what actually
     * sounded (docs/20-PHASE-2-SPEC.md §2.3). This trains internal pitch generation rather than
     * reaction to a stimulus, which is the Gordon-derived endpoint in docs/02-PEDAGOGY.md §6.
     *
     * The order matters and is why this cannot be a variant of the recognition item: the reference
     * establishes the key, then [statedDegree] is shown *silently*, then nothing sounds for
     * [gapBeforeSoundedNoteMs], then one note plays. What is on screen during the gap is an
     * instruction, never a revealed answer (docs/20-PHASE-2-SPEC.md §5.3).
     */
    data class PredictionItem(
        override val skill: SkillId,
        val key: PitchClass,
        val mode: Mode,
        /** The degree named on screen — what the learner is asked to hear in their head. */
        val statedDegree: ScaleDegree,
        /** The pitch [statedDegree] actually denotes in this key and octave: what a match would sound like. */
        val statedMidi: Int,
        /** The pitch that actually sounds. Equal to [statedMidi] on a matching item. */
        val soundedMidi: Int,
        /**
         * Detuning applied to [soundedMidi], for `PREDICT_DEVIATION` level 3 ("same degree detuned 30
         * cents"). Zero at every other level — a deviation is otherwise a different note, not a bent one.
         */
        val soundedCentsOffset: Double = 0.0,
        val referencePlan: ReferencePlan,
        val timbre: TimbreId,
        val referenceTimbre: TimbreId,
        val timing: ItemTiming,
        /** Silent audiation window between the stated degree appearing and the note sounding. */
        val gapBeforeSoundedNoteMs: Long,
        /** The degree pool this node draws [statedDegree] from — `{1,3,5}` at `M12.PREDICT_TRIAD`, wider later. */
        val activeDegrees: List<ScaleDegree>,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.MatchDirection

        /** True when the sounded pitch is exactly what was named — no note substitution and no detuning. */
        val matches: Boolean get() = soundedMidi == statedMidi && soundedCentsOffset == 0.0

        /**
         * The directionally-correct answer. `M12.PREDICT_TRIAD` scores against the *collapsed* form
         * instead (docs/20-PHASE-2-SPEC.md §8.1 decision 3) — see
         * [AnswerAlphabet.MatchDirection.matchedVsNot]; a learner who hears the mismatch but cannot yet
         * name its direction is not penalized there for a skill that belongs to `M1.HIGH_LOW`.
         *
         * Compared in cents rather than MIDI numbers so a detuned deviation resolves to a direction
         * rather than collapsing to "matched" on an equal integer.
         */
        val correctLabel: String
            get() {
                val soundedCents = soundedMidi * CENTS_PER_SEMITONE + soundedCentsOffset
                val statedCents = statedMidi.toDouble() * CENTS_PER_SEMITONE
                return when {
                    soundedCents == statedCents -> AnswerAlphabet.MatchDirection.MATCHED
                    soundedCents < statedCents -> AnswerAlphabet.MatchDirection.TOO_LOW
                    else -> AnswerAlphabet.MatchDirection.TOO_HIGH
                }
            }

        private companion object {
            const val CENTS_PER_SEMITONE = 100.0
        }
    }
}
