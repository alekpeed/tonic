package com.tonic.feature.diagnostic.ui

import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.feature.diagnostic.R
import com.tonic.feature.diagnostic.engine.M0SubTest

/** The prompt and two-button copy for one M0 sub-test - plain data, kept out of `DiagnosticScreen.kt` so it's testable without a composition. */
internal data class AnswerCopy(
    val sectionNameRes: Int,
    val setupRes: Int,
    val promptRes: Int,
    val firstLabelRes: Int,
    val firstRawLabel: String,
    val secondLabelRes: Int,
    val secondRawLabel: String,
)

internal fun answerCopyFor(subTest: M0SubTest): AnswerCopy =
    when (subTest) {
        M0SubTest.PITCH_DIRECTION ->
            AnswerCopy(
                R.string.diagnostic_section_name_pitch_direction,
                R.string.diagnostic_setup_pitch_direction,
                R.string.diagnostic_prompt_pitch_direction,
                R.string.diagnostic_answer_higher,
                AnswerAlphabet.HigherLower.HIGHER,
                R.string.diagnostic_answer_lower,
                AnswerAlphabet.HigherLower.LOWER,
            )
        M0SubTest.SAME_DIFFERENT ->
            AnswerCopy(
                R.string.diagnostic_section_name_same_different,
                R.string.diagnostic_setup_same_different,
                R.string.diagnostic_prompt_same_different,
                R.string.diagnostic_answer_same,
                AnswerAlphabet.SameDifferent.SAME,
                R.string.diagnostic_answer_different,
                AnswerAlphabet.SameDifferent.DIFFERENT,
            )
        M0SubTest.TONAL_MEMORY ->
            AnswerCopy(
                R.string.diagnostic_section_name_tonal_memory,
                R.string.diagnostic_setup_tonal_memory,
                R.string.diagnostic_prompt_tonal_memory,
                R.string.diagnostic_answer_same,
                AnswerAlphabet.SameDifferent.SAME,
                R.string.diagnostic_answer_different,
                AnswerAlphabet.SameDifferent.DIFFERENT,
            )
        M0SubTest.AMUSIA_SCREEN ->
            AnswerCopy(
                R.string.diagnostic_section_name_amusia_screen,
                R.string.diagnostic_setup_amusia_screen,
                R.string.diagnostic_prompt_amusia_screen,
                R.string.diagnostic_answer_sounded_right,
                AnswerAlphabet.IntactAltered.INTACT,
                R.string.diagnostic_answer_something_off,
                AnswerAlphabet.IntactAltered.ALTERED,
            )
    }
