package com.tonic.feature.practice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R

/**
 * The optional sung answer — docs/30-PHASE-3-SPEC.md §6.3 and §6.4.
 *
 * **Sits above the degree ladder and never replaces it.** §6.3: "Every sung item must offer a one-tap
 * fallback to answer by tapping instead, always visible, no penalty, no different scoring. Someone who
 * can hear it but can't produce it must never be stuck." So this composable adds a control and hides
 * nothing — there is no state of the practice screen in which the ladder is absent because singing is
 * on, and `SungAnswerControlTest` asserts that rather than leaving it to review.
 *
 * The copy is held to §2's rule that "no UI copy may imply singing is the 'real' or 'advanced' way to
 * do it. It's an option, not a tier." Nothing here praises singing, ranks it against tapping, or
 * frames the ladder as a fallback for people who cannot manage the better thing.
 *
 * **No real-time pitch display.** §6.4 decides that outright: a moving indicator showing where your
 * voice sits relative to the target turns a recall task into a matching task, because you can hunt for
 * the answer instead of retrieving it. The listening state says only that it is listening.
 */
@Composable
internal fun SungAnswerControl(
    state: SungCaptureState,
    enabled: Boolean,
    onSing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TonicSpacing.sm),
    ) {
        OutlinedButton(
            onClick = onSing,
            // Disabled only while the item is still playing, exactly as the ladder is - never because
            // a previous answer was unclear. A learner who was not understood must be able to try
            // again immediately, or "unclear is not wrong" is untrue in the only place it is felt.
            enabled = enabled && state != SungCaptureState.LISTENING,
            modifier = Modifier.testTag("sing_answer"),
        ) {
            Text(
                stringResource(
                    if (state == SungCaptureState.LISTENING) {
                        R.string.sung_answer_listening
                    } else {
                        R.string.sung_answer_sing
                    },
                ),
            )
        }

        if (state == SungCaptureState.UNCLEAR) {
            // §5.2's rule, said out loud at the one moment it matters. Neutral about the learner and
            // about their voice: the app did not catch it, which is a statement about the app.
            Text(
                text = stringResource(R.string.sung_answer_unclear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("sung_unclear_notice"),
            )
        }
    }
}

/**
 * The non-scoring accuracy readout — §3 mitigation 4 and §6.4, shown only *after* the answer is in.
 *
 * "How close you were in cents is shown as information, never as a grade, and never enters the
 * staircase or mastery evaluation." Phrased in plain language rather than a number, and deliberately
 * without any word of approval or correction: the answer was already scored on which degree was sung,
 * and this line is about the voice, which is not being marked at all.
 */
@Composable
internal fun SungAccuracyReadout(
    cents: Int,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            kotlin.math.abs(cents) <= CLOSE_ENOUGH_CENTS -> stringResource(R.string.sung_readout_on_pitch)
            cents < 0 -> stringResource(R.string.sung_readout_flat, -cents)
            else -> stringResource(R.string.sung_readout_sharp, cents)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.testTag("sung_accuracy_readout"),
    )
}

/**
 * Inside this, the readout says "on pitch" rather than naming a direction.
 *
 * Not a tolerance — nothing is scored here. It exists because reporting "3 cents flat" invites a
 * learner to chase a number that is inaudible and irrelevant to the exercise, which is the same
 * gravitational pull toward measuring singing that §3 exists to resist.
 */
private const val CLOSE_ENOUGH_CENTS = 15

@Preview(name = "Sung answer - idle", showBackground = true)
@Composable
private fun SungAnswerIdlePreview() {
    TonicTheme(darkTheme = false) {
        SungAnswerControl(state = SungCaptureState.IDLE, enabled = true, onSing = {})
    }
}

@Preview(name = "Sung answer - listening", showBackground = true)
@Composable
private fun SungAnswerListeningPreview() {
    TonicTheme(darkTheme = false) {
        SungAnswerControl(state = SungCaptureState.LISTENING, enabled = true, onSing = {})
    }
}

@Preview(name = "Sung answer - unclear", showBackground = true)
@Composable
private fun SungAnswerUnclearPreview() {
    TonicTheme(darkTheme = false) {
        SungAnswerControl(state = SungCaptureState.UNCLEAR, enabled = true, onSing = {})
    }
}

@Preview(name = "Sung accuracy readout", showBackground = true)
@Composable
private fun SungAccuracyReadoutPreview() {
    TonicTheme(darkTheme = false) {
        SungAccuracyReadout(cents = -42)
    }
}
