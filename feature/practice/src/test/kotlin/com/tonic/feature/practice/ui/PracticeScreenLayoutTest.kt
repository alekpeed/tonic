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
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
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
    fun `every active degree button is fully tappable at the starting node`() {
        // Was a CHARACTERIZATION test asserting the defect: on this exact screen degrees 1 and 3
        // measured 0dp and could not be pressed. Inverted now that the layout is fixed, per that test's
        // own instruction. M2.DEG_SET_1 is where every user starts, so this is the case that matters
        // most.
        renderPractice(PreviewStates.awaitingAnswer)

        for (degree in listOf(1, 3, 5)) {
            assertTrue(
                slotHeight(degree) >= TonicSpacing.minTouchTarget,
                "degree $degree measured ${slotHeight(degree)}, below the " +
                    "${TonicSpacing.minTouchTarget} touch target docs/08-UI-SPEC.md §1 requires",
            )
        }
    }

    @Test
    fun `the starting node needs no scrolling - every button is on screen at once`() {
        // docs/08-UI-SPEC.md §3's actual intent. The ladder can now scroll as a last resort, but it
        // must not have to at the node users are actually on: hunting for the tonic mid-answer is the
        // thing §3 exists to prevent.
        renderPractice(PreviewStates.awaitingAnswer)

        for (degree in listOf(1, 3, 5)) {
            val bounds = compose.onNodeWithTag("degree_button_$degree").getUnclippedBoundsInRoot()
            assertTrue(
                bounds.bottom <= screenHeight,
                "degree $degree ends at ${bounds.bottom}, past the $screenHeight screen bottom",
            )
        }
    }

    @Test
    fun `no active degree is ever squeezed, even at the full diatonic set`() {
        // Seven 56dp buttons genuinely do not fit a 5-inch screen alongside the chrome §4 mandates
        // (7*56 + 6*8 = 440dp against roughly 308dp available), so this set scrolls - see
        // DegreeLadder's scroll note and docs/20-PHASE-2-SPEC.md §8.2. What must never happen again is
        // a button rendered at a height nobody can press, and that is what this asserts.
        renderPractice(PreviewStates.fullDiatonicSet)

        for (degree in 1..7) {
            assertTrue(
                slotHeight(degree) >= TonicSpacing.minTouchTarget,
                "degree $degree measured ${slotHeight(degree)} - squeezed below the touch target",
            )
        }
    }

    @Test
    fun `at twelve degrees every button is still tappable on the real screen`() {
        // Stage 2.5's acceptance criterion where it actually has to hold: not the ladder in isolation
        // but the ladder competing with docs/08-UI-SPEC.md §4's chrome on a 5-inch screen. This is the
        // widest answer set the app ever shows.
        renderPractice(PreviewStates.fullChromaticSet)

        for (degree in ScaleDegree.ALL_CHROMATIC) {
            val bounds =
                compose.onNodeWithTag("degree_button_${degree.canonicalLabel}").getUnclippedBoundsInRoot()
            assertTrue(
                bounds.height >= TonicSpacing.minTouchTarget,
                "${degree.canonicalLabel} measured ${bounds.height} - squeezed below the touch target",
            )
            assertTrue(
                bounds.width >= TonicSpacing.minTouchTarget,
                "${degree.canonicalLabel} measured ${bounds.width} wide - narrower than the touch target",
            )
        }
    }

    @Test
    fun `buttons keep their touch target at 200 percent font scale`() {
        // docs/08-UI-SPEC.md §9. Larger text grows the chrome above the ladder, which is what used to
        // push the tonic to zero height; the scroll fallback means it now costs scroll, not tappability.
        renderPractice(PreviewStates.awaitingAnswer, fontScale = MAX_SUPPORTED_FONT_SCALE)

        for (degree in listOf(1, 3, 5)) {
            assertTrue(
                slotHeight(degree) >= TonicSpacing.minTouchTarget,
                "at 200% font scale degree $degree measured ${slotHeight(degree)}",
            )
        }
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
