package com.tonic.feature.practice.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.AudiatedPitch
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.LabelStyle
import com.tonic.core.ui.components.PlaybackPhase
import com.tonic.core.ui.labels.displayLabel
import com.tonic.core.ui.ladder.DegreeLadder
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R
import com.tonic.feature.practice.ui.rhythm.RhythmAnswerArea

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
 *
 * That claim was **false when it was written**, and `Item.RhythmItem` is what proved it. The dispatch
 * ended in `else ->`, so a rhythm item did not fail to compile here: it fell into the recognition arm
 * and drew an empty degree ladder — the exact failure the paragraph above describes `M9` having, in
 * the file whose comment says it cannot happen again. The `when` is now exhaustive over the sealed
 * hierarchy, with the six `M0`/`M1` diagnostic types named and refused rather than defaulted, because
 * `:feature:diagnostic` has its own loop and one of them reaching this screen is a routing bug worth
 * hearing about.
 */
@Composable
internal fun AnswerArea(
    uiState: PracticeUiState,
    onDegreeSelected: (ScaleDegree) -> Unit,
    onLabelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSing: () -> Unit = {},
    onTap: (Long) -> Unit = {},
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

        is Item.RhythmItem ->
            RhythmAnswerArea(
                item = item,
                enabled = uiState.inputEnabled,
                selectedLabel = uiState.selectedAnswerLabel,
                correctLabel = uiState.correctAnswerLabel,
                onLabelSelected = onLabelSelected,
                onTap = onTap,
                modifier = modifier,
                hapticsEnabled = uiState.hapticsEnabled,
                reduceMotion = uiState.reduceMotion,
                audibleTaps = uiState.audibleTaps,
                tapCount = uiState.tapCount,
            )

        // The six diagnostic types belong to :feature:diagnostic's own loop and have no control here.
        // Named rather than swept into a default so that adding an item type is a compile error in
        // this file, which is what the paragraph above promised and did not deliver.
        is Item.PitchDirectionItem,
        is Item.SameDifferentItem,
        is Item.TonalMemoryItem,
        is Item.ContourItem,
        is Item.StepLeapItem,
        is Item.AmusiaScreenItem,
        ->
            error(
                "${item::class.simpleName} is a diagnostic item and cannot be answered on the practice " +
                    "screen - :feature:diagnostic has its own loop for M0/M1",
            )

        is Item.FunctionalRecognitionItem, null ->
            Column(modifier = modifier) {
                // Above the ladder, never instead of it - docs/30-PHASE-3-SPEC.md §6.3. The whole
                // point of the fallback is that it is *always visible*, so there is deliberately no
                // branch here that swaps one control for the other: singing adds a row and takes
                // nothing away. SungAnswerControlTest asserts the ladder survives every sung state.
                if (uiState.sungResponseAvailable) {
                    SungAnswerControl(
                        state = uiState.sungCapture,
                        enabled = uiState.inputEnabled,
                        onSing = onSing,
                    )
                    Spacer(modifier = Modifier.height(TonicSpacing.md))
                }
                DegreeLadder(
                    activeDegrees = uiState.activeDegrees,
                    // Which degrees form the spine and which hang beside them as alterations - see
                    // DegreeLadder's slot loop. Not simply the item's mode: at M10.MIXED_MODE a spine
                    // that followed the item would reshape the whole column per item and announce the
                    // mode more loudly than the buttons ever could. See SkillGraph.ladderSpineMode.
                    mode =
                        uiState.recognitionItem?.let { SkillGraph.ladderSpineMode(it.skill, it.mode) }
                            ?: Mode.MAJOR,
                    labelStyle = uiState.labelStyle,
                    enabled = uiState.inputEnabled,
                    selectedDegree = uiState.selectedDegree,
                    correctDegree = uiState.correctDegree,
                    reduceMotion = uiState.reduceMotion,
                    onDegreeSelected = onDegreeSelected,
                )
                // §6.4's non-scoring readout, only once the answer is in (correctDegree set) - shown
                // during feedback, never while the learner is still deciding, and never as a grade.
                val sungCents = uiState.lastSungCents
                if (sungCents != null && uiState.correctDegree != null) {
                    Spacer(modifier = Modifier.height(TonicSpacing.sm))
                    SungAccuracyReadout(
                        cents = sungCents,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
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

        // docs/30-PHASE-3-SPEC.md §5.4: an invitation, during the gap, and only while the gap lasts.
        // There is no button — the capture runs itself (see PracticeViewModel.captureAudiation) — so
        // this line's only job is to make sure the learner is not singing into what looks like a dead
        // screen. §6.4 rules out anything more: no meter, no moving indicator, nothing that would let
        // them hunt for the pitch instead of recalling it.
        if (uiState.sungResponseAvailable && uiState.phase == PlaybackPhase.AUDIATION_GAP) {
            Text(
                text = stringResource(R.string.m12_sung_listening),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("m12_sung_listening"),
            )
        }

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

        if (uiState.sungResponseAvailable && uiState.correctAnswerLabel != null) {
            AudiationReadout(
                audiated = uiState.audiatedPitch,
                labelStyle = uiState.labelStyle,
            )
        }
    }
}

/**
 * What the learner sang into the gap, told back to them after the answer — docs/30-PHASE-3-SPEC.md §6.4.
 *
 * **Not a second verdict.** The attempt was scored on the button and is already resolved by the time
 * this appears; §5.4 is explicit that the sung pitch supplements the judgment rather than replacing
 * it, so nothing here says right or wrong, and it is deliberately shown for a correct answer and an
 * incorrect one alike. What it adds is the one thing the button cannot express: whether the note being
 * judged against was the note that was asked for. §5.4 calls that out as the reason to keep the two
 * apart — "a learner who sings the right pitch and then misreports the direction has a specific,
 * diagnosable problem" — and a learner who audiated `5` when told `3` has a different one, which they
 * can only act on if someone tells them it happened.
 *
 * The cents readout appears only when the right degree was held, because [SungAccuracyReadout]'s copy
 * ("about 40 cents below that note") describes a voice missing a note it was aiming at. Attached to a
 * learner who held the wrong degree entirely it would report a large number about the wrong question,
 * which reads as a harsh grade on singing — exactly what §3 mitigation 4 exists to prevent.
 */
@Composable
private fun AudiationReadout(
    audiated: AudiatedPitch?,
    labelStyle: LabelStyle,
) {
    Spacer(modifier = Modifier.height(TonicSpacing.sm))
    if (audiated == null) {
        // Said out loud rather than left blank. The learner sang and heard nothing back; silence would
        // read as the app having judged it, which is the one thing that did not happen (§6.5).
        Text(
            text = stringResource(R.string.m12_sung_not_caught),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("m12_sung_not_caught"),
        )
        return
    }
    Text(
        text = stringResource(R.string.m12_sung_held, audiated.degree.displayLabel(labelStyle)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag("m12_sung_held"),
    )
    if (audiated.heldStatedDegree) {
        SungAccuracyReadout(cents = audiated.centsFromStated)
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
