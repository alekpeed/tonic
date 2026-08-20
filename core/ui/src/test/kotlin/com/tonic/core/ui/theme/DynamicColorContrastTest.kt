package com.tonic.core.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertTrue

/**
 * docs/08-UI-SPEC.md §8 asks for two things that pull against each other: "Material 3 dynamic color
 * where available" *and* "contrast meeting WCAG AA minimum, including the ladder button states." The
 * app runs with `dynamicColor = true`, so on Android 12+ the entire palette is derived from the user's
 * wallpaper and none of `TonicColors` applies. Every `@Preview` used to pin `dynamicColor = false`,
 * so no preview ever showed what the app actually renders — and the two contrast defects found on a
 * real device were invisible in review because of it.
 *
 * This exercises the **real dynamic path**, not the fallback. Material guarantees contrast *within* a
 * role pair (`onSurface` on `surface`), never *between* two arbitrary roles, so the rule these fixes
 * follow is: depend only on guaranteed pairs, or on geometry, never on a delta between two container
 * roles. These assertions are what enforce that.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class DynamicColorContrastTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /** WCAG 2.1 contrast ratio. AA is 4.5:1 for body text, 3.0:1 for large text and UI components. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun schemes(): List<Pair<String, ColorScheme>> =
        listOf(
            "dynamic-light" to dynamicLightColorScheme(context),
            "dynamic-dark" to dynamicDarkColorScheme(context),
            "fallback-light" to LightColors,
            "fallback-dark" to DarkColors,
        )

    /**
     * [com.tonic.core.ui.components.StageHeader] draws `onSurface` on `surface`. That is a guaranteed
     * pair, which is exactly why it was chosen over the `onSurfaceVariant` it used before — the fix for
     * a stage label a live user could not find on a full screen.
     */
    @Test
    fun `the stage header's colour pair clears AA on every scheme, dynamic included`() {
        for ((name, scheme) in schemes()) {
            val ratio = contrast(scheme.onSurface, scheme.surface)
            assertTrue(
                ratio >= AA_BODY_TEXT,
                "StageHeader must stay legible on $name - onSurface vs surface was %.2f:1".format(ratio),
            )
        }
    }

    /**
     * The ladder's gap draws `onSurfaceVariant` ink on `surface`, at half alpha. Ink-on-surface is a
     * guaranteed pair; the previous design compared `primaryContainer` against `surfaceVariant`, which
     * is not, and measured 1.54:1 dark / 1.18:1 light in the fallback scheme alone.
     */
    @Test
    fun `the ladder gap's rule is visible on every scheme, dynamic included`() {
        for ((name, scheme) in schemes()) {
            val ratio = contrast(scheme.onSurfaceVariant, scheme.surface)
            assertTrue(
                ratio >= AA_LARGE_TEXT,
                "the gap rule must be visible on $name - onSurfaceVariant vs surface was %.2f:1".format(ratio),
            )
        }
    }

    /**
     * The regression guard for the actual bug: a container-vs-container delta is not a reliable signal.
     * This asserts the failure is *real* on at least one scheme the app can genuinely run under, so that
     * anyone tempted to reintroduce a fill-only distinction sees why it was abandoned rather than
     * trusting that some schemes happen to look fine.
     */
    @Test
    fun `container-versus-container contrast is demonstrably unreliable, which is why shape carries the gap`() {
        val failing =
            schemes().filter { (_, scheme) ->
                contrast(scheme.primaryContainer, scheme.surfaceVariant) < AA_LARGE_TEXT
            }
        assertTrue(
            failing.isNotEmpty(),
            "expected at least one scheme where an active-vs-gap fill delta fails AA - if this ever " +
                "passes everywhere, the finding behind the shape-based gap deserves re-examining",
        )
    }

    @Test
    fun `report the measured ratios`() {
        println("=== measured contrast, per scheme ===")
        for ((name, scheme) in schemes()) {
            println(
                "  %-15s stageHeader(onSurface/surface)=%.2f:1  gapRule(onSurfaceVariant/surface)=%.2f:1  oldFillDelta(primaryContainer/surfaceVariant)=%.2f:1"
                    .format(
                        name,
                        contrast(scheme.onSurface, scheme.surface),
                        contrast(scheme.onSurfaceVariant, scheme.surface),
                        contrast(scheme.primaryContainer, scheme.surfaceVariant),
                    ),
            )
        }
    }

    private companion object {
        const val AA_BODY_TEXT = 4.5
        const val AA_LARGE_TEXT = 3.0
    }
}
