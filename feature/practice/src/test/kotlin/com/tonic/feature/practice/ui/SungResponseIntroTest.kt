package com.tonic.feature.practice.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * docs/30-PHASE-3-SPEC.md Stage 3.2, second acceptance criterion: the explanation is reachable.
 *
 * Rendered rather than read. Three of Phase 2's four wiring bugs were a correct screen and a correct
 * resolver joined by one wrong line in the composition layer, in a place nothing tested
 * (docs/21-HANDOFF.md §6, docs/20-PHASE-2-SPEC.md §8.4–8.5) — so the join is what these check.
 */
@RunWith(AndroidJUnit4::class)
class SungResponseIntroTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the sung kind dispatches to the sung explanation, not the major one`() {
        compose.setContent {
            TonicTheme {
                IntroForKind(
                    kind = IntroKind.SUNG,
                    answerLabel = "3",
                    answerRevealed = false,
                    onPlayExample = {},
                    onRevealAnswer = {},
                    onStart = {},
                )
            }
        }
        compose.onNodeWithTag("sung_intro_start").assertExists()
        compose.onNodeWithTag("m2_intro_start").assertDoesNotExist()
    }

    /**
     * §3 mitigation 5 and §6.2 point 3. The whole screen exists to defuse one belief — that the learner's
     * voice is what is being marked — so the three statements carrying that are asserted individually
     * rather than trusting that the screen "has copy on it".
     */
    @Test
    fun `it states plainly that the voice is not scored, any octave counts, and tapping remains`() {
        compose.setContent { TonicTheme { SungResponseIntroContent {} } }

        compose
            .onNodeWithText("How well you sing is not part of this. Nothing here scores your voice.")
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "Sing it high or low, whichever is comfortable for your voice. It counts either way.",
            ).assertIsDisplayed()
        compose
            .onNodeWithText(
                "The buttons are always there too. You can tap any question instead, at any time.",
            ).assertIsDisplayed()
    }

    /** §5.2's rule, stated to the learner rather than only implemented: unclear is not wrong. */
    @Test
    fun `it tells the learner that an unreadable answer is not counted wrong`() {
        compose.setContent { TonicTheme { SungResponseIntroContent {} } }
        compose
            .onNodeWithText(
                "If the app cannot make out what you sang, it simply asks again. " +
                    "That is never counted as a wrong answer.",
            ).assertIsDisplayed()
    }

    /**
     * §1's "not a tutorial mode the user must complete correctly": start is live immediately, with no
     * example to play and nothing to acknowledge first.
     *
     * The scroll is required, not incidental. This screen is longer than the default test viewport, so
     * the button sits below the fold - and a `performClick` on a node outside the viewport does not
     * throw, it simply never reaches the button, which would leave this test asserting on a click that
     * never happened. IntroDispatchTest records the same hazard for the major screen.
     */
    @Test
    fun `start is available without any interaction first`() {
        var started = false
        compose.setContent { TonicTheme { SungResponseIntroContent { started = true } } }
        compose.onNodeWithTag("sung_intro_start").performScrollTo().performClick()
        assertTrue(started, "Start must work on first press, with nothing required beforehand")
    }
}
