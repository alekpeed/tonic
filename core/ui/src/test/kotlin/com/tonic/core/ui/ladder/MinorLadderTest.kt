package com.tonic.core.ui.ladder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The ladder in minor — docs/20-PHASE-2-SPEC.md §3's `M10`.
 *
 * Minor's characteristic degrees are altered values: `♭3` is `ScaleDegree(3, -1)`, deliberately not
 * equal to `ScaleDegree(3)` (§2.1). The ladder used to decide what to draw with `scaleDegree in
 * activeDegrees`, so every one of those rendered as an inactive gap and a learner practicing minor had
 * no button for the note they were being asked about. It matches by scale *position* now.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class MinorLadderTest {
    @get:Rule
    val compose = createComposeRule()

    private val minorSet4 =
        listOf(
            ScaleDegree(1),
            ScaleDegree(2),
            ScaleDegree(3, -1),
            ScaleDegree(4),
            ScaleDegree(5),
            ScaleDegree(6, -1),
        )

    @Test
    fun `every minor degree gets a real button, not a gap`() {
        setLadder(minorSet4)
        for (degree in minorSet4) {
            // Tagged by canonicalLabel, not by slot number. Changed in Stage 2.4: harmonic minor puts
            // ♭7 and ♮7 in the same slot, so a slot-numbered tag would address two different buttons -
            // and did, silently. Identical tags for every unaltered degree, since canonicalLabel is the
            // bare numeral there.
            compose.onNodeWithTag("degree_button_${degree.canonicalLabel}").assertIsDisplayed()
        }
    }

    @Test
    fun `an altered degree is labeled with its accidental, so it cannot be mistaken for the natural`() {
        setLadder(minorSet4)
        compose.onNodeWithText("♭3").assertIsDisplayed()
        compose.onNodeWithText("♭6").assertIsDisplayed()
        // The unaltered ones keep their plain numerals.
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun `solfege names minor's degrees with their own syllables, never the natural ones`() {
        setLadder(minorSet4, style = LabelStyle.SOLFEGE)
        compose.onNodeWithText("Me").assertIsDisplayed()
        compose.onNodeWithText("Le").assertIsDisplayed()
        compose.onNodeWithText("Do").assertIsDisplayed()
    }

    private fun setLadder(
        active: List<ScaleDegree>,
        style: LabelStyle = LabelStyle.NUMBERS,
    ) {
        compose.setContent {
            com.tonic.core.ui.theme.TonicTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    DegreeLadder(
                        activeDegrees = active,
                        mode = Mode.MINOR,
                        labelStyle = style,
                        enabled = true,
                        selectedDegree = null,
                        correctDegree = null,
                        onDegreeSelected = {},
                        modifier = Modifier.height(1_000.dp),
                    )
                }
            }
        }
    }
}
