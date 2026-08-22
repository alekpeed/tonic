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
import com.tonic.core.model.items.CadenceFadeLevel
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Mode
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.ui.components.MinimalProgressIndicator
import com.tonic.core.ui.components.PlaybackPhase
import com.tonic.core.ui.components.PlaybackPhaseIndicator
import com.tonic.core.ui.components.StageHeader
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
    //
    // Which one is dispatched on the kind the ViewModel resolved from the node. This screen previously
    // rendered M2IntroContent unconditionally, so M10IntroContent - built, tested and correctly
    // selected by introKindFor - was unreachable, and a learner arriving at minor was shown the major
    // explanation. The bug was in the one line that connects them, which is exactly where a test that
    // exercises a composable directly cannot see it.
    if (uiState.showIntro) {
        IntroForKind(
            kind = uiState.introKind,
            answerLabel = viewModel.workedExampleAnswer,
            answerRevealed = uiState.introAnswerRevealed,
            onPlayExample = viewModel::onPlayWorkedExample,
            onRevealAnswer = viewModel::onRevealWorkedExampleAnswer,
            onStart = viewModel::onIntroDismissed,
            predictionExample =
                PredictionExampleCopy(
                    statedLabel = viewModel.predictionExampleLabel,
                    matched = viewModel.predictionExampleMatched,
                    soundedWasLower = viewModel.predictionExampleWasLower,
                ),
            mixedModeExampleAnswer = viewModel.mixedModeExampleAnswer,
            onPlayM9Major = viewModel::onPlayM9MajorExample,
            onPlayM9Minor = viewModel::onPlayM9MinorExample,
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
        onLabelSelected = viewModel::onLabelSelected,
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
 * What THIS item's setup sounds like, when it is anything other than the full chord sequence the
 * intro taught — or null for the default shape, which needs no line. Derived from the item itself, so
 * it is inherently per-item: a bare item inside an L1 group or an L6/L7 audiation block gets its
 * "no setup this time" line on every such item, not only on the once-per-transition announcement
 * (docs/11-ONBOARDING-CLARITY.md §9.3 - a user mid-block must never face a bare note with no on-screen
 * account of why nothing played).
 */
internal fun referenceNoteRes(item: Item.FunctionalRecognitionItem): Int? {
    val plan = item.referencePlan
    if (plan.elements.isEmpty()) return R.string.practice_ref_none
    return when (plan.cadenceFadeLevel) {
        CadenceFadeLevel.L0, CadenceFadeLevel.L1 -> null
        CadenceFadeLevel.L2 -> R.string.practice_ref_two_chords
        CadenceFadeLevel.L3 -> R.string.practice_ref_one_chord
        CadenceFadeLevel.L4 -> R.string.practice_ref_drone
        CadenceFadeLevel.L5 -> R.string.practice_ref_flash
        // Non-empty elements at L6/L7 = the block-start key establishment.
        CadenceFadeLevel.L6, CadenceFadeLevel.L7 -> R.string.practice_ref_block_start
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
        // Prediction axes never reach this screen: M12 items have their own screen and their own
        // announcements (docs/20-PHASE-2-SPEC.md §5.3), and the scheduler cannot offer a prediction axis
        // to a recognition node (DifficultyAxis.Scope). Left as an explicit branch rather than an `else`
        // so that adding a real recognition axis still fails to compile here until it has copy - which
        // is docs/11-ONBOARDING-CLARITY.md §9.3's "no silent difficulty changes, ever" enforced by the
        // type system rather than by remembering.
        DifficultyAxis.PREDICT_GAP, DifficultyAxis.PREDICT_DEVIATION ->
            error("${change.axis} is a prediction axis and cannot move on the recognition practice screen")
    }

/** In words, every time - the phase indicator itself is deliberately non-verbal (docs/08-UI-SPEC.md §4). */
private fun revealedModeRes(mode: Mode): Int =
    when (mode) {
        Mode.MAJOR -> R.string.practice_that_was_major
        Mode.MINOR -> R.string.practice_that_was_minor
    }

private fun phaseCaptionRes(phase: PlaybackPhase): Int =
    when (phase) {
        PlaybackPhase.REFERENCE -> R.string.practice_phase_reference
        PlaybackPhase.TARGET -> R.string.practice_phase_target
        PlaybackPhase.AWAITING_ANSWER -> R.string.practice_phase_awaiting_answer
        PlaybackPhase.AUDIATION_GAP -> R.string.practice_phase_audiation_gap
    }

// `internal`, not private: docs/09-BUILD-PLAN.md Stage 7's "ladder fits ... on a 5-inch screen" is a
// claim about this composable's layout, and it went unverified through all of Phase 1 because nothing
// could reach it. PracticeScreenLayoutTest measures it directly. Still module-private - no widening of
// the feature's public API (docs/04-ARCHITECTURE.md §4).

/** What [M12IntroContent]'s reveal has to say, read off the real worked example rather than hardcoded. */
internal data class PredictionExampleCopy(
    val statedLabel: String = "",
    val matched: Boolean = false,
    val soundedWasLower: Boolean = false,
)

/**
 * The one place that maps a resolved [IntroKind] to the screen that explains it.
 *
 * Extracted from [PracticeScreen] because it is where a real bug lived and could not be seen: the
 * screen rendered [M2IntroContent] unconditionally, so [M10IntroContent] — built, previewed, and
 * correctly selected by the ViewModel — was unreachable, and a learner arriving at minor was handed
 * the major explanation. Every test in the repository exercised either the composables or the
 * ViewModel, and the defect was in the line between them. It is a separate composable now so that
 * line has something to test.
 */
@Composable
internal fun IntroForKind(
    kind: IntroKind,
    answerLabel: String,
    answerRevealed: Boolean,
    onPlayExample: () -> Unit,
    onRevealAnswer: () -> Unit,
    onStart: () -> Unit,
    predictionExample: PredictionExampleCopy = PredictionExampleCopy(),
    mixedModeExampleAnswer: String = "",
    onPlayM9Major: () -> Unit = {},
    onPlayM9Minor: () -> Unit = {},
) {
    when (kind) {
        IntroKind.M9 ->
            M9IntroContent(
                onPlayMajor = onPlayM9Major,
                onPlayMinor = onPlayM9Minor,
                onStart = onStart,
            )
        IntroKind.M10 -> M10IntroContent(onPlayExample = onPlayExample, onStart = onStart)
        IntroKind.M11 -> M11IntroContent(onStart = onStart)
        IntroKind.SUNG -> SungResponseIntroContent(onStart = onStart)
        IntroKind.MIXED_MODE ->
            MixedModeIntroContent(
                answerLabel = mixedModeExampleAnswer,
                answerRevealed = answerRevealed,
                onPlayExample = onPlayExample,
                onRevealAnswer = onRevealAnswer,
                onStart = onStart,
            )
        IntroKind.M12 ->
            M12IntroContent(
                statedLabel = predictionExample.statedLabel,
                matched = predictionExample.matched,
                soundedWasLower = predictionExample.soundedWasLower,
                answerRevealed = answerRevealed,
                onPlayExample = onPlayExample,
                onRevealAnswer = onRevealAnswer,
                onStart = onStart,
            )
        // NONE reaches here only through onOpenIntro's recall on a node with no explanation of its
        // own, where the major screen is the right thing to show: it is the one that explains the task
        // shape every recognition node shares.
        IntroKind.M2, IntroKind.NONE ->
            M2IntroContent(
                answerLabel = answerLabel,
                answerRevealed = answerRevealed,
                onPlayExample = onPlayExample,
                onRevealAnswer = onRevealAnswer,
                onStart = onStart,
            )
    }
}

@Composable
internal fun PracticeContent(
    uiState: PracticeUiState,
    onDegreeSelected: (ScaleDegree) -> Unit,
    onReplay: () -> Unit,
    onSkip: () -> Unit,
    /** A non-degree answer — `M9`'s major/minor, `M12`'s matched/too-low/too-high. */
    onLabelSelected: (String) -> Unit = {},
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
        if (uiState.skipAcknowledged) {
            Text(
                text = stringResource(R.string.practice_skipped),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.sm).testTag("skip_acknowledged"),
            )
        }

        uiState.axisChange?.let { change ->
            Text(
                text = stringResource(axisChangeRes(change)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.sm).testTag("axis_change"),
            )
        }

        // Time, not items - the session is bounded by minutes (docs/07-ADAPTIVE-ENGINE.md §8), so the
        // bar fills steadily from session start and reaches full exactly when the budget does.
        MinimalProgressIndicator(
            fraction = uiState.timeFraction,
            contentDescription = "session time",
        )

        uiState.recognitionItem?.let { item ->
            referenceNoteRes(item)?.let { res ->
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = TonicSpacing.sm).testTag("reference_note"),
                )
            }
        }

        // One break here, not two. A Spacer(lg) immediately followed by the indicator's own lg top
        // padding stacked 48dp of empty space for a single visual separation - dead height the ladder
        // needed (see DegreeLadder's scroll note).
        PlaybackPhaseIndicator(
            phase = uiState.phase,
            reduceMotion = uiState.reduceMotion,
            modifier = Modifier.padding(top = TonicSpacing.lg),
        )
        Text(
            text = stringResource(phaseCaptionRes(uiState.phase)),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = TonicSpacing.sm),
        )

        TextButton(
            onClick = onReplay,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("replay_button"),
        ) {
            Text(stringResource(R.string.practice_replay))
        }

        Spacer(modifier = Modifier.height(TonicSpacing.sm))

        // docs/20-PHASE-2-SPEC.md §5.4, and it sits *above* the ladder rather than below it on purpose:
        // it appears only after an answer, on a screen whose bottom half is already showing which
        // button was right. Put underneath, it would compete with that; put here, it reads as the
        // caption to the feedback the learner is looking at.
        uiState.revealedMode?.let { mode ->
            Text(
                text = stringResource(revealedModeRes(mode)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = TonicSpacing.sm)
                        .testTag("revealed_mode"),
            )
        }

        AnswerArea(
            uiState = uiState,
            onDegreeSelected = onDegreeSelected,
            onLabelSelected = onLabelSelected,
            modifier = Modifier.weight(1f),
        )

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
