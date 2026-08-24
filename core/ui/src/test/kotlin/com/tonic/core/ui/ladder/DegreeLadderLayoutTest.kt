package com.tonic.core.ui.ladder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.width
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

    @Test
    fun `all twelve chromatic degrees render, each at or above the minimum touch target`() {
        // Stage 2.5's acceptance criterion - docs/20-PHASE-2-SPEC.md, "ladder legible at 12
        // positions (or redesigned)" - measured rather than asserted, which is the entire reason §8.1
        // decision 1 required this harness before the redesign.
        setLadder(ScaleDegree.ALL_CHROMATIC.toList())

        for (degree in ScaleDegree.ALL_CHROMATIC) {
            val node = compose.onNodeWithTag("degree_button_${degree.canonicalLabel}")
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(
                bounds.height >= TonicSpacing.minTouchTarget,
                "${degree.canonicalLabel} is ${bounds.height} tall, below the touch-target minimum",
            )
            assertTrue(
                bounds.width >= TonicSpacing.minTouchTarget,
                "${degree.canonicalLabel} is ${bounds.width} wide, below the touch-target minimum - " +
                    "docs/20-PHASE-2-SPEC.md §5.2: adding chromatic positions must not shrink buttons " +
                    "below it",
            )
        }
    }

    @Test
    fun `twelve degrees cost no more vertical height than seven`() {
        // The property docs/20-PHASE-2-SPEC.md §8.1 decision 1 is built on and §8.2 depends on. A
        // twelve-*slot* column would be 12*56 + 11*8 = 760dp against ~568dp of usable screen; hanging
        // the chromatic degrees off their diatonic neighbors keeps the column at seven slots, so the
        // ladder's height is identical whether the learner has 3 degrees or all 12.
        // One composition, recomposed - the rule allows setContent only once per test, and comparing
        // across two renders of the same ladder is the point anyway.
        val active = mutableStateOf(ScaleDegree.ALL_DIATONIC.toList())
        setLadder(active)

        val diatonicHeight =
            compose.onNodeWithTag("degree_button_1").bottomDp() - compose.onNodeWithTag("degree_button_7").topDp()

        active.value = ScaleDegree.ALL_CHROMATIC.toList()
        compose.waitForIdle()
        val chromaticHeight =
            compose.onNodeWithTag("degree_button_1").bottomDp() - compose.onNodeWithTag("degree_button_7").topDp()

        assertTrue(
            abs((chromaticHeight - diatonicHeight).value) < TOLERANCE_DP,
            "seven degrees occupy $diatonicHeight and twelve occupy $chromaticHeight - if these have " +
                "diverged, the chromatic degrees have started extending the column and §8.2's " +
                "arithmetic no longer holds",
        )
    }

    @Test
    fun `twelve degrees still clear the touch target at 200 percent font scale`() {
        // docs/20-PHASE-2-SPEC.md §5.2 asks for exactly this case: twelve positions, 5-inch screen,
        // maximum font scale. The narrow alteration buttons are where it would break first - they get a
        // third of the row width and the same label growth as the spine.
        setLadder(ScaleDegree.ALL_CHROMATIC.toList(), fontScale = MAX_SUPPORTED_FONT_SCALE)

        for (degree in ScaleDegree.ALL_CHROMATIC) {
            val bounds = compose.onNodeWithTag("degree_button_${degree.canonicalLabel}").getUnclippedBoundsInRoot()
            assertTrue(
                bounds.height >= TonicSpacing.minTouchTarget && bounds.width >= TonicSpacing.minTouchTarget,
                "at 200% font scale ${degree.canonicalLabel} measured ${bounds.width} x ${bounds.height}",
            )
        }
    }

    @Test
    fun `a chromatic degree is drawn beside the diatonic note it is named after, never in place of it`() {
        // What "legible at 12 positions" has to mean pedagogically (§2.2): ♯4 is only recognizable as
        // "not 4, not 5", so 4 must still be on screen and adjacent when ♯4 appears. Sorted by pitch,
        // lower on the left - so ♯4 sits to the right of 4, and ♭2 to the left of 2.
        setLadder(ScaleDegree.ALL_CHROMATIC.toList())

        val four = compose.onNodeWithTag("degree_button_4").getUnclippedBoundsInRoot()
        val sharpFour = compose.onNodeWithTag("degree_button_#4").getUnclippedBoundsInRoot()
        assertTrue(abs((four.top - sharpFour.top).value) < TOLERANCE_DP, "♯4 shares slot 4's row")
        assertTrue(sharpFour.left >= four.right, "♯4 is the higher pitch, so it sits to the right of 4")

        val two = compose.onNodeWithTag("degree_button_2").getUnclippedBoundsInRoot()
        val flatTwo = compose.onNodeWithTag("degree_button_b2").getUnclippedBoundsInRoot()
        assertTrue(abs((two.top - flatTwo.top).value) < TOLERANCE_DP, "♭2 shares slot 2's row")
        assertTrue(flatTwo.right <= two.left, "♭2 is the lower pitch, so it sits to the left of 2")

        // And the spine still dominates: an alteration reads as secondary by size, which is §8.1
        // decision 1's "distinguished by shape and position rather than by fill".
        assertTrue(four.width > sharpFour.width, "the diatonic 4 keeps the wider spine slot")
    }

    private fun setLadder(
        active: List<ScaleDegree>,
        fontScale: Float = 1.0f,
    ) = setLadder(mutableStateOf(active), fontScale)

    private fun setLadder(
        active: State<List<ScaleDegree>>,
        fontScale: Float = 1.0f,
    ) {
        compose.setContent {
            WithFontScale(fontScale) {
                TonicTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DegreeLadder(
                            activeDegrees = active.value,
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
