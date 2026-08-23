package com.tonic.core.model.items

import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.PitchClass
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.MetronomePlan
import com.tonic.core.model.rhythm.RhythmPattern

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
     * `M9.*`: mode identification — docs/20-PHASE-2-SPEC.md §2.4. Something sounds; the learner says
     * whether it is major or minor. No degree is being named and no tonal *position* is being judged,
     * which is why this is its own item type rather than a recognition item with a two-button ladder.
     *
     * The three `M9` nodes differ only in how much support [presentation] gives, so one item type
     * covers all of them: a full cadence, then a bare tonic triad, then an unaccompanied melodic
     * fragment. That progression is the whole curriculum of the module.
     */
    data class ModeIdentificationItem(
        override val skill: SkillId,
        val key: PitchClass,
        /** The answer. What the learner has to hear. */
        val mode: Mode,
        val tonicMidi: Int,
        val presentation: ModePresentation,
        /** What actually sounds, already built for [presentation] and [mode]. */
        val elements: List<ReferenceElement>,
        val timbre: TimbreId,
        val timing: ItemTiming,
        override val seed: Long,
    ) : Item {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.MajorMinor

        /** The label a correct answer carries into the attempt log. */
        val correctLabel: String
            get() =
                when (mode) {
                    Mode.MAJOR -> AnswerAlphabet.MajorMinor.MAJOR
                    Mode.MINOR -> AnswerAlphabet.MajorMinor.MINOR
                }
    }

    /**
     * `M3.*`: rhythm — docs/40-PHASE-4-SPEC.md §8.
     *
     * One item type for both halves of the module, distinguished by [mode], because a recognition item
     * and a production item present *the same thing* and differ only in what the learner does next
     * (§3.3). Splitting them would duplicate the pattern, the tempo, the meter and the metronome plan
     * across two types that must never disagree about any of them — and they must not, because a
     * concept is introduced in recognition form and then produced, and the two are meant to be
     * recognizably the same exercise.
     *
     * The item carries no pitch at all. `M3` shares no skill node with the pitch track and a learner can
     * start `M3.BEAT_FIND` having never touched `M2` (§2), so there is no key, no degree and no
     * reference plan here — only [timbre], which is shared with the pitch track for the same
     * generalization reason it exists there.
     */
    data class RhythmItem(
        override val skill: SkillId,
        val meter: Meter,
        /** Beats per minute. Derived from `TEMPO_DEVIATION` via [com.tonic.core.model.rhythm.Tempo]. */
        val tempoBpm: Int,
        /** The rhythm to be heard, and — on a production item — tapped back. */
        val pattern: RhythmPattern,
        /** Exactly what the metronome does, from `METRONOME_FADE` (§3.2). */
        val metronomePlan: MetronomePlan,
        val mode: RhythmMode,
        /**
         * On a recognition item, the patterns offered as choices, in the order they are played;
         * [pattern] is one of them. Empty on a production item, which has nothing to choose between.
         */
        val choices: List<RhythmPattern> = emptyList(),
        val timbre: TimbreId,
        override val seed: Long,
    ) : Item {
        init {
            when (mode) {
                RhythmMode.RECOGNITION -> {
                    require(choices.size >= 2) { "A recognition item needs at least two choices" }
                    require(pattern in choices) { "The answer must be one of the choices offered" }
                }

                RhythmMode.PRODUCTION ->
                    require(choices.isEmpty()) { "A production item is tapped back, not chosen from" }
            }
        }

        override val answerAlphabet: AnswerAlphabet =
            when (mode) {
                RhythmMode.RECOGNITION -> AnswerAlphabet.PatternChoice(choices.size)
                RhythmMode.PRODUCTION -> AnswerAlphabet.Tapped
            }

        /**
         * The label a correct answer carries into the attempt log.
         *
         * On a recognition item that is the position of the right pattern among the choices. On a
         * production item there is no label to choose, so the pattern's own onsets stand in — the thing
         * scoring compares tap times against, and enough to reconstruct what was asked without the seed.
         */
        val correctLabel: String
            get() =
                when (mode) {
                    RhythmMode.RECOGNITION -> (choices.indexOf(pattern) + 1).toString()
                    RhythmMode.PRODUCTION -> pattern.onsetTicks.joinToString(",")
                }

        /** When each onset sounds, in milliseconds from the start of the pattern. */
        val onsetTimesMs: List<Double>
            get() {
                val msPerTick =
                    com.tonic.core.model.rhythm.Tempo
                        .msPerTick(tempoBpm)
                return pattern.onsetTicks.map { it * msPerTick }
            }
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

/**
 * How an `M9` item presents its mode — docs/20-PHASE-2-SPEC.md §3, in prerequisite order. Each step
 * removes harmonic scaffolding, so the mode has to be heard from less.
 */
enum class ModePresentation {
    /** A full cadence. The mode is stated four times over, by every chord in the progression. */
    CADENCE,

    /** A bare tonic triad, alone. One chord, and its third is the entire answer. */
    TONIC_TRIAD,

    /** A short unaccompanied melodic fragment. No harmony at all - the mode is carried by melody. */
    MELODIC_FRAGMENT,
}

/**
 * Whether a rhythm item is heard or produced — docs/40-PHASE-4-SPEC.md §3.3.
 *
 * "Every rhythmic concept is introduced in recognition form before production form." The reason is the
 * same structural argument docs/30-PHASE-3-SPEC.md §3 makes about singing: production conflates two
 * skills. A learner who perceives a syncopation correctly but taps it sloppily is not failing at rhythm
 * perception, and an app that cannot tell those apart will remediate the wrong one.
 *
 * It is also what makes §7.5 possible: recognition nodes form a complete path through every concept, so
 * a learner whose motor control makes accurate tapping impossible can still learn and demonstrate
 * rhythmic understanding.
 */
enum class RhythmMode {
    /** "Which of these did you just hear?" Tap timing is irrelevant; only perception is measured. */
    RECOGNITION,

    /** "Tap that back." Perception plus motor execution, scored per docs/40-PHASE-4-SPEC.md §6. */
    PRODUCTION,
}
