package com.tonic.core.model.rhythm

import com.tonic.core.model.items.AnswerAlphabet

/**
 * What a rhythm item actually asks — docs/40-PHASE-4-SPEC.md §3.3 and §5.1.
 *
 * A sealed interface rather than a mode flag beside a list of choices, because those two fields have
 * to agree and nothing would enforce it: a recognition item with no choices, or a production item with
 * them, would both typecheck. Here the question *is* the shape, so an item cannot be built in a state
 * that means nothing. The mode follows from the question rather than being asserted alongside it.
 *
 * Three questions, not two, because `M3.DOWNBEAT` is genuinely a third thing. §5.1 gives it
 * "identify which beat is 'one'", whose answers are positions in time rather than rhythms — trying to
 * express it as a choice between patterns is what stalled it at Stage 4.2.
 */
public sealed interface RhythmQuestion {
    /** The valid answers, in the form the attempt log stores. */
    public val answerAlphabet: AnswerAlphabet

    /** The label a correct answer carries. */
    public val correctLabel: String

    /**
     * "Tap that back." Production — perception plus motor execution, scored per §6.
     *
     * No alphabet, because what is scored is not a label but the tap timestamps against the pattern.
     */
    public data object TapItBack : RhythmQuestion {
        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.Tapped
        override val correctLabel: String = TAPPED
        public const val TAPPED: String = "TAPPED"
    }

    /**
     * "Which of these did you just hear?" — §3.3's recognition form.
     *
     * The choices are *played*, in order, and labelled by position. There is nothing else they could
     * honestly be labelled: this app shows no notation (docs/02-PEDAGOGY.md), and a rhythm has no name
     * the learner has been taught — §3.1 gives them syllables for positions within a beat, not names
     * for whole patterns. So the options are the sounds themselves, in the order they arrived.
     *
     * @property choices every pattern played, in playback order.
     * @property answerIndex which of them was the one asked about, zero-based.
     */
    public data class WhichPattern(
        public val choices: List<RhythmPattern>,
        public val answerIndex: Int,
    ) : RhythmQuestion {
        init {
            require(choices.size >= 2) { "A choice needs at least two options, was ${choices.size}" }
            require(answerIndex in choices.indices) { "The answer must be one of the choices offered" }
        }

        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.PatternChoice(choices.size)
        override val correctLabel: String = (answerIndex + 1).toString()
    }

    /**
     * "Where was 'one'?" — `M3.DOWNBEAT`, docs/40-PHASE-4-SPEC.md §5.1 and §3.4.
     *
     * §3.4 makes beat induction a first-class skill: "finding the beat in music that doesn't announce
     * it — hearing where 'one' is — is a genuine, trainable, frequently-absent skill." The presentation
     * follows from that phrase directly. **Playback begins part-way into the bar**, so the first beat
     * heard is usually not the downbeat and the learner has to feel where the bar turns over rather
     * than read it off the start of the audio. An item that always began on the downbeat would have the
     * same answer every time and would train nothing.
     *
     * [downbeatPosition] is one-based among the beats heard. It is *sometimes* 1, drawn from the seed
     * like everything else, because a node where the answer is never the first option teaches a
     * strategy rather than a skill — the same balanced-answer reasoning `M9` uses for its major/minor
     * coin.
     *
     * The metronome must not accent the downbeat on these items. The accent is the answer.
     *
     * @property beatsHeard how many beats the learner is offered to choose between.
     * @property downbeatPosition which of them was "one", one-based.
     */
    public data class WhichBeatIsOne(
        public val beatsHeard: Int,
        public val downbeatPosition: Int,
    ) : RhythmQuestion {
        init {
            require(beatsHeard >= 2) { "A choice needs at least two beats, was $beatsHeard" }
            require(downbeatPosition in 1..beatsHeard) {
                "The downbeat must be one of the beats heard: $downbeatPosition of $beatsHeard"
            }
        }

        override val answerAlphabet: AnswerAlphabet = AnswerAlphabet.PatternChoice(beatsHeard)
        override val correctLabel: String = downbeatPosition.toString()
    }
}
