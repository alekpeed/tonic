package com.tonic.core.model.rhythm

import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.items.RhythmMode
import com.tonic.core.model.music.TimbreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `Item.RhythmItem` — docs/40-PHASE-4-SPEC.md §8, and §3.3's recognition-before-production split.
 *
 * One item type serves both halves of the module because a recognition item and a production item
 * present the *same thing* and differ only in what the learner does next. These assertions are what
 * keeps that from becoming a type where half the fields are meaningless: a recognition item without
 * choices, or a production item with them, is refused at construction rather than surviving to confuse
 * a screen.
 */
class RhythmItemTest {
    private val meter = Meter.FOUR_FOUR
    private val plain = RhythmPattern(meter, bars = 1, onsetTicks = listOf(0, 12, 24, 36))
    private val syncopated = RhythmPattern(meter, bars = 1, onsetTicks = listOf(0, 6, 24, 36))
    private val plan = MetronomePlanner.plan(MetronomeFadeLevel.L1, meter, bars = 1)

    private fun item(
        question: RhythmQuestion,
        pattern: RhythmPattern = plain,
        tempoBpm: Int = 100,
    ) = Item.RhythmItem(
        skill = SkillIds.M3_BEAT_FIND,
        meter = meter,
        tempoBpm = tempoBpm,
        pattern = pattern,
        metronomePlan = plan,
        question = question,
        timbre = TimbreId.PURE,
        seed = 1L,
    )

    @Test
    fun `a production item is tapped, with nothing to choose from`() {
        val production = item(RhythmQuestion.TapItBack)
        assertEquals(AnswerAlphabet.Tapped, production.answerAlphabet)
        assertTrue(production.answerAlphabet.labels.isEmpty())
        assertEquals(RhythmMode.PRODUCTION, production.mode)
    }

    @Test
    fun `a pattern-choice item labels its choices by figure, not position`() {
        // docs/40-PHASE-4-SPEC.md §8. A position label names a different rhythm in every item, so a
        // confusion matrix over positions would accumulate cells that mean nothing. The figure at the
        // beat where the choices diverge is what the learner actually had to hear.
        val recognition =
            item(RhythmQuestion.WhichPattern(choices = listOf(plain, syncopated), answerIndex = 0))
        val alphabet = recognition.answerAlphabet
        assertIs<AnswerAlphabet.PatternChoice>(alphabet)
        // The two patterns agree on beat 0 and diverge on beat 1: plain sounds `ta`, syncopated has
        // nothing on the beat and a sound half a beat early, which reads as `di`.
        assertEquals(listOf("ta", "ta-di"), alphabet.labels)
        assertEquals("ta", recognition.correctLabel)
        assertEquals(RhythmMode.RECOGNITION, recognition.mode)
    }

    @Test
    fun `the correct label follows the answer wherever it sits among the choices`() {
        val recognition =
            item(RhythmQuestion.WhichPattern(choices = listOf(plain, syncopated), answerIndex = 1))
        assertEquals("ta-di", recognition.correctLabel)
        // And the screen can still map back to the option the learner was shown.
        val question = recognition.question
        assertIs<RhythmQuestion.WhichPattern>(question)
        assertEquals(2, question.positionOf("ta-di"))
    }

    @Test
    fun `a downbeat item asks which beat was one`() {
        // M3.DOWNBEAT's answers are positions in time rather than rhythms - the distinction that made
        // it a question of its own rather than a choice between patterns.
        val downbeat = item(RhythmQuestion.WhichBeatIsOne(beatsHeard = 4, downbeatPosition = 3))
        assertEquals(listOf("1", "2", "3", "4"), downbeat.answerAlphabet.labels)
        assertEquals("3", downbeat.correctLabel)
        assertEquals(RhythmMode.RECOGNITION, downbeat.mode)
    }

    @Test
    fun `a question that means nothing cannot be built`() {
        // The reason the question is a sealed type rather than a mode flag beside a list: there is no
        // longer a state where an item claims to be a recognition item and offers nothing to choose
        // between, because the two facts are one fact.
        assertFailsWith<IllegalArgumentException>("one choice is not a choice") {
            RhythmQuestion.WhichPattern(choices = listOf(plain), answerIndex = 0)
        }
        assertFailsWith<IllegalArgumentException>("the answer must be among the choices") {
            RhythmQuestion.WhichPattern(choices = listOf(plain, syncopated), answerIndex = 2)
        }
        assertFailsWith<IllegalArgumentException>("a downbeat must be one of the beats heard") {
            RhythmQuestion.WhichBeatIsOne(beatsHeard = 4, downbeatPosition = 5)
        }
        assertFailsWith<IllegalArgumentException>("one beat is not a choice") {
            RhythmQuestion.WhichBeatIsOne(beatsHeard = 1, downbeatPosition = 1)
        }
    }

    @Test
    fun `onset times follow the tempo, not the tick grid`() {
        val atHundred = item(RhythmQuestion.TapItBack)
        assertEquals(listOf(0.0, 600.0, 1200.0, 1800.0), atHundred.onsetTimesMs)

        // Twice the tempo, half the elapsed time. The ticks did not move; only the rendering of them.
        val atTwoHundred = item(RhythmQuestion.TapItBack, tempoBpm = 200)
        assertEquals(listOf(0.0, 300.0, 600.0, 900.0), atTwoHundred.onsetTimesMs)
    }

    @Test
    fun `an alphabet that cannot identify a choice is refused`() {
        assertFailsWith<IllegalArgumentException>("one option is not a choice") {
            AnswerAlphabet.PatternChoice(listOf("ta"))
        }
        // Two choices sharing a label would make the attempt log unable to say which rhythm the
        // learner picked - the whole point of labelling by figure rather than by position.
        assertFailsWith<IllegalArgumentException>("duplicate labels") {
            AnswerAlphabet.PatternChoice(listOf("ta", "ta"))
        }
    }
}
