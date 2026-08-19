package com.tonic.feature.practice.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.ui.components.MinimalProgressIndicator
import com.tonic.core.ui.components.PlaybackPhaseIndicator
import com.tonic.core.ui.ladder.DegreeLadder
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R

/** docs/08-UI-SPEC.md §4: progress indicator, playback state, replay, ladder, skip - top to bottom. */
@Composable
fun PracticeScreen(
    onSessionComplete: (Long) -> Unit,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) { viewModel.startIfNeeded() }
    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collect { haptic.performHapticFeedback(HapticFeedbackType.VirtualKey) }
    }
    LaunchedEffect(uiState.isFinished, uiState.sessionId) {
        val sessionId = uiState.sessionId
        if (uiState.isFinished && sessionId != null) onSessionComplete(sessionId)
    }

    BackgroundingObserver(onBackgrounded = viewModel::onAppBackgrounded)

    val resumable = uiState.resumableSession
    if (resumable != null) {
        ResumeOfferState(onContinue = viewModel::onResumeSession, onStartFresh = viewModel::onStartFreshSession)
        return
    }

    if (uiState.isPaused) {
        PausedState(onContinue = viewModel::onResumeFromPause)
        return
    }

    if (uiState.isLoading || uiState.item == null) {
        LoadingState(isFinished = uiState.isFinished)
        return
    }

    PracticeContent(
        uiState = uiState,
        onDegreeSelected = viewModel::onDegreeSelected,
        onReplay = viewModel::onReplay,
        onSkip = viewModel::onSkip,
    )
}

/**
 * docs/06-AUDIO-ENGINE.md §8's backgrounding case. `ON_STOP` also fires on a configuration change, so
 * [Activity.isChangingConfigurations] gates it: a rotation must leave the item intact
 * (docs/10-TESTING.md §11 — "rotation mid-item → no audio restart, no state loss"), and the ViewModel
 * that owns the item already survives one. Only a genuine background transition discards.
 */
@Composable
private fun BackgroundingObserver(onBackgrounded: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && context.findActivity()?.isChangingConfigurations != true) {
                    onBackgrounded()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun ResumeOfferState(
    onContinue: () -> Unit,
    onStartFresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(TonicSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.practice_resume_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("practice_resume_title"),
        )
        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(
            text = stringResource(R.string.practice_resume_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.xl))
        Button(onClick = onContinue, modifier = Modifier.testTag("practice_resume_continue")) {
            Text(stringResource(R.string.practice_resume_continue))
        }
        Spacer(modifier = Modifier.height(TonicSpacing.sm))
        TextButton(onClick = onStartFresh, modifier = Modifier.testTag("practice_resume_start_fresh")) {
            Text(stringResource(R.string.practice_resume_start_fresh))
        }
    }
}

@Composable
private fun PausedState(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(TonicSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.practice_paused_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("practice_paused_title"),
        )
        Spacer(modifier = Modifier.height(TonicSpacing.md))
        Text(
            text = stringResource(R.string.practice_paused_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TonicSpacing.xl))
        Button(onClick = onContinue, modifier = Modifier.testTag("practice_paused_continue")) {
            Text(stringResource(R.string.practice_paused_continue))
        }
    }
}

@Composable
private fun LoadingState(isFinished: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isFinished) {
            Text(
                text = stringResource(R.string.practice_session_complete),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("practice_session_complete"),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.testTag("practice_loading"))
        }
    }
}

@Composable
private fun PracticeContent(
    uiState: PracticeUiState,
    onDegreeSelected: (ScaleDegree) -> Unit,
    onReplay: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(TonicSpacing.md),
    ) {
        MinimalProgressIndicator(
            itemsCompleted = uiState.itemsCompleted,
            itemsPlanned = uiState.itemsPlanned,
        )

        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        PlaybackPhaseIndicator(
            phase = uiState.phase,
            reduceMotion = uiState.reduceMotion,
            modifier = Modifier.padding(vertical = TonicSpacing.lg),
        )

        TextButton(
            onClick = onReplay,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("replay_button"),
        ) {
            Text(stringResource(R.string.practice_replay))
        }

        Spacer(modifier = Modifier.height(TonicSpacing.md))

        Box(modifier = Modifier.weight(1f)) {
            DegreeLadder(
                activeDegrees = uiState.activeDegrees,
                labelStyle = uiState.labelStyle,
                enabled = uiState.inputEnabled,
                selectedDegree = uiState.selectedDegree,
                correctDegree = uiState.correctDegree,
                reduceMotion = uiState.reduceMotion,
                onDegreeSelected = onDegreeSelected,
            )
        }

        Spacer(modifier = Modifier.height(TonicSpacing.sm))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.align(Alignment.End).testTag("skip_button"),
        ) {
            Text(
                text = stringResource(R.string.practice_skip),
                color = LocalContentColor.current.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(name = "Awaiting answer - light", showBackground = true)
@Composable
private fun PracticeContentIdlePreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        PracticeContent(uiState = PreviewStates.awaitingAnswer, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Awaiting answer - dark", showBackground = true, uiMode = 0x20)
@Composable
private fun PracticeContentIdleDarkPreview() {
    TonicTheme(darkTheme = true, dynamicColor = false) {
        PracticeContent(uiState = PreviewStates.awaitingAnswer, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Correct feedback", showBackground = true)
@Composable
private fun PracticeContentCorrectPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        PracticeContent(uiState = PreviewStates.correctFeedback, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Incorrect feedback", showBackground = true)
@Composable
private fun PracticeContentIncorrectPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        PracticeContent(uiState = PreviewStates.incorrectFeedback, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Full diatonic set", showBackground = true)
@Composable
private fun PracticeContentFullSetPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        PracticeContent(uiState = PreviewStates.fullDiatonicSet, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun PracticeLoadingPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        LoadingState(isFinished = false)
    }
}

@Preview(name = "Session complete", showBackground = true)
@Composable
private fun PracticeCompletePreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        LoadingState(isFinished = true)
    }
}

@Preview(name = "Resume offer", showBackground = true)
@Composable
private fun PracticeResumeOfferPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        ResumeOfferState(onContinue = {}, onStartFresh = {})
    }
}

@Preview(name = "Paused after interruption", showBackground = true)
@Composable
private fun PracticePausedPreview() {
    TonicTheme(darkTheme = false, dynamicColor = false) {
        PausedState(onContinue = {})
    }
}
