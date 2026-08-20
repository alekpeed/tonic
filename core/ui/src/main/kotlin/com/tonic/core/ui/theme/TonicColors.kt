package com.tonic.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The fallback scheme used when dynamic color (Android 12+) isn't available — docs/08-UI-SPEC.md §8.
 * A calm, low-saturation indigo rather than Material's default purple: the app's whole design stance
 * is "the user is listening, not reading," so nothing on screen should compete for attention.
 */
object TonicColors {
    // Brand.
    val Indigo10 = Color(0xFF0A0F2E)
    val Indigo20 = Color(0xFF16205A)
    val Indigo30 = Color(0xFF232F82)
    val Indigo40 = Color(0xFF3A44A8)
    val Indigo80 = Color(0xFFBFC4F2)
    val Indigo90 = Color(0xFFE2E4FA)

    // Neutrals.
    val Slate10 = Color(0xFF11131C)
    val Slate20 = Color(0xFF1B1E2A)
    val Slate90 = Color(0xFFF3F3F7)
    val Slate95 = Color(0xFFF9F9FB)
    val Slate99 = Color(0xFFFDFDFE)

    // Correct/incorrect are never signaled by hue alone (docs/08-UI-SPEC.md §8) — every ladder state
    // pairs one of these with a shape or icon change too. Both are desaturated relative to a typical
    // "success green"/"error red" so neither reads as celebratory or alarming — see docs/08-UI-SPEC.md
    // §1: "a correct answer is not a celebration."
    val CorrectLight = Color(0xFF2E6E4E)
    val CorrectDark = Color(0xFF8FD4AE)
    val IncorrectLight = Color(0xFF8C4A3A)
    val IncorrectDark = Color(0xFFE3A594)
}
