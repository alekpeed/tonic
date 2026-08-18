package com.tonic.feature.diagnostic.ui

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
import com.tonic.core.ui.components.BinaryChoiceButtons
import com.tonic.core.ui.components.MinimalProgressIndicator
import com.tonic.core.ui.components.PlaybackPhase
import com.tonic.core.ui.components.PlaybackPhaseIndicator
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.diagnostic.R
import com.tonic.feature.diagnostic.engine.M0SubTest

/** docs/08-UI-SPEC.md §5: same structural language as Practice, simpler binary-choice answer widgets. */
@Composable
fun DiagnosticScreen(
    onContinue: () -> Unit,
    viewModel: DiagnosticViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        !uiState.hasStarted -> IntroState(onBegin = viewModel::begin)
        uiState.isFinished -> ResultState(outcome = uiState.outcome, onContinue = onContinue)
        else ->
            RunningState(
                uiState = uiState,
                onAnswerSelected = viewModel::onAnswerSelected,
                onReplay = viewModel::onReplay,
            )
    }
}

@Composable
private fun IntroState(onBegin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(TonicSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.diagnostic_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(
            text = stringResource(R.string.diagnostic_intro_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.xl))
        Button(onClick = onBegin, modifier = Modifier.testTag("diagnostic_begin")) {
            Text(stringResource(R.string.diagnostic_begin))
        }
    }
}

@Composable
private fun RunningState(
    uiState: DiagnosticUiState,
    onAnswerSelected: (String) -> Unit,
    onReplay: () -> Unit,
) {
    val subTest = uiState.currentSubTest
    val item = uiState.currentItem
    Column(
        modifier = Modifier.fillMaxSize().padding(TonicSpacing.md),
    ) {
        MinimalProgressIndicator(itemsCompleted = uiState.subTestIndex, itemsPlanned = uiState.totalSubTests)
        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        PlaybackPhaseIndicator(
            phase = if (uiState.inputEnabled) PlaybackPhase.AWAITING_ANSWER else PlaybackPhase.TARGET,
            modifier = Modifier.padding(vertical = TonicSpacing.lg),
        )

        TextButton(
            onClick = onReplay,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("diagnostic_replay"),
        ) {
            Text(stringResource(R.string.diagnostic_replay))
        }

        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        if (subTest != null) {
            val copy = answerCopyFor(subTest)
            Text(
                text = stringResource(copy.promptRes),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(TonicSpacing.lg))
            BinaryChoiceButtons(
                firstLabel = stringResource(copy.firstLabelRes),
                secondLabel = stringResource(copy.secondLabelRes),
                enabled = uiState.inputEnabled && item != null,
                onFirstSelected = { onAnswerSelected(copy.firstRawLabel) },
                onSecondSelected = { onAnswerSelected(copy.secondRawLabel) },
            )
        }
    }
}

@Composable
private fun ResultState(
    outcome: DiagnosticOutcome?,
    onContinue: () -> Unit,
) {
    val titleRes =
        if (outcome == DiagnosticOutcome.START_WITH_FUNDAMENTALS) {
            R.string.diagnostic_result_fundamentals_title
        } else {
            R.string.diagnostic_result_proceed_title
        }
    val bodyRes =
        if (outcome == DiagnosticOutcome.START_WITH_FUNDAMENTALS) {
            R.string.diagnostic_result_fundamentals_body
        } else {
            R.string.diagnostic_result_proceed_body
        }

    Column(
        modifier = Modifier.fillMaxSize().padding(TonicSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("diagnostic_result_title"),
        )
        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(text = stringResource(bodyRes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(TonicSpacing.xl))
        Button(onClick = onContinue, modifier = Modifier.testTag("diagnostic_continue")) {
            Text(stringResource(R.string.diagnostic_continue))
        }
    }
}

@Preview(name = "Intro", showBackground = true)
@Composable
private fun IntroPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        IntroState(onBegin = {})
    }
}

@Preview(name = "Running - pitch direction", showBackground = true)
@Composable
private fun RunningPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        RunningState(
            uiState =
                DiagnosticUiState(
                    hasStarted = true,
                    currentSubTest = M0SubTest.PITCH_DIRECTION,
                    subTestIndex = 0,
                    inputEnabled = true,
                ),
            onAnswerSelected = {},
            onReplay = {},
        )
    }
}

@Preview(name = "Result - proceed to practice", showBackground = true)
@Composable
private fun ResultProceedPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        ResultState(outcome = DiagnosticOutcome.PROCEED_TO_PRACTICE, onContinue = {})
    }
}

@Preview(name = "Result - fundamentals", showBackground = true)
@Composable
private fun ResultFundamentalsPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        ResultState(outcome = DiagnosticOutcome.START_WITH_FUNDAMENTALS, onContinue = {})
    }
}
