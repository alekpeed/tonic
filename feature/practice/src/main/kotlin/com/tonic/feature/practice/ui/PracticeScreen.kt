package com.tonic.feature.practice.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.tonic.core.model.items.AxisChange
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.ui.components.MinimalProgressIndicator
import com.tonic.core.ui.components.PlaybackPhase
import com.tonic.core.ui.components.PlaybackPhaseIndicator
import com.tonic.core.ui.components.StageHeader
import com.tonic.core.ui.ladder.DegreeLadder
import com.tonic.core.ui.theme.TonicSpacing
import com.tonic.core.ui.theme.TonicTheme
import com.tonic.feature.practice.R

/** docs/08-UI-SPEC.md §4: progress indicator, playback state, replay, ladder, skip - top to bottom. */
@Composable
fun PracticeScreen(
    onSessionComplete: (Long) -> Unit,
    onExitToHome: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // docs/08-UI-SPEC.md §2a. The system back gesture is a "way out" too, and it must go through the
    // same save-then-leave path as the visible control: letting it pop the screen directly would tear
    // the ViewModel down before the resume state is written, silently losing the session.
    BackHandler { viewModel.onExitSession(onExitToHome) }

    LaunchedEffect(Unit) { viewModel.startIfNeeded() }
    LaunchedEffect(Unit) {
        viewModel.hapticEvents.collect { haptic.performHapticFeedback(HapticFeedbackType.VirtualKey) }
    }
    LaunchedEffect(uiState.isFinished, uiState.sessionId) {
        val sessionId = uiState.sessionId
        if (uiState.isFinished && sessionId != null) onSessionComplete(sessionId)
    }

    BackgroundingObserver(onBackgrounded = viewModel::onAppBackgrounded)

    // Above every other state: docs/11-ONBOARDING-CLARITY.md §1 requires this before the first item
    // plays, and §5 makes it recallable at any point afterwards.
    if (uiState.showIntro) {
        M2IntroContent(
            answerLabel = viewModel.workedExampleAnswer,
            answerRevealed = uiState.introAnswerRevealed,
            onPlayExample = viewModel::onPlayWorkedExample,
            onRevealAnswer = viewModel::onRevealWorkedExampleAnswer,
            onStart = viewModel::onIntroDismissed,
        )
        return
    }

    val resumable = uiState.resumableSession
    if (resumable != null) {
        ResumeOfferState(onContinue = viewModel::onResumeSession, onStartFresh = viewModel::onStartFreshSession)
        return
    }

    if (uiState.isPaused) {
        PausedState(
            onContinue = viewModel::onResumeFromPause,
            onExit = { viewModel.onExitSession(onExitToHome) },
        )
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
        onOpenIntro = viewModel::onOpenIntro,
        onExit = { viewModel.onExitSession(onExitToHome) },
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
private fun PausedState(
    onContinue: () -> Unit,
    onExit: () -> Unit = {},
) {
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
        Spacer(modifier = Modifier.height(TonicSpacing.sm))
        TextButton(onClick = onExit, modifier = Modifier.testTag("practice_paused_exit")) {
            Text(stringResource(R.string.practice_paused_exit))
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

/**
 * The one-line announcement for an axis that just moved — docs/11-ONBOARDING-CLARITY.md §9.3. Every
 * axis in docs/03-CURRICULUM.md §5.3 is covered in both directions, because "a level change is a level
 * change regardless of what triggered it": this same mapping serves a staircase step and the scheduled
 * warmup-to-normal transition at item 6 without distinguishing them, since the user cannot tell them
 * apart and does not need to.
 */
private fun axisChangeRes(change: AxisChange): Int =
    when (change.axis) {
        DifficultyAxis.CADENCE_FADE ->
            if (change.isIncrease) R.string.practice_axis_cadence_harder else R.string.practice_axis_cadence_easier
        DifficultyAxis.TIMBRE_VARIETY ->
            if (change.isIncrease) R.string.practice_axis_timbre_harder else R.string.practice_axis_timbre_easier
        DifficultyAxis.REGISTER_SPREAD ->
            if (change.isIncrease) R.string.practice_axis_register_harder else R.string.practice_axis_register_easier
        DifficultyAxis.OCTAVE_DISPLACE ->
            if (change.isIncrease) R.string.practice_axis_octave_harder else R.string.practice_axis_octave_easier
        DifficultyAxis.TEMPO_DENSITY ->
            if (change.isIncrease) R.string.practice_axis_tempo_harder else R.string.practice_axis_tempo_easier
        DifficultyAxis.KEY_SPREAD ->
            if (change.isIncrease) R.string.practice_axis_key_harder else R.string.practice_axis_key_easier
    }

/** In words, every time - the phase indicator itself is deliberately non-verbal (docs/08-UI-SPEC.md §4). */
private fun phaseCaptionRes(phase: PlaybackPhase): Int =
    when (phase) {
        PlaybackPhase.REFERENCE -> R.string.practice_phase_reference
        PlaybackPhase.TARGET -> R.string.practice_phase_target
        PlaybackPhase.AWAITING_ANSWER -> R.string.practice_phase_awaiting_answer
    }

@Composable
private fun PracticeContent(
    uiState: PracticeUiState,
    onDegreeSelected: (ScaleDegree) -> Unit,
    onReplay: () -> Unit,
    onSkip: () -> Unit,
    onOpenIntro: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(TonicSpacing.md),
    ) {
        // docs/08-UI-SPEC.md §2a: a clear, always-visible way out, reachable mid-item. Sharing the row
        // with the stage header keeps it present on every frame of the session without costing a line.
        Box(modifier = Modifier.fillMaxWidth()) {
            StageHeader(
                stringResource(R.string.practice_stage_name),
                modifier = Modifier.align(Alignment.Center),
            )
            TextButton(
                onClick = onExit,
                modifier = Modifier.align(Alignment.CenterStart).testTag("practice_exit"),
            ) {
                Text(text = stringResource(R.string.practice_exit), style = MaterialTheme.typography.labelLarge)
            }
        }

        // Non-blocking and self-clearing: it occupies its own line only on the item where the change
        // landed, and the next item's null axisChange removes it. Nothing to dismiss, nothing gated
        // behind it - docs/11-ONBOARDING-CLARITY.md §9.3 asks for a brief statement, not an interstitial.
        uiState.axisChange?.let { change ->
            Text(
                text = stringResource(axisChangeRes(change)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.sm).testTag("axis_change"),
            )
        }

        MinimalProgressIndicator(
            itemsCompleted = uiState.itemsCompleted,
            itemsPlanned = uiState.itemsPlanned,
        )

        Spacer(modifier = Modifier.height(TonicSpacing.lg))

        PlaybackPhaseIndicator(
            phase = uiState.phase,
            reduceMotion = uiState.reduceMotion,
            modifier = Modifier.padding(top = TonicSpacing.lg),
        )
        Text(
            text = stringResource(phaseCaptionRes(uiState.phase)),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.lg),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // docs/11-ONBOARDING-CLARITY.md §5: "a small, low-emphasis help affordance on the practice
            // screen itself." Same screen, same worked example, never an abbreviated version.
            TextButton(onClick = onOpenIntro, modifier = Modifier.testTag("practice_help")) {
                Text(
                    text = stringResource(R.string.m2_intro_help),
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("skip_button"),
            ) {
                Text(
                    text = stringResource(R.string.practice_skip),
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview(name = "Awaiting answer - light", showBackground = true)
@Composable
private fun PracticeContentIdlePreview() {
    TonicTheme(darkTheme = false) {
        PracticeContent(uiState = PreviewStates.awaitingAnswer, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Awaiting answer - dark", showBackground = true, uiMode = 0x20)
@Composable
private fun PracticeContentIdleDarkPreview() {
    TonicTheme(darkTheme = true) {
        PracticeContent(uiState = PreviewStates.awaitingAnswer, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Correct feedback", showBackground = true)
@Composable
private fun PracticeContentCorrectPreview() {
    TonicTheme(darkTheme = false) {
        PracticeContent(uiState = PreviewStates.correctFeedback, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Incorrect feedback", showBackground = true)
@Composable
private fun PracticeContentIncorrectPreview() {
    TonicTheme(darkTheme = false) {
        PracticeContent(uiState = PreviewStates.incorrectFeedback, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Full diatonic set", showBackground = true)
@Composable
private fun PracticeContentFullSetPreview() {
    TonicTheme(darkTheme = false) {
        PracticeContent(uiState = PreviewStates.fullDiatonicSet, onDegreeSelected = {}, onReplay = {}, onSkip = {})
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun PracticeLoadingPreview() {
    TonicTheme(darkTheme = false) {
        LoadingState(isFinished = false)
    }
}

@Preview(name = "Session complete", showBackground = true)
@Composable
private fun PracticeCompletePreview() {
    TonicTheme(darkTheme = false) {
        LoadingState(isFinished = true)
    }
}

@Preview(name = "Resume offer", showBackground = true)
@Composable
private fun PracticeResumeOfferPreview() {
    TonicTheme(darkTheme = false) {
        ResumeOfferState(onContinue = {}, onStartFresh = {})
    }
}

@Preview(name = "Paused after interruption", showBackground = true)
@Composable
private fun PracticePausedPreview() {
    TonicTheme(darkTheme = false) {
        PausedState(onContinue = {})
    }
}
