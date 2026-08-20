package com.tonic.feature.practice.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 7's ladder-sizing criterion, measured against the **real** practice
 * screen rather than an estimate of it.
 *
 * `DegreeLadderLayoutTest` in `:core:ui` pins the ladder's own intrinsic geometry, but the Stage 7
 * claim — "ladder fits seven degrees plus gaps on a 5-inch screen, no scroll, at 200% font scale" — is
 * a claim about this screen, where the ladder competes with the header, phase indicator, captions,
 * replay button and help/skip row for vertical space. That claim was reported met in Phase 1 and never
 * executed once; this renders the actual composable at the 5-inch reference device and measures what
 * happens.
 *
 * The ladder sits in a `weight(1f)` `Box` wrapping a **non-scrolling** `Column` of fixed-height slots,
 * so when the content exceeds the space there is no scrollbar and no error — the bottom of the ladder
 * is simply not on screen. That silent-clipping mode is why this has to be measured rather than
 * eyeballed on whatever handset happens to be nearby.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class PracticeScreenLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var screenHeight: Dp = 0.dp

    @Test
    fun `CHARACTERIZATION - on a 5-inch screen the lowest degree buttons collapse to zero height`() {
        // This test documents a DEFECT, not a requirement. It asserts what the shipped layout actually
        // does so the bug cannot drift unnoticed, and it must be INVERTED - to "every active degree
        // button is at least minTouchTarget tall" - the moment the layout is fixed.
        //
        // The ladder is a non-scrolling Column of fixed-height slots inside a weight(1f) Box. When its
        // 440dp of slots meet the ~248dp the practice chrome leaves on a 640dp-tall screen, Compose does
        // not scroll and does not error: it squeezes the last children to nothing. Degrees are ordered 7
        // at the top down to 1 at the bottom, so the buttons that vanish are the low ones - including
        // degree 1, the tonic, which is "home" and the most consequential button on the screen.
        renderPractice(PreviewStates.fullDiatonicSet)

        assertEquals(56.dp, slotHeight(7), "the top slots are laid out at full size")
        assertEquals(56.dp, slotHeight(6))
        assertEquals(56.dp, slotHeight(5))
        assertEquals(37.dp, slotHeight(4), "degree 4 is partially squeezed")
        for (degree in listOf(3, 2, 1)) {
            assertEquals(
                0.dp,
                slotHeight(degree),
                "degree $degree currently renders at zero height and cannot be tapped",
            )
        }
    }

    @Test
    fun `CHARACTERIZATION - even the three-degree starting node loses two of its three buttons`() {
        // The most serious form of the defect, because M2.DEG_SET_1 is where every user starts. An
        // inactive gap occupies a full 56dp slot exactly like a button, so the ladder's height does not
        // depend on how many degrees are active - and the squeeze lands on real buttons rather than on
        // the gaps. A user on a 5-inch device can answer 5, and cannot answer 1 or 3 at all.
        renderPractice(PreviewStates.awaitingAnswer)

        assertEquals(56.dp, slotHeight(5), "degree 5 survives")
        assertEquals(0.dp, slotHeight(3), "degree 3 is untappable")
        assertEquals(0.dp, slotHeight(1), "degree 1 - home - is untappable")
    }

    @Test
    fun `CHARACTERIZATION - 200 percent font scale makes it no better`() {
        // docs/08-UI-SPEC.md §9 requires surviving 200% font scale. Larger text grows the chrome above
        // the ladder, so the squeeze can only worsen. Recorded so the redesign has a baseline to beat.
        renderPractice(PreviewStates.awaitingAnswer, fontScale = MAX_SUPPORTED_FONT_SCALE)

        assertTrue(
            slotHeight(1) < TonicSpacing.minTouchTarget,
            "degree 1 measured ${slotHeight(1)} at 200% font scale",
        )
    }

    private fun slotHeight(degree: Int): Dp =
        compose.onNodeWithTag("degree_button_$degree").getUnclippedBoundsInRoot().height

    private fun renderPractice(
        uiState: PracticeUiState,
        fontScale: Float = 1.0f,
    ) {
        compose.setContent {
            screenHeight = LocalConfiguration.current.screenHeightDp.dp
            WithFontScale(fontScale) {
                TonicTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PracticeContent(
                            uiState = uiState,
                            onDegreeSelected = {},
                            onReplay = {},
                            onSkip = {},
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_SUPPORTED_FONT_SCALE = 2.0f
    }
}

/** Overrides only the font scale, leaving pixel density alone — what the Android display setting does. */
@Composable
private fun WithFontScale(
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density, fontScale),
        content = content,
    )
}
