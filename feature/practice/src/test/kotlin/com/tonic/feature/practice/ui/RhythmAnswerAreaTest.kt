package com.tonic.feature.practice.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.rhythm.Meter
import com.tonic.core.model.rhythm.MetronomeFadeLevel
import com.tonic.core.model.rhythm.MetronomePlanner
import com.tonic.core.model.rhythm.RhythmPattern
import com.tonic.core.model.rhythm.RhythmQuestion
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.ui.rhythm.CHOICE_TAG_PREFIX
import com.tonic.feature.practice.ui.rhythm.TAP_SURFACE_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `M3` answer controls — docs/40-PHASE-4-SPEC.md §7.2 and §3.3.
 *
 * The case worth testing rather than reading is the dispatch itself. Before this, `AnswerArea` ended
 * in `else ->` and a rhythm item fell into the recognition arm, which drew an empty degree ladder and
 * offered nothing to answer with — the same failure its own KDoc describes `M9` having had, in the
 * file that claims it cannot recur. So each question shape is rendered through the real `AnswerArea`
 * rather than through its own composable directly.
 */
@RunWith(AndroidJUnit4::class)
class RhythmAnswerAreaTest {
    @get:Rule
    val compose = createComposeRule()

    private fun item(question: RhythmQuestion) =
        Item.RhythmItem(
            skill = SkillIds.M3_BEAT_FIND,
            meter = Meter.FOUR_FOUR,
            tempoBpm = 100,
            pattern =
                RhythmPattern(
                    Meter.FOUR_FOUR,
                    bars = 1,
                    onsetTicks = (0 until 4).map { it * Meter.TICKS_PER_BEAT },
                ),
            metronomePlan = MetronomePlanner.plan(MetronomeFadeLevel.L0, Meter.FOUR_FOUR, bars = 1),
            question = question,
            timbre = TimbreId.PURE,
            seed = 1L,
        )

    private fun render(
        question: RhythmQuestion,
        enabled: Boolean = true,
        selectedLabel: String? = null,
        correctLabel: String? = null,
        onLabelSelected: (String) -> Unit = {},
        onTap: (Long) -> Unit = {},
    ) {
        compose.setContent {
            TonicTheme {
                AnswerArea(
                    uiState =
                        PracticeUiState(
                            item = item(question),
                            inputEnabled = enabled,
                            selectedAnswerLabel = selectedLabel,
                            correctAnswerLabel = correctLabel,
                        ),
                    onDegreeSelected = {},
                    onLabelSelected = onLabelSelected,
                    onTap = onTap,
                )
            }
        }
    }

    @Test
    fun `a production item puts the tap surface on screen`() {
        render(RhythmQuestion.TapItBack)
        compose.onNodeWithTag(TAP_SURFACE_TAG).assertIsDisplayed()
    }

    @Test
    fun `every tap is reported with its own timestamp`() {
        val taps = mutableListOf<Long>()
        render(RhythmQuestion.TapItBack, onTap = { taps += it })

        repeat(3) { compose.onNodeWithTag(TAP_SURFACE_TAG).performTouchInput { click() } }

        assertEquals(3, taps.size, "each touch is an onset and must be reported")
        // §4.1: the timestamps have to be usable as a timeline, not merely present. Non-decreasing is
        // the weakest true statement about them - a click's simulated down and up can share a
        // millisecond - and it fails immediately if the surface ever reports a constant.
        assertTrue(taps.zipWithNext().all { (a, b) -> b >= a }, "timestamps must advance: $taps")
    }

    @Test
    fun `a disabled tap surface records nothing`() {
        // While the pattern is still playing, a tap is not an answer - docs/08-UI-SPEC.md §3's
        // disabled state. Taps arriving early would be scored against a pattern the learner has not
        // finished hearing.
        val taps = mutableListOf<Long>()
        render(RhythmQuestion.TapItBack, enabled = false, onTap = { taps += it })

        compose.onNodeWithTag(TAP_SURFACE_TAG).performTouchInput { click() }

        assertTrue(taps.isEmpty(), "a disabled surface must not record taps, got $taps")
    }

    @Test
    fun `a which-pattern item offers one button per choice and answers with the figure`() {
        val choices =
            listOf(
                RhythmPattern(Meter.FOUR_FOUR, 1, (0 until 4).map { it * Meter.TICKS_PER_BEAT }),
                RhythmPattern(
                    Meter.FOUR_FOUR,
                    1,
                    // The same four beats with the second one split, so the two choices diverge at
                    // exactly one beat - which is what makes the item test one figure.
                    listOf(
                        0,
                        Meter.TICKS_PER_BEAT,
                        Meter.TICKS_PER_BEAT + Meter.TICKS_PER_BEAT / 2,
                        Meter.TICKS_PER_BEAT * 2,
                        Meter.TICKS_PER_BEAT * 3,
                    ),
                ),
            )
        val question = RhythmQuestion.WhichPattern(choices = choices, answerIndex = 1)
        var answered: String? = null
        render(question, onLabelSelected = { answered = it })

        compose.onNodeWithTag("${CHOICE_TAG_PREFIX}2").performClick()

        // The button says "2" and the log stores a figure. §8's confusion matrix is over figures, and
        // a position means a different rhythm in every item.
        assertEquals(question.figureSignatures[1], answered)
    }

    @Test
    fun `a downbeat item offers one button per beat heard and answers with the position`() {
        var answered: String? = null
        render(
            RhythmQuestion.WhichBeatIsOne(beatsHeard = 4, downbeatPosition = 3),
            onLabelSelected = { answered = it },
        )

        for (beat in 1..4) compose.onNodeWithTag("$CHOICE_TAG_PREFIX$beat").assertIsDisplayed()
        compose.onNodeWithTag("${CHOICE_TAG_PREFIX}3").performClick()

        // Here the position *is* the answer - §3.4's skill is beat induction, and "the third beat you
        // heard" means the same thing from one item to the next.
        assertEquals("3", answered)
    }
}
