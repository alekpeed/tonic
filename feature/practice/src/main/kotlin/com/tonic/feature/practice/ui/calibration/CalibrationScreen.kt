package com.tonic.feature.practice.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonic.core.model.rhythm.BlockReason
import com.tonic.core.model.rhythm.CalibrationFailure
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R
import com.tonic.feature.practice.ui.rhythm.TapSurface

/**
 * The calibration run — docs/40-PHASE-4-SPEC.md §4.3, with §7.1's explanation in front of it.
 *
 * §7.1 lists calibration among the three things that must be explained before they happen, and gives
 * the register to aim at: "why it exists, in plain terms ('your phone has a small delay; this measures
 * it')". So the screen opens on words and a button, never on a metronome already playing.
 *
 * **No number is ever shown**, including on success. The measured constant is real and is stored, and
 * telling a learner "we measured 38 ms" invites them to run it again for a better score — which is
 * both meaningless (there is no better) and the precise failure docs/02-PEDAGOGY.md §6 keeps latency
 * out of the interface to avoid. Success says it worked.
 */
@Composable
fun CalibrationScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CalibrationContent(
        uiState = uiState,
        onStart = viewModel::onStart,
        onTap = viewModel::onTap,
        onRetry = viewModel::onRetry,
        onDone = onDone,
        modifier = modifier,
    )
}

@Composable
internal fun CalibrationContent(
    uiState: CalibrationUiState,
    onStart: () -> Unit,
    onTap: (Long) -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(TonicSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val blocked = uiState.blocked
        if (blocked != null) {
            // §4.2: "not a warning the user can dismiss into a broken experience - an actual mode
            // change." There is no start button on this branch at all, and the way out named is the
            // one that works.
            Body(stringResource(blockedRes(blocked)), tag = "calibration_blocked")
            Spacer(modifier = Modifier.height(TonicSpacing.lg))
            TextButton(onClick = onDone, modifier = Modifier.testTag("calibration_back")) {
                Text(stringResource(R.string.calibration_back))
            }
            return@Column
        }

        when (uiState.stage) {
            CalibrationStage.EXPLANATION -> {
                Body(stringResource(R.string.calibration_explanation), tag = "calibration_explanation")
                Spacer(modifier = Modifier.height(TonicSpacing.md))
                Body(stringResource(R.string.calibration_instruction), tag = "calibration_instruction")
                Spacer(modifier = Modifier.height(TonicSpacing.lg))
                Button(onClick = onStart, modifier = Modifier.testTag("calibration_start")) {
                    Text(stringResource(R.string.calibration_start))
                }
            }

            CalibrationStage.RUNNING -> {
                Body(stringResource(R.string.calibration_running), tag = "calibration_running")
                Spacer(modifier = Modifier.height(TonicSpacing.md))
                // The same surface practice uses, so the thing being measured is the thing that will
                // be used. A calibration run tapped on a different control would measure a different
                // input path - which is the whole quantity in question.
                TapSurface(enabled = true, onTap = onTap, tapCount = uiState.tapCount)
            }

            CalibrationStage.DONE -> {
                Body(stringResource(R.string.calibration_done), tag = "calibration_done")
                Spacer(modifier = Modifier.height(TonicSpacing.lg))
                Button(onClick = onDone, modifier = Modifier.testTag("calibration_finish")) {
                    Text(stringResource(R.string.calibration_finish))
                }
            }

            CalibrationStage.FAILED -> {
                Body(stringResource(failureRes(uiState.failure)), tag = "calibration_failed")
                Spacer(modifier = Modifier.height(TonicSpacing.lg))
                Button(onClick = onRetry, modifier = Modifier.testTag("calibration_retry")) {
                    Text(stringResource(R.string.calibration_retry))
                }
                Spacer(modifier = Modifier.height(TonicSpacing.sm))
                // docs/08-UI-SPEC.md §2a: every screen has a way out, including a failing one. A
                // learner who cannot get a clean run must not be trapped on the screen that keeps
                // telling them so.
                TextButton(onClick = onDone, modifier = Modifier.testTag("calibration_back")) {
                    Text(stringResource(R.string.calibration_back))
                }
            }
        }
    }
}

@Composable
private fun Body(
    text: String,
    tag: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/**
 * Why tapping is unavailable, in the learner's terms.
 *
 * Each names what to *do*, not what is wrong with their equipment. docs/02-PEDAGOGY.md's standing rule
 * that the app never tells a learner they cannot do something applies to their phone as well.
 */
private fun blockedRes(reason: BlockReason): Int =
    when (reason) {
        BlockReason.BLUETOOTH_OUTPUT -> R.string.calibration_blocked_bluetooth
        BlockReason.UNSUPPORTED_ROUTE -> R.string.calibration_blocked_route

        // The monitor's *starting* value, before the platform has said anything, as well as its
        // answer when the platform says nothing usable. Honestly the same sentence: we do not know
        // where the sound is going, and a constant stored against a slot we guessed would be worse
        // than none. Its own wording rather than sharing the unsupported-route one, which would tell
        // a learner their perfectly ordinary phone was unsupported for the half second before the
        // route resolved.
        BlockReason.ROUTE_UNKNOWN -> R.string.calibration_blocked_unknown

        // Reachable only if this screen ever consults ProductionGate, which it deliberately does not -
        // being uncalibrated is why someone is here. Named rather than defaulted so that a future
        // change which does route it here has to write the sentence.
        BlockReason.NOT_CALIBRATED -> R.string.calibration_blocked_route
    }

/** Null means the run was abandoned for a reason that is not about the measurement. */
private fun failureRes(failure: CalibrationFailure?): Int =
    when (failure) {
        CalibrationFailure.NOT_ENOUGH_TAPS -> R.string.calibration_failed_few_taps
        CalibrationFailure.OFFSET_IMPLAUSIBLE -> R.string.calibration_failed_implausible
        null -> R.string.calibration_failed_interrupted
    }

@Preview(name = "Calibration, explaining itself")
@Composable
private fun CalibrationExplanationPreview() {
    TonicTheme {
        CalibrationContent(
            uiState = CalibrationUiState(),
            onStart = {},
            onTap = {},
            onRetry = {},
            onDone = {},
        )
    }
}

@Preview(name = "Calibration, blocked on Bluetooth")
@Composable
private fun CalibrationBlockedPreview() {
    TonicTheme {
        CalibrationContent(
            uiState = CalibrationUiState(blocked = BlockReason.BLUETOOTH_OUTPUT),
            onStart = {},
            onTap = {},
            onRetry = {},
            onDone = {},
        )
    }
}
