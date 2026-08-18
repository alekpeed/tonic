package com.tonic.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Correct/incorrect feedback tones, kept outside [MaterialTheme.colorScheme]'s fixed slots (there is
 * no "success"/"error-that-isn't-a-form-error" pair in Material 3) but still theme-aware — dynamic
 * color on Android 12+ replaces the brand palette entirely, so these can't be hardcoded constants if
 * they're going to hold up against every dynamic scheme a user's wallpaper produces. In practice they
 * only shift with light/dark, per docs/08-UI-SPEC.md §8's contrast requirement.
 */
data class TonicExtendedColors(
    val correct: Color,
    val incorrect: Color,
)

private val LocalTonicExtendedColors =
    compositionLocalOf {
        TonicExtendedColors(correct = TonicColors.CorrectLight, incorrect = TonicColors.IncorrectLight)
    }

private val LightColors =
    lightColorScheme(
        primary = TonicColors.Indigo40,
        onPrimary = TonicColors.Slate99,
        primaryContainer = TonicColors.Indigo90,
        onPrimaryContainer = TonicColors.Indigo10,
        secondaryContainer = TonicColors.Slate95,
        background = TonicColors.Slate99,
        onBackground = TonicColors.Slate10,
        surface = TonicColors.Slate99,
        onSurface = TonicColors.Slate10,
        surfaceVariant = TonicColors.Slate95,
    )

private val DarkColors =
    darkColorScheme(
        primary = TonicColors.Indigo80,
        onPrimary = TonicColors.Indigo10,
        primaryContainer = TonicColors.Indigo30,
        onPrimaryContainer = TonicColors.Indigo90,
        secondaryContainer = TonicColors.Slate20,
        // Practice sessions happen at night with headphones on - docs/08-UI-SPEC.md §8. True near-black
        // rather than Material's usual dark-grey default, so a bright room-lit phone screen isn't the
        // thing standing between a learner and sleep.
        background = TonicColors.Slate10,
        onBackground = TonicColors.Slate90,
        surface = TonicColors.Slate10,
        onSurface = TonicColors.Slate90,
        surfaceVariant = TonicColors.Slate20,
    )

@Composable
fun TonicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> DarkColors
            else -> LightColors
        }
    val extendedColors =
        if (darkTheme) {
            TonicExtendedColors(correct = TonicColors.CorrectDark, incorrect = TonicColors.IncorrectDark)
        } else {
            TonicExtendedColors(correct = TonicColors.CorrectLight, incorrect = TonicColors.IncorrectLight)
        }

    CompositionLocalProvider(LocalTonicExtendedColors provides extendedColors) {
        MaterialTheme(colorScheme = colorScheme, typography = TonicTypography, content = content)
    }
}

object TonicTheme {
    val extendedColors: TonicExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTonicExtendedColors.current
}
