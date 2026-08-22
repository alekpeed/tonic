package com.tonic.feature.practice.ui

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.music.Mode
import com.tonic.core.ui.components.PlaybackPhase
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * `M10.MIXED_MODE` on the real practice screen — docs/20-PHASE-2-SPEC.md §5.4.
 *
 * The node's whole premise is that the screen gives nothing away before the answer and states the mode
 * after it. Both halves are properties of this composable, and neither is visible from the generator
 * or the loop.
 */
@RunWith(AndroidJUnit4::class)
class MixedModeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(uiState: PracticeUiState) {
        compose.setContent {
            TonicTheme {
                PracticeContent(uiState = uiState, onDegreeSelected = {}, onReplay = {}, onSkip = {})
            }
        }
    }

    @Test
    fun `nothing states the mode before the answer`() {
        // Every phase before AWAITING_ANSWER included: the caption, the phase indicator and the ladder
        // are all on screen while the cadence plays, and any of them naming the mode would hand over
        // the answer to the question this node exists to ask.
        render(PreviewStates.mixedMode.copy(phase = PlaybackPhase.REFERENCE, inputEnabled = false))

        compose.onNodeWithTag("revealed_mode").assertDoesNotExist()
        for (phrase in listOf("major", "Major", "minor", "Minor")) {
            assertEquals(
                0,
                compose.onAllNodesWithTextSubstring(phrase).fetchSemanticsNodes().size,
                "\"$phrase\" is on screen before the learner has answered",
            )
        }
    }

    @Test
    fun `the mode is stated after the answer`() {
        // §5.4, verbatim: "after the answer, the feedback must state which mode it was, or the learner
        // cannot learn from a mistake."
        val state = PreviewStates.mixedModeAnswered
        render(state)
        compose.onNodeWithTag("revealed_mode").assertExists()
        // Read off the state rather than hardcoded, so the assertion stays true of whichever mode the
        // example item happens to be in.
        val expected = if (state.revealedMode == Mode.MINOR) "That one was minor." else "That one was major."
        compose.onNodeWithText(expected).assertExists()
    }

    @Test
    fun `the ladder offers all ten degrees, including both thirds`() {
        render(PreviewStates.mixedMode)
        for (label in listOf("1", "2", "b3", "3", "4", "5", "b6", "6", "b7", "7")) {
            compose.onNodeWithTag("degree_button_$label").assertExists()
        }
    }

    @Test
    fun `an ordinary node still says nothing about mode after an answer`() {
        // The statement is mandatory at MIXED_MODE and noise everywhere else - a learner who was told
        // at the start of the session which mode they are in does not need it repeated per item.
        render(PreviewStates.correctFeedback)
        compose.onNodeWithTag("revealed_mode").assertDoesNotExist()
    }
}

private fun ComposeContentTestRule.onAllNodesWithTextSubstring(text: String) =
    onAllNodesWithText(text, substring = true)
