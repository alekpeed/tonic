package com.tonic.feature.practice.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `M12` explanation — docs/20-PHASE-2-SPEC.md §5.1, which singles this screen out: "a worked
 * example is not optional here; it is the only way this task is comprehensible."
 *
 * So the tests are about comprehensibility rather than layout: that the example is playable, that the
 * reveal is user-driven and names the direction, and that the three things a first-time reader has to
 * be told — the task runs backwards, the silence is the exercise, and what "hear it in your head"
 * means — are all actually on the screen.
 */
@RunWith(AndroidJUnit4::class)
class M12IntroTest {
    @get:Rule
    val compose = createComposeRule()

    private var played = 0
    private var revealed = 0
    private var started = 0

    private fun render(
        matched: Boolean = false,
        soundedWasLower: Boolean = true,
        answerRevealed: Boolean = false,
    ) {
        compose.setContent {
            TonicTheme {
                M12IntroContent(
                    statedLabel = "5",
                    matched = matched,
                    soundedWasLower = soundedWasLower,
                    answerRevealed = answerRevealed,
                    onPlayExample = { played++ },
                    onRevealAnswer = { revealed++ },
                    onStart = { started++ },
                )
            }
        }
    }

    @Test
    fun `the example is playable and repeatable`() {
        // docs/11-ONBOARDING-CLARITY.md §3: in real audio, and hearing it more than once is the point.
        render()
        compose.onNodeWithTag("m12_intro_play_example").performScrollTo().performClick()
        compose.onNodeWithTag("m12_intro_play_example").performClick()
        assertEquals(2, played)
    }

    @Test
    fun `the answer is never shown before the learner asks for it`() {
        render()
        compose.onNodeWithTag("m12_intro_reveal").assertExists()
        assertTrue(
            compose.onAllNodesWithText("That was", substring = true).fetchSemanticsNodes().isEmpty(),
            "the outcome was on screen before the learner had a chance to try holding the note",
        )

        compose.onNodeWithTag("m12_intro_reveal").performScrollTo().performClick()
        assertEquals(1, revealed)
    }

    @Test
    fun `the reveal names the direction, because the third button asks for it`() {
        // §8.1 decision 3 added direction to make the confusion data diagnostic. An example that only
        // said "not a match" would leave two of the three buttons unexplained.
        render(matched = false, soundedWasLower = true, answerRevealed = true)
        compose.onNodeWithText("lower than", substring = true).assertExists()
    }

    @Test
    fun `a matching example says so plainly`() {
        render(matched = true, answerRevealed = true)
        compose.onNodeWithText("was a match", substring = true).assertExists()
    }

    @Test
    fun `the three things a first-time reader has to be told are all present`() {
        render()
        // The task runs backwards.
        compose.onNodeWithText("runs backwards", substring = true).assertExists()
        // The silence is the exercise, not a stall.
        compose.onNodeWithText("The silence is not the app loading", substring = true).assertExists()
        // What "hear it in your head" means, for someone who thinks they cannot.
        compose.onNodeWithText("song you know", substring = true).assertExists()
    }

    @Test
    fun `nothing on the screen implies a time limit`() {
        // docs/02-PEDAGOGY.md §6 and §5.3. The screen explains a several-second silence, which is
        // exactly where a reassurance about speed would be tempting and a mention of timing fatal.
        render()
        for (phrase in listOf("quickly", "as fast", "time limit", "seconds to", "hurry")) {
            assertTrue(
                compose.onAllNodesWithText(phrase, substring = true).fetchSemanticsNodes().isEmpty(),
                "\"$phrase\" appears on the audiation explanation and creates time pressure",
            )
        }
        compose.onNodeWithText("Nothing is timing you", substring = true).assertExists()
    }

    @Test
    fun `starting dismisses it`() {
        render()
        compose.onNodeWithTag("m12_intro_start").performScrollTo().performClick()
        assertEquals(1, started)
    }

    @Test
    fun `the three answers it describes are the three the screen offers`() {
        // Keeps the explanation and the control from drifting apart.
        assertEquals(3, AnswerAlphabet.MatchDirection.labels.size)
        render()
        compose.onNodeWithText("it matched, it was lower, or it was higher", substring = true).assertExists()
    }
}
