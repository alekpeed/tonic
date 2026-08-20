package com.tonic.core.ui.ladder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The measurement harness docs/09-BUILD-PLAN.md Stage 7 required and never had.
 *
 * Stage 7's acceptance criterion was "ladder fits seven degrees plus gaps on a 5-inch screen, no
 * scroll, at 200% font scale" and docs/08-UI-SPEC.md §9 says to "test the ladder at maximum scale."
 * Both were reported met. Neither was ever executed: the repository had no Compose UI test and no
 * instrumented test of any kind, and `compose-ui-test-junit4` was wired only into `androidTest`, which
 * this project has no way to run. The one ladder property that *did* get checked in the real world —
 * that inactive gaps read as distinct from buttons — turned out to be false in a user's hands.
 *
 * So this file exists to make the ladder's geometry a measured fact rather than an assertion, and it
 * is a precondition for Phase 2 Stage 2.5: docs/20-PHASE-2-SPEC.md §8.1 decision 1 sizes the piano-
 * geometry redesign against exactly these numbers, and "ladder legible at 12 positions" has to be
 * verifiable rather than asserted.
 *
 * Runs under Robolectric at the 5-inch reference device docs/08-UI-SPEC.md §3 names: `w360dp-h640dp`
 * is what both a 720×1280 xhdpi and a 1080×1920 xxhdpi 5-inch phone report.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = FIVE_INCH_PORTRAIT)
class DegreeLadderLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every one of the seven slots renders, at or above the minimum touch target`() {
        setLadder(ScaleDegree.TONIC_TRIAD.toList())

        // Active degrees are real buttons; the rest are gaps. Both occupy a full slot, which is what
        // keeps the ladder's shape from reshuffling as degrees unlock (docs/08-UI-SPEC.md §3).
        for (degree in ScaleDegree.TONIC_TRIAD) {
            val height = compose.onNodeWithTag("degree_button_${degree.degree}").heightDp()
            assertTrue(
                height >= TonicSpacing.minTouchTarget,
                "degree ${degree.degree} is $height tall, below the ${TonicSpacing.minTouchTarget} " +
                    "minimum touch target docs/08-UI-SPEC.md §1 requires",
            )
        }
    }

    @Test
    fun `a button still clears the minimum touch target at 200 percent font scale`() {
        // docs/08-UI-SPEC.md §9: "Respect system font scaling up to 200% without layout breakage."
        // The button's height is a fixed 56dp while its label scales, so this is the case where text
        // can outgrow its box. Measured, not assumed.
        setLadder(ScaleDegree.TONIC_TRIAD.toList(), fontScale = MAX_SUPPORTED_FONT_SCALE)

        for (degree in ScaleDegree.TONIC_TRIAD) {
            val height = compose.onNodeWithTag("degree_button_${degree.degree}").heightDp()
            assertTrue(
                height >= TonicSpacing.minTouchTarget,
                "at 200% font scale degree ${degree.degree} measured $height",
            )
        }
    }

    @Test
    fun `the ladder's intrinsic height is what the slot arithmetic says, and it is recorded here`() {
        // This is the number docs/20-PHASE-2-SPEC.md §8.1 decision 1 turns on, so it is pinned rather
        // than left to be re-derived by hand. Seven slots at the 56dp touch target with 8dp between
        // them. Twelve slots would be 12*56 + 11*8 = 760dp, which is why chromatic degrees hang off the
        // seams between diatonic neighbors instead of extending the column: 760dp does not fit in the
        // ~568dp a 5-inch screen has after its system bars, before any other UI is placed.
        setLadder(ScaleDegree.ALL_DIATONIC.toList())

        val top = compose.onNodeWithTag("degree_button_7").topDp()
        val bottom = compose.onNodeWithTag("degree_button_1").bottomDp()
        val measured = bottom - top
        val expected = TonicSpacing.minTouchTarget * SLOT_COUNT + TonicSpacing.sm * (SLOT_COUNT - 1)

        assertTrue(
            abs((measured - expected).value) < TOLERANCE_DP,
            "ladder intrinsic height measured $measured, slot arithmetic says $expected",
        )
    }

    @Test
    fun `seven full-size slots exceed a 5-inch screen's usable height on their own`() {
        // The arithmetic behind docs/20-PHASE-2-SPEC.md §8.2's deviation, pinned so it cannot be
        // re-litigated from memory. 440dp of slots against ~568dp of usable screen leaves 128dp for
        // every other thing docs/08-UI-SPEC.md §4 mandates - header, time bar, phase indicator, phase
        // caption, replay, help/skip - which is not enough. That is why the ladder scrolls as a last
        // resort rather than squeezing, and why twelve positions must hang off the seams instead of
        // extending the column.
        //
        // Whether the real screen keeps every button tappable is asserted where it belongs, against the
        // real composable: PracticeScreenLayoutTest in :feature:practice.
        setLadder(ScaleDegree.ALL_DIATONIC.toList())

        val needed =
            compose.onNodeWithTag("degree_button_1").bottomDp() - compose.onNodeWithTag("degree_button_7").topDp()

        assertTrue(
            needed > FIVE_INCH_USABLE_HEIGHT - MINIMUM_PRACTICE_CHROME_HEIGHT,
            "seven slots measured $needed, which now fits alongside the minimum chrome - if that is " +
                "real, revisit docs/20-PHASE-2-SPEC.md §8.2 and the scroll fallback it justifies",
        )
    }

    private fun setLadder(
        active: List<ScaleDegree>,
        fontScale: Float = 1.0f,
    ) {
        compose.setContent {
            WithFontScale(fontScale) {
                TonicTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DegreeLadder(
                            activeDegrees = active,
                            mode = Mode.MAJOR,
                            labelStyle = LabelStyle.NUMBERS,
                            enabled = true,
                            selectedDegree = null,
                            correctDegree = null,
                            onDegreeSelected = {},
                            modifier = Modifier.height(LADDER_MEASUREMENT_HEIGHT),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val SLOT_COUNT = 7
        const val MAX_SUPPORTED_FONT_SCALE = 2.0f
        const val TOLERANCE_DP = 1.0f

        /**
         * Tall enough that the ladder is measured at its intrinsic size rather than at whatever a
         * smaller container would squeeze it into — the point is to learn what it *wants*.
         */
        val LADDER_MEASUREMENT_HEIGHT = 1_000.dp

        /** 640dp tall minus a 24dp status bar and a 48dp navigation bar. */
        val FIVE_INCH_USABLE_HEIGHT = 568.dp

/**
         * The floor for docs/08-UI-SPEC.md §4's mandated chrome, with every discretionary spacer already
         * removed: header 48 + time bar 4 + phase indicator 48 + phase caption 20 + replay 48 +
         * help/skip row 48 + column padding 32. Nothing here can be deleted without dropping a
         * requirement.
         */
        val MINIMUM_PRACTICE_CHROME_HEIGHT = 248.dp
    }
}

/** `w360dp-h640dp` — the 5-inch reference device of docs/08-UI-SPEC.md §3, in Robolectric qualifiers. */
const val FIVE_INCH_PORTRAIT = "w360dp-h640dp"

/**
 * Overrides only the font scale, leaving pixel density alone — which is exactly what the Android
 * display setting does, and what docs/08-UI-SPEC.md §9's "up to 200%" refers to.
 */
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

private fun SemanticsNodeInteraction.heightDp(): Dp = getUnclippedBoundsInRoot().height

private fun SemanticsNodeInteraction.topDp(): Dp = getUnclippedBoundsInRoot().top

private fun SemanticsNodeInteraction.bottomDp(): Dp = getUnclippedBoundsInRoot().bottom
