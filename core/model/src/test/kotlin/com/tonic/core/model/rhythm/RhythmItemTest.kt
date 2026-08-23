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
        mode: RhythmMode,
        pattern: RhythmPattern = plain,
        choices: List<RhythmPattern> = emptyList(),
        tempoBpm: Int = 100,
    ) = Item.RhythmItem(
        skill = SkillIds.M3_BEAT_FIND,
        meter = meter,
        tempoBpm = tempoBpm,
        pattern = pattern,
        metronomePlan = plan,
        mode = mode,
        choices = choices,
        timbre = TimbreId.PURE,
        seed = 1L,
    )

    @Test
    fun `a production item is tapped, with nothing to choose from`() {
        val production = item(RhythmMode.PRODUCTION)
        assertEquals(AnswerAlphabet.Tapped, production.answerAlphabet)
        assertTrue(production.answerAlphabet.labels.isEmpty())
    }

    @Test
    fun `a recognition item offers its choices as positions`() {
        // Labels are positions because there is nothing else they could honestly be: this app shows no
        // notation and a rhythm has no name the learner has been taught.
        val recognition = item(RhythmMode.RECOGNITION, choices = listOf(plain, syncopated))
        val alphabet = recognition.answerAlphabet
        assertIs<AnswerAlphabet.PatternChoice>(alphabet)
        assertEquals(listOf("1", "2"), alphabet.labels)
        assertEquals("1", recognition.correctLabel)
    }

    @Test
    fun `the correct label finds the answer wherever it sits among the choices`() {
        val recognition = item(RhythmMode.RECOGNITION, pattern = syncopated, choices = listOf(plain, syncopated))
        assertEquals("2", recognition.correctLabel)
    }

    @Test
    fun `a production item labels itself by its onsets`() {
        // There is no label to choose, so the pattern's own onsets stand in - the thing scoring compares
        // tap times against, and enough to reconstruct what was asked without the seed.
        assertEquals("0,12,24,36", item(RhythmMode.PRODUCTION).correctLabel)
    }

    @Test
    fun `an item whose mode and choices disagree is refused`() {
        assertFailsWith<IllegalArgumentException>("a recognition item needs choices") {
            item(RhythmMode.RECOGNITION)
        }
        assertFailsWith<IllegalArgumentException>("one choice is not a choice") {
            item(RhythmMode.RECOGNITION, choices = listOf(plain))
        }
        assertFailsWith<IllegalArgumentException>("the answer must be among the choices") {
            item(RhythmMode.RECOGNITION, pattern = plain, choices = listOf(syncopated, syncopated))
        }
        assertFailsWith<IllegalArgumentException>("a production item is tapped, not chosen from") {
            item(RhythmMode.PRODUCTION, choices = listOf(plain, syncopated))
        }
    }

    @Test
    fun `onset times follow the tempo, not the tick grid`() {
        val atHundred = item(RhythmMode.PRODUCTION)
        assertEquals(listOf(0.0, 600.0, 1200.0, 1800.0), atHundred.onsetTimesMs)

        // Twice the tempo, half the elapsed time. The ticks did not move; only the rendering of them.
        val atTwoHundred = item(RhythmMode.PRODUCTION, tempoBpm = 200)
        assertEquals(listOf(0.0, 300.0, 600.0, 900.0), atTwoHundred.onsetTimesMs)
    }

    @Test
    fun `a choice count below two is refused by the alphabet itself`() {
        assertFailsWith<IllegalArgumentException> { AnswerAlphabet.PatternChoice(1) }
    }
}
