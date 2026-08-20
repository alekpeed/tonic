package com.tonic.feature.practice.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The wiring between "which explanation does this node need" and "which explanation is on screen."
 *
 * This existed as two correct halves and one wrong line. [PracticeViewModel.introKindFor] resolved the
 * kind properly and had tests; [M10IntroContent] and [M9IntroContent] were written, previewed and
 * tested as composables. `PracticeScreen` then called `M2IntroContent` unconditionally, so a learner
 * reaching minor was shown the major explanation — the one screen docs/08-UI-SPEC.md §3a exists to
 * prevent them from being denied. Nothing in the suite could see it, because nothing tested the join.
 *
 * Each case below fails against that version.
 */
@RunWith(AndroidJUnit4::class)
class IntroDispatchTest {
    @get:Rule
    val compose = createComposeRule()

    // assertExists rather than assertIsDisplayed: the question here is which screen got composed, and
    // these screens scroll - the M2 one's start button sits below the fold on the default test device,
    // which is a fact about its length and not about the dispatch.
    private fun render(kind: IntroKind) {
        compose.setContent {
            TonicTheme {
                IntroForKind(
                    kind = kind,
                    answerLabel = "3",
                    answerRevealed = false,
                    onPlayExample = {},
                    onRevealAnswer = {},
                    onStart = {},
                )
            }
        }
    }

    @Test
    fun `the minor node gets the minor explanation`() {
        render(IntroKind.M10)
        compose.onNodeWithTag("m10_intro_start").assertExists()
        compose.onNodeWithTag("m2_intro_start").assertDoesNotExist()
    }

    @Test
    fun `the chromatic node gets the chromatic explanation`() {
        render(IntroKind.M11)
        compose.onNodeWithTag("m11_intro_start").assertExists()
        compose.onNodeWithTag("m2_intro_start").assertDoesNotExist()
    }

    @Test
    fun `the major node gets the major explanation`() {
        render(IntroKind.M2)
        compose.onNodeWithTag("m2_intro_start").assertExists()
    }

    @Test
    fun `recall on a node with no explanation of its own falls back to the major one`() {
        // docs/11-ONBOARDING-CLARITY.md §5's help affordance is always reachable, including from a node
        // whose own first-run screen has been seen. Showing nothing would be a dead button.
        render(IntroKind.NONE)
        compose.onNodeWithTag("m2_intro_start").assertExists()
    }
}
