package com.tonic.core.ui.ladder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Harmonic minor puts ♭7 and ♮7 in the same scale position. Both are answers. Both need a button. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class TwoDegreesOneSlotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `harmonic minor shows both the flat seventh and the natural seventh`() {
        val harmonicMinor =
            listOf(
                ScaleDegree(1),
                ScaleDegree(2),
                ScaleDegree(3, -1),
                ScaleDegree(4),
                ScaleDegree(5),
                ScaleDegree(6, -1),
                ScaleDegree(7, -1),
                ScaleDegree(7),
            )
        compose.setContent {
            TonicTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    DegreeLadder(
                        activeDegrees = harmonicMinor,
                        mode = Mode.MINOR,
                        labelStyle = LabelStyle.NUMBERS,
                        enabled = true,
                        selectedDegree = null,
                        correctDegree = null,
                        onDegreeSelected = {},
                        modifier = Modifier.height(1_400.dp),
                    )
                }
            }
        }
        compose.onNodeWithText("♭7").assertIsDisplayed()
        compose.onNodeWithText("7").assertIsDisplayed()
    }
}
