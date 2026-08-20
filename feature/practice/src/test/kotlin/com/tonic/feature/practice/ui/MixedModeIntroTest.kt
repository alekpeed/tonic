package com.tonic.feature.practice.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.music.Mode
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mixed-mode explanation — docs/08-UI-SPEC.md §3a.
 *
 * Not one of the four screens docs/20-PHASE-2-SPEC.md §5.1 names, and required anyway: §3a's test for
 * a new task shape is "a different question, a different answer control, **or a different thing to
 * listen for**," and this is the third. The learner has to hear which key they are in before the
 * degree means anything, and the ladder has silently grown from seven buttons to ten because of it.
 */
@RunWith(AndroidJUnit4::class)
class MixedModeIntroTest {
    @get:Rule
    val compose = createComposeRule()

    private var played = 0
    private var revealed = 0
    private var started = 0

    /** Present somewhere on the screen. Not [onNodeWithText], which fails when a phrase appears twice. */
    private fun assertPresent(text: String) {
        assertTrue(
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty(),
            "\"$text\" is not on the mixed-mode explanation",
        )
    }

    private fun render(answerRevealed: Boolean = false) {
        compose.setContent {
            TonicTheme {
                MixedModeIntroContent(
                    answerLabel = "♭3",
                    answerRevealed = answerRevealed,
                    onPlayExample = { played++ },
                    onRevealAnswer = { revealed++ },
                    onStart = { started++ },
                )
            }
        }
    }

    @Test
    fun `it explains the two things that actually changed`() {
        render()
        // The mode stops being announced...
        assertPresent("you won't be told")
        // ...and that is why the ladder grew.
        assertPresent("more buttons now")
    }

    @Test
    fun `it says why the buttons do not change between rounds`() {
        // The one thing a learner might otherwise read as a bug: ten buttons when only seven can be
        // right. Left unexplained it looks like the app forgot which mode it was in.
        render()
        assertPresent("giving the answer away")
    }

    @Test
    fun `it tells the learner the mode will be stated after each answer`() {
        // docs/20-PHASE-2-SPEC.md §5.4 promises this on the practice screen; saying so up front is what
        // makes a miss feel diagnosable rather than arbitrary.
        render()
        assertPresent("you'll be told which flavor it was")
    }

    @Test
    fun `the worked example is playable and its answer is user-driven`() {
        render()
        compose.onNodeWithTag("mixed_intro_play_example").performScrollTo().performClick()
        assertEquals(1, played)

        assertTrue(
            compose.onAllNodesWithText("That one was", substring = true).fetchSemanticsNodes().isEmpty(),
            "the outcome was on screen before the learner had a chance to listen",
        )
        compose.onNodeWithTag("mixed_intro_reveal").performScrollTo().performClick()
        assertEquals(1, revealed)
    }

    @Test
    fun `the reveal names both the mode and the degree`() {
        // Both, because both are new information here: on every earlier node the mode was a given and
        // only the degree was in question.
        render(answerRevealed = true)
        assertPresent("That one was minor, and the note was ♭3")
    }

    @Test
    fun `the worked example really is a minor item`() {
        // The screen's claim has to be true of the audio it plays. A major example would demonstrate a
        // flow indistinguishable from ordinary practice and teach nothing about why the ladder changed.
        assertEquals(Mode.MINOR, MixedModeWorkedExample.generate().mode)
    }

    @Test
    fun `starting dismisses it`() {
        render()
        compose.onNodeWithTag("mixed_intro_start").performScrollTo().performClick()
        assertEquals(1, started)
    }
}
