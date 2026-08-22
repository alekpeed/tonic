package com.tonic.feature.practice.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/30-PHASE-3-SPEC.md §6.3, which is one of Stage 3.3's three acceptance criteria: "Every sung
 * item must offer a one-tap fallback to answer by tapping instead, always visible, no penalty, no
 * different scoring. Someone who can hear it but can't produce it must never be stuck."
 *
 * "Always visible" is the part worth testing rather than reading. It is easy to write a screen where
 * the ladder is present in the state you happened to look at and absent in one you did not — while
 * listening, say, or after an unclear answer, both of which are exactly when a learner who cannot sing
 * the note most needs the buttons. So these walk every sung state and assert the ladder survives all
 * of them.
 */
@RunWith(AndroidJUnit4::class)
class SungAnswerControlTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(
        sungAvailable: Boolean,
        capture: SungCaptureState,
        inputEnabled: Boolean = true,
        onDegreeSelected: (ScaleDegree) -> Unit = {},
        onSing: () -> Unit = {},
    ) {
        compose.setContent {
            TonicTheme {
                AnswerArea(
                    uiState =
                        PreviewStates.awaitingAnswer
                            .copy(
                                inputEnabled = inputEnabled,
                                sungResponseAvailable = sungAvailable,
                                sungCapture = capture,
                            ),
                    onDegreeSelected = onDegreeSelected,
                    onLabelSelected = {},
                    onSing = onSing,
                )
            }
        }
    }

    /**
     * The criterion itself, across every state singing can be in — driven through one composition,
     * since a Compose rule accepts `setContent` only once.
     *
     * Asserted on a real answer button rather than a ladder container: the claim §6.3 makes is that
     * the learner can *answer* by tapping, and a present-but-empty ladder would satisfy a container
     * check while stranding exactly the person the rule exists for.
     */
    @Test
    fun `the degree ladder is present in every sung state`() {
        var capture by mutableStateOf(SungCaptureState.IDLE)
        compose.setContent {
            TonicTheme {
                AnswerArea(
                    uiState =
                        PreviewStates.awaitingAnswer
                            .copy(sungResponseAvailable = true, sungCapture = capture),
                    onDegreeSelected = {},
                    onLabelSelected = {},
                    onSing = {},
                )
            }
        }

        for (state in SungCaptureState.entries) {
            compose.runOnUiThread { capture = state }
            compose.waitForIdle()
            compose
                .onNodeWithTag(LADDER_BUTTON)
                .assertExists("the tap fallback vanished while sung capture was $state")
        }
    }

    /** And it is not merely present but usable — a visible-but-dead ladder would strand the same learner. */
    @Test
    fun `the ladder still answers while singing is offered`() {
        var tapped: ScaleDegree? = null
        render(sungAvailable = true, capture = SungCaptureState.IDLE, onDegreeSelected = { tapped = it })

        compose.onNodeWithTag(LADDER_BUTTON).performScrollTo().performClick()

        assertEquals(1, tapped?.degree, "§6.3: the fallback is one tap, with no different scoring")
    }

    /**
     * The tap-only path, unchanged — Stage 3.3's first acceptance criterion. A learner who never
     * enables singing must see exactly the screen they saw before Phase 3 existed, with nothing added
     * and nothing to dismiss.
     */
    @Test
    fun `nothing about singing appears when it is unavailable`() {
        render(sungAvailable = false, capture = SungCaptureState.IDLE)

        compose.onNodeWithTag("sing_answer").assertDoesNotExist()
        compose.onNodeWithTag("sung_unclear_notice").assertDoesNotExist()
        compose.onNodeWithTag(LADDER_BUTTON).assertExists()
    }

    /**
     * §5.2's "unclear is not wrong", as the learner experiences it: the notice appears and the sing
     * button is immediately live again. A retry that had to wait, or a button that stayed disabled
     * after a mumble, would make "not wrong" untrue in the only place it is felt.
     */
    @Test
    fun `an unclear answer says so and leaves both ways of answering open`() {
        var sang = false
        render(sungAvailable = true, capture = SungCaptureState.UNCLEAR, onSing = { sang = true })

        compose.onNodeWithTag("sung_unclear_notice").assertIsDisplayed()
        compose.onNodeWithTag("sing_answer").assertIsEnabled().performClick()
        assertTrue(sang, "a learner who was not understood must be able to try again at once")
        compose.onNodeWithTag(LADDER_BUTTON).assertExists()
    }

    /** While actually recording, the sing button is the one thing that stops — pressing it again would restart capture. */
    @Test
    fun `the sing button is inert while listening, and the ladder is not`() {
        var tapped: ScaleDegree? = null
        render(
            sungAvailable = true,
            capture = SungCaptureState.LISTENING,
            onDegreeSelected = { tapped = it },
        )

        // Asserted as not-enabled rather than clicked-and-ignored: Compose strips the click action
        // from a disabled node, so performClick on one throws "missing OnClick" instead of quietly
        // doing nothing - which would fail this test for a reason unrelated to what it checks.
        compose.onNodeWithTag("sing_answer").assertIsNotEnabled()

        compose.onNodeWithTag(LADDER_BUTTON).performScrollTo().performClick()
        assertEquals(1, tapped?.degree, "§6.3: the buttons are live throughout, including mid-capture")
    }

    private companion object {
        /**
         * Degree 1's button. `DegreeLadder` tags each button by canonical label; there is no container
         * tag, and asserting on a real button is the stronger claim anyway - §6.3's promise is that the
         * learner can *answer* by tapping, and a present-but-empty ladder would satisfy a container
         * check while stranding exactly the person the rule exists for.
         *
         * Every click on it scrolls first. The ladder puts degree 1 at the *bottom* of seven slots
         * inside a `verticalScroll`, so on the default test viewport it sits below the fold - and
         * `performClick` on a node outside the viewport does not throw, it simply never reaches the
         * button, leaving the test asserting on a click that never happened. Runs #13 and #14 were
         * spent on exactly that, and `IntroDispatchTest` records the same hazard.
         */
        const val LADDER_BUTTON = "degree_button_1"
    }
}
