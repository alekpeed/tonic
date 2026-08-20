package com.tonic.feature.practice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.ui.labels.displayLabel
import com.tonic.core.ui.ladder.DegreeLadder
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R

/**
 * The answer control for whichever item type is on screen.
 *
 * A REAL BUG this exists to close. `PracticeContent` rendered the degree ladder unconditionally, so an
 * `M9` item — which has no degrees — put an empty ladder on screen and **no major/minor buttons at
 * all**. The item type, the generator, the ViewModel's answer handler, and the explanation screen
 * were all built in Stage 2.2 and correct; the screen simply never grew a branch for them, and the
 * module was unreachable from Home, so nothing exercised the gap. Writing `M12`'s three buttons
 * without also giving `M9` its two would have meant authoring this dispatch and deliberately leaving
 * one arm of it broken.
 *
 * Dispatching in one place also means a future item type is a compile error here rather than a blank
 * area at runtime, which is the same reasoning behind `PracticeItems` in the engine layer.
 */
@Composable
internal fun AnswerArea(
    uiState: PracticeUiState,
    onDegreeSelected: (ScaleDegree) -> Unit,
    onLabelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val item = uiState.item) {
        is Item.ModeIdentificationItem ->
            ModeAnswerButtons(
                enabled = uiState.inputEnabled,
                selectedLabel = uiState.selectedAnswerLabel,
                correctLabel = uiState.correctAnswerLabel,
                onLabelSelected = onLabelSelected,
                modifier = modifier,
            )

        is Item.PredictionItem ->
            PredictionAnswerArea(
                item = item,
                uiState = uiState,
                onLabelSelected = onLabelSelected,
                modifier = modifier,
            )

        else ->
            Box(modifier = modifier) {
                DegreeLadder(
                    activeDegrees = uiState.activeDegrees,
                    // The item's own mode decides which degrees form the ladder's spine and which hang
                    // beside them as alterations - see DegreeLadder's slot loop.
                    mode = uiState.recognitionItem?.mode ?: Mode.MAJOR,
                    labelStyle = uiState.labelStyle,
                    enabled = uiState.inputEnabled,
                    selectedDegree = uiState.selectedDegree,
                    correctDegree = uiState.correctDegree,
                    reduceMotion = uiState.reduceMotion,
                    onDegreeSelected = onDegreeSelected,
                )
            }
    }
}

/** `M9`: two buttons, nothing else. The whole question is which of two flavors the passage had. */
@Composable
private fun ModeAnswerButtons(
    enabled: Boolean,
    selectedLabel: String?,
    correctLabel: String?,
    onLabelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        for (label in AnswerAlphabet.MajorMinor.labels) {
            AnswerButton(
                label = stringResource(modeLabelRes(label)),
                testTag = "mode_button_$label",
                enabled = enabled,
                isSelected = selectedLabel == label,
                isCorrect = correctLabel == label,
                revealed = correctLabel != null,
                onClick = { onLabelSelected(label) },
            )
            Spacer(modifier = Modifier.height(TonicSpacing.md))
        }
    }
}

/**
 * `M12` — docs/20-PHASE-2-SPEC.md §5.3.
 *
 * The named degree is displayed large and *persistently*, from the moment the cadence ends through
 * the silent gap and the sounded note. §5.3 warns that it "must not look like a revealed answer," and
 * the thing that keeps it from reading that way is the accompanying line: it is phrased as an
 * instruction ("hear this one") and it is on screen *before* anything sounds, which a revealed answer
 * never is.
 *
 * Three buttons from the first item onward (§8.1 decision 3), so the interaction never changes shape
 * mid-module even though `M12.PREDICT_TRIAD` scores either direction the same.
 */
@Composable
private fun PredictionAnswerArea(
    item: Item.PredictionItem,
    uiState: PracticeUiState,
    onLabelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.m12_hold_this_one),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = item.statedDegree.displayLabel(uiState.labelStyle),
            style = MaterialTheme.typography.displayLarge,
            modifier =
                Modifier
                    .padding(vertical = TonicSpacing.sm)
                    .testTag("prediction_stated_degree")
                    .semantics {
                        contentDescription = "hold degree ${spoken(item.statedDegree)} in your head"
                    },
        )

        Spacer(modifier = Modifier.height(TonicSpacing.sm))

        for (label in AnswerAlphabet.MatchDirection.labels) {
            AnswerButton(
                label = stringResource(matchLabelRes(label)),
                testTag = "match_button_$label",
                enabled = uiState.inputEnabled,
                isSelected = uiState.selectedAnswerLabel == label,
                isCorrect = uiState.correctAnswerLabel == label,
                revealed = uiState.correctAnswerLabel != null,
                onClick = { onLabelSelected(label) },
            )
            Spacer(modifier = Modifier.height(TonicSpacing.sm))
        }
    }
}

/**
 * One full-width answer button, shared by the two-choice and three-choice layouts.
 *
 * Feedback follows the ladder's rule (docs/08-UI-SPEC.md §8): color is never the only signal, so the
 * correct answer also gains a filled container and the chosen-but-wrong one an outline, and both are
 * spoken through [contentDescription] rather than shown only as a hue.
 */
@Composable
private fun AnswerButton(
    label: String,
    testTag: String,
    enabled: Boolean,
    isSelected: Boolean,
    isCorrect: Boolean,
    revealed: Boolean,
    onClick: () -> Unit,
) {
    val extended = TonicTheme.extendedColors
    val description =
        when {
            revealed && isCorrect -> "$label, correct answer"
            revealed && isSelected -> "$label, your answer, incorrect"
            else -> label
        }
    val modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = TonicSpacing.minTouchTarget)
            .testTag(testTag)
            .semantics { contentDescription = description }

    when {
        revealed && isCorrect ->
            Button(
                onClick = onClick,
                enabled = false,
                colors =
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = extended.correct.copy(alpha = 0.22f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier = modifier,
            ) { Text(label) }

        revealed && isSelected ->
            OutlinedButton(
                onClick = onClick,
                enabled = false,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        disabledContainerColor = extended.incorrect.copy(alpha = 0.22f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier = modifier,
            ) { Text(label) }

        else ->
            OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    }
}

private fun modeLabelRes(label: String): Int =
    when (label) {
        AnswerAlphabet.MajorMinor.MAJOR -> R.string.m9_answer_MAJOR
        AnswerAlphabet.MajorMinor.MINOR -> R.string.m9_answer_MINOR
        else -> error("not a mode answer: $label")
    }

private fun matchLabelRes(label: String): Int =
    when (label) {
        AnswerAlphabet.MatchDirection.MATCHED -> R.string.m12_answer_matched
        AnswerAlphabet.MatchDirection.TOO_LOW -> R.string.m12_answer_too_low
        AnswerAlphabet.MatchDirection.TOO_HIGH -> R.string.m12_answer_too_high
        else -> error("not a match answer: $label")
    }

/** Spoken form for TalkBack — "flat 3", never the glyph, for the same reason the ladder does it. */
private fun spoken(degree: ScaleDegree): String =
    when {
        degree.alteration < 0 -> "flat ${degree.degree}"
        degree.alteration > 0 -> "sharp ${degree.degree}"
        else -> "${degree.degree}"
    }
