package com.tonic.feature.practice.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.ui.components.PlaybackPhase
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * The answer control matches the item type — docs/20-PHASE-2-SPEC.md §5.3, and the `M9` hole that
 * Stage 2.6 found while building it.
 *
 * `PracticeContent` rendered the degree ladder unconditionally, so an `M9` item put an empty ladder on
 * screen with **no major/minor buttons at all**. Every piece around it was correct and tested; nothing
 * tested which control was on screen for which item, so nothing could see it. These cases fail against
 * that version.
 */
@RunWith(AndroidJUnit4::class)
class AnswerAreaTest {
    @get:Rule
    val compose = createComposeRule()

    private var chosen: String? = null

    private fun render(uiState: PracticeUiState) {
        compose.setContent {
            TonicTheme {
                AnswerArea(
                    uiState = uiState,
                    onDegreeSelected = {},
                    onLabelSelected = { chosen = it },
                )
            }
        }
    }

    @Test
    fun `a mode item gets two buttons, not an empty ladder`() {
        render(PreviewStates.modeIdentification)

        compose.onNodeWithTag("mode_button_${AnswerAlphabet.MajorMinor.MAJOR}").assertExists()
        compose.onNodeWithTag("mode_button_${AnswerAlphabet.MajorMinor.MINOR}").assertExists()
        compose.onNodeWithTag("degree_button_1").assertDoesNotExist()
    }

    @Test
    fun `a mode answer reaches the handler`() {
        render(PreviewStates.modeIdentification)
        compose.onNodeWithTag("mode_button_${AnswerAlphabet.MajorMinor.MINOR}").performClick()
        assertEquals(AnswerAlphabet.MajorMinor.MINOR, chosen)
    }

    @Test
    fun `a prediction item gets three buttons and the degree it named`() {
        // docs/20-PHASE-2-SPEC.md §5.3: "answer controls are two buttons... not the ladder" - three
        // under §8.1 decision 3 - and the named degree displayed prominently.
        render(PreviewStates.prediction)

        compose.onNodeWithTag("prediction_stated_degree").assertExists()
        for (label in AnswerAlphabet.MatchDirection.labels) {
            compose.onNodeWithTag("match_button_$label").assertExists()
        }
        compose.onNodeWithTag("degree_button_1").assertDoesNotExist()
    }

    @Test
    fun `the named degree is on screen during the silent gap, before anything has sounded`() {
        // §5.3: the stated degree "is the *instruction*, not the answer". What keeps it from reading as
        // a revealed answer is that it is already there while the audio is still silent - a revealed
        // answer never is.
        render(PreviewStates.prediction.copy(phase = PlaybackPhase.AUDIATION_GAP, inputEnabled = false))
        compose.onNodeWithTag("prediction_stated_degree").assertExists()
    }

    @Test
    fun `a recognition item still gets the ladder`() {
        render(PreviewStates.awaitingAnswer)
        compose.onNodeWithTag("degree_button_1").assertExists()
        compose.onNodeWithTag("match_button_${AnswerAlphabet.MatchDirection.MATCHED}").assertDoesNotExist()
    }

    @Test
    fun `nothing on the prediction screen counts down`() {
        // docs/20-PHASE-2-SPEC.md §5.3 rules out a countdown explicitly, and docs/02-PEDAGOGY.md §6
        // forbids time pressure generally. A digit on screen during the gap would be read as one
        // whatever it was labeled, so this asserts the absence of any bare number other than the degree
        // being held.
        render(PreviewStates.prediction.copy(phase = PlaybackPhase.AUDIATION_GAP, inputEnabled = false))

        for (candidate in listOf("1", "2", "3", "4", "5", "0:01", "0:05", "5s", "5 seconds")) {
            val matches = compose.onAllNodesWithText(candidate, substring = false).fetchSemanticsNodes()
            val allowed = candidate == PreviewStates.predictionStatedLabel
            if (!allowed) {
                assertEquals(
                    0,
                    matches.size,
                    "\"$candidate\" is on screen during the audiation gap and will read as a countdown",
                )
            }
        }
        // And the instruction that replaces it is present.
        compose.onNodeWithText("Hear this one in your head").assertExists()
    }
}
