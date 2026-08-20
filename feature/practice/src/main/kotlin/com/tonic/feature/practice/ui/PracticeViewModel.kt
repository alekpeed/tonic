package com.tonic.feature.practice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.engine.session.DueReview
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.time.Clock
import com.tonic.core.ui.components.PlaybackPhase
import com.tonic.feature.practice.engine.PracticeLoopEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * docs/04-ARCHITECTURE.md §3: "ViewModels orchestrate `:core:engine`/`:core:data`/`:core:audio`. They
 * contain no pedagogical logic." Every adaptive decision already happened inside [PracticeLoopEngine]
 * (Stage 6); this class's only two jobs are (1) resolving which node a session starts against, since
 * nothing upstream of Practice exists yet to hand that off (docs/09-BUILD-PLAN.md Stage 9's Home
 * screen, not yet built — see the note on [resolveSessionStart]), and (2) sequencing the UI-facing
 * timing docs/08-UI-SPEC.md §3 calls for: a visual flash and, on an incorrect answer, the audio
 * contrast sequence, both completing before the next item appears.
 */
@HiltViewModel
class PracticeViewModel
    @Inject
    constructor(
        private val engine: PracticeLoopEngine,
        private val audioPlayer: AudioPlayer,
        private val skillStateRepository: SkillStateRepository,
        private val sessionRepository: SessionRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(PracticeUiState())
        val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

        private val _hapticEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val hapticEvents: SharedFlow<Unit> = _hapticEvents.asSharedFlow()

        private var phaseJob: Job? = null
        private var skipNoticeJob: Job? = null
        private var timeTickerJob: Job? = null
        private var started = false

        /** The one worked example, generated once and reused for replays so the audio never changes under the narration. */
        private val workedExample by lazy { WorkedExample.generate() }

        /** What the worked example's correct answer is, for the reveal - read off the real item, never hardcoded. */
        val workedExampleAnswer: String get() = workedExample.targetDegree.degree.toString()

        /** Idempotent - a rotation or process restart re-collecting this ViewModel must not start a second session. */
        fun startIfNeeded() {
            if (started) return
            started = true
            viewModelScope.launch {
                // docs/11-ONBOARDING-CLARITY.md §1/§5: shown automatically exactly once, before the
                // first M2 item ever plays. The session underneath still starts, so dismissing the
                // screen lands straight on a ready item rather than a spinner.
                if (!settingsRepository.settings.first().module2IntroSeen) {
                    _uiState.update { it.copy(showIntro = true) }
                }
                // docs/10-TESTING.md §11: "force stop mid-session -> resume offered, no data loss." An
                // interrupted session is offered back rather than silently replaced with a fresh one -
                // starting fresh would strand its resume row and re-plan work the user already did.
                val resumable = sessionRepository.findResumable()
                if (resumable?.resumeState != null) {
                    _uiState.update { it.copy(isLoading = false, resumableSession = resumable) }
                    return@launch
                }
                beginFreshSession()
            }
        }

        /** The user accepted the resume offer — continue the interrupted session from its stored plan. */
        fun onResumeSession() {
            val session = _uiState.value.resumableSession ?: return
            _uiState.update { it.copy(resumableSession = null, isLoading = true) }
            viewModelScope.launch {
                engine.resume(session)
                observeEngineAndSettings()
            }
        }

        /**
         * The user declined the resume offer. The interrupted session is closed out first — otherwise its
         * `resumeStateJson` stays non-null and `findResumable()` would keep offering it at every launch.
         * Its recorded attempts are untouched; only the offer goes away.
         */
        fun onStartFreshSession() {
            val session = _uiState.value.resumableSession ?: return
            _uiState.update { it.copy(resumableSession = null, isLoading = true) }
            viewModelScope.launch {
                session.id?.let { sessionRepository.complete(it, session.completedItemCount, clock.now()) }
                beginFreshSession()
            }
        }

        /**
         * Plays the worked example through the *real* audio path — same renderer, same player as a
         * practice item (docs/11-ONBOARDING-CLARITY.md §3: "in real audio"). Repeatable: hearing it more
         * than once is the point.
         */
        fun onPlayWorkedExample() {
            viewModelScope.launch {
                audioPlayer.play(
                    SynthEngine.renderItem(
                        referencePlan = workedExample.referencePlan,
                        gapAfterReferenceMs = workedExample.timing.gapAfterReferenceMs,
                        targetMidi = workedExample.targetMidi,
                        targetTimbre = workedExample.timbre,
                        targetDurationMs = workedExample.timing.targetDurationMs,
                        seed = workedExample.seed,
                    ),
                )
            }
        }

        /** The reveal is user-driven: the answer is never shown before they've had the chance to listen. */
        fun onRevealWorkedExampleAnswer() {
            _uiState.update { it.copy(introAnswerRevealed = true) }
        }

        /**
         * Dismisses the explanation and records that it has been shown, so it never appears
         * automatically again. Recalling it later via [onOpenIntro] deliberately does not touch the flag.
         */
        fun onIntroDismissed() {
            _uiState.update { it.copy(showIntro = false, introAnswerRevealed = false) }
            viewModelScope.launch { settingsRepository.setModule2IntroSeen(true) }
        }

        /**
         * The help affordance - docs/11-ONBOARDING-CLARITY.md §5: "always reachable on demand... the
         * exact same explanation and worked example, not an abbreviated version."
         */
        fun onOpenIntro() {
            audioPlayer.stop()
            _uiState.update { it.copy(showIntro = true, introAnswerRevealed = false) }
        }

        /**
         * The user is leaving Practice for Home - docs/08-UI-SPEC.md §2a. The engine persists the
         * session for resume *before* [onExited] runs, because navigating away tears this ViewModel (and
         * its scope) down: firing the navigation first would race the write that makes the session
         * resumable. Next launch, [startIfNeeded]'s findResumable() offers this session back.
         */
        fun onExitSession(onExited: () -> Unit) {
            phaseJob?.cancel()
            viewModelScope.launch {
                engine.leaveSession()
                onExited()
            }
        }

        /** Continues after an interruption paused the loop (docs/06-AUDIO-ENGINE.md §8). */
        fun onResumeFromPause() {
            viewModelScope.launch { engine.resumeAfterPause() }
        }

        /**
         * The app was genuinely backgrounded — not a configuration change; the screen filters those out
         * before calling this. Delegates to the engine's own non-suspending entry point rather than
         * launching here, because `viewModelScope` is itself about to be torn down.
         */
        fun onAppBackgrounded() {
            if (!started) return
            engine.onBackgrounded()
        }

        private suspend fun beginFreshSession() {
            val settings = settingsRepository.settings.first()
            val (currentNode, dueReviews) = resolveSessionStart()
            engine.start(
                currentNode = currentNode,
                dueReviews = dueReviews,
                sessionLengthMinutes = settings.sessionLengthMinutes,
                rootSeed = Random.nextLong(),
                now = clock.now(),
            )
            observeEngineAndSettings()
        }

        /**
         * Drives [PracticeUiState.timeFraction] once a second from the engine's session window. Wall
         * clock on purpose: the budget is wall-clock (docs/07-ADAPTIVE-ENGINE.md §8), so the bar keeps
         * moving through pauses and thinking time alike - "timed from the time I start the practice."
         */
        private fun startTimeTicker() {
            timeTickerJob?.cancel()
            timeTickerJob =
                viewModelScope.launch {
                    while (true) {
                        val loopState = engine.state.value
                        val startedAt = loopState.sessionStartedAt
                        val endsAt = loopState.sessionEndsAt
                        if (startedAt != null && endsAt != null && !loopState.isFinished) {
                            val total =
                                java.time.Duration
                                    .between(startedAt, endsAt)
                                    .toMillis()
                                    .coerceAtLeast(1)
                            val elapsed =
                                java.time.Duration
                                    .between(startedAt, clock.now())
                                    .toMillis()
                            _uiState.update {
                                it.copy(timeFraction = (elapsed.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                        if (loopState.isFinished) {
                            _uiState.update { it.copy(timeFraction = 1f) }
                            return@launch
                        }
                        delay(TIME_TICK_MS)
                    }
                }
        }

        private fun observeEngineAndSettings() {
            startTimeTicker()
            viewModelScope.launch {
                settingsRepository.settings.collect { settings ->
                    _uiState.update {
                        it.copy(
                            labelStyle = settings.labelStyle,
                            reduceMotion = settings.reduceMotion,
                            hapticsEnabled = settings.hapticsEnabled,
                        )
                    }
                }
            }
            viewModelScope.launch {
                engine.state.collect { loopState ->
                    val itemChanged = _uiState.value.item?.seed != loopState.currentItem?.seed
                    _uiState.update {
                        it.copy(
                            item = loopState.currentItem,
                            itemsCompleted = loopState.itemsCompleted,
                            itemsPlanned = loopState.itemsPlanned,
                            isFinished = loopState.isFinished,
                            sessionId = loopState.sessionId,
                            isPaused = loopState.isPaused,
                            axisChange = loopState.axisChange,
                            isLoading = false,
                            selectedDegree = if (itemChanged) null else it.selectedDegree,
                            correctDegree = if (itemChanged) null else it.correctDegree,
                            inputEnabled = if (itemChanged) false else it.inputEnabled,
                        )
                    }
                    if (itemChanged) {
                        loopState.currentItem?.let(::runPlaybackPhaseTimer)
                    }
                }
            }
        }

        /** docs/08-UI-SPEC.md §4's reference -> gap -> target -> "your turn" sequence, timed from [Item.FunctionalRecognitionItem.timing] since the engine only reports *what* is playing, not when each phase ends. */
        private fun runPlaybackPhaseTimer(item: Item.FunctionalRecognitionItem) {
            phaseJob?.cancel()
            phaseJob =
                viewModelScope.launch {
                    // Timed from the plan's *actual* sequential audio, not from one referenceDurationMs:
                    // that constant is per-chord, so a four-chord cadence used to flip to "here comes the
                    // question" while three chords were still playing - and a plan with no elements at
                    // all (an L1 group item, an L6/L7 block item) showed "setting up home" over silence.
                    val referenceMs = item.referencePlan.sequentialDurationMs
                    if (referenceMs > 0) {
                        _uiState.update { it.copy(phase = PlaybackPhase.REFERENCE, inputEnabled = false) }
                        delay(referenceMs + item.timing.gapAfterReferenceMs)
                        _uiState.update { it.copy(phase = PlaybackPhase.TARGET) }
                        delay(item.timing.targetDurationMs)
                    } else {
                        // No reference plays: the item is gap + target, so it opens on the question.
                        _uiState.update { it.copy(phase = PlaybackPhase.TARGET, inputEnabled = false) }
                        delay(item.timing.gapAfterReferenceMs + item.timing.targetDurationMs)
                    }
                    _uiState.update { it.copy(phase = PlaybackPhase.AWAITING_ANSWER, inputEnabled = true) }
                }
        }

        fun onDegreeSelected(degree: ScaleDegree) {
            if (!_uiState.value.inputEnabled) return
            val item = _uiState.value.item ?: return
            _uiState.update { it.copy(selectedDegree = degree, inputEnabled = false) }

            viewModelScope.launch {
                engine.submitAnswer(degree.degree.toString(), autoAdvance = false)
                val feedback = engine.state.value.lastFeedback ?: return@launch
                val correctDegree = item.activeDegrees.first { it.degree.toString() == feedback.correctLabel }
                _uiState.update { it.copy(correctDegree = correctDegree) }
                if (_uiState.value.hapticsEnabled) _hapticEvents.tryEmit(Unit)

                if (feedback.correct) {
                    delay(CORRECT_FEEDBACK_MS)
                } else {
                    // docs/02-PEDAGOGY.md §6: replay target-in-context, the chosen note, target again -
                    // "do not advance until it completes" (docs/08-UI-SPEC.md §3).
                    delay(INCORRECT_FLASH_SETTLE_MS)
                    engine.playIncorrectContrast(degree.degree.toString())
                }
                delay(INTER_ITEM_PAUSE_MS)
                engine.proceedToNextItem()
            }
        }

        fun onReplay() {
            viewModelScope.launch { engine.replay() }
        }

        fun onSkip() {
            phaseJob?.cancel()
            skipNoticeJob?.cancel()
            skipNoticeJob =
                viewModelScope.launch {
                    // Shown immediately - before the engine even responds - and held long enough to read
                    // across the item change, so the press visibly did something even when the next item
                    // looks identical (docs/08-UI-SPEC.md §2a).
                    _uiState.update { it.copy(skipAcknowledged = true) }
                    delay(SKIP_NOTICE_MS)
                    _uiState.update { it.copy(skipAcknowledged = false) }
                }
            viewModelScope.launch { engine.abandonCurrentItem() }
        }

        override fun onCleared() {
            phaseJob?.cancel()
            engine.close()
        }

        /**
         * Which node to practice and which mastered nodes are due for review — normally the Home
         * screen's job (docs/08-UI-SPEC.md §2: "Start session, current progress at a glance"), but Home
         * is Stage 9 and not yet built. Scoped here to the minimum this screen needs to be functional on
         * its own: the first M2 node (in [SkillGraph.m2Nodes] order) that isn't yet [MasteryState.MASTERED]
         * — deliberately not gated on LOCKED vs. AVAILABLE, since the M0 diagnostic placement flow that
         * performs the real unlock is Stage 8, also not yet built, and a fresh install would otherwise
         * have nothing practiceable at all. Once Stage 8 lands and writes real AVAILABLE states via
         * placement, this still resolves correctly without changes - a LOCKED node past `M2_DEG_SET_1`
         * simply won't exist for a real user by the time they reach here.
         */
        private suspend fun resolveSessionStart(): Pair<SkillWorkContext, List<DueReview>> {
            val states = skillStateRepository.observeAll().first()
            val currentNodeId =
                SkillGraph.m2Nodes.firstOrNull { states[it.id]?.masteryState != MasteryState.MASTERED }?.id
                    ?: SkillGraph.m2Nodes.last().id
            val currentState = states[currentNodeId]
            val currentContext =
                SkillWorkContext(
                    skillId = currentNodeId,
                    axisLevels = currentState?.axisLevels ?: emptyMap(),
                    totalAttempts = currentState?.totalAttempts ?: 0,
                )

            val now = clock.now()
            val dueReviews =
                skillStateRepository
                    .dueForReview(now)
                    .filter { it != currentNodeId }
                    .mapNotNull { id ->
                        val state = states[id] ?: return@mapNotNull null
                        DueReview(skillId = id, dueAt = state.fsrs.due ?: now, axisLevels = state.axisLevels)
                    }

            return currentContext to dueReviews
        }

        private companion object {
            const val CORRECT_FEEDBACK_MS = 300L
            const val INCORRECT_FLASH_SETTLE_MS = 250L

            // "Between items: a short, consistent pause (~400 ms). Do not vary it randomly" - docs/08-UI-SPEC.md §4.
            const val INTER_ITEM_PAUSE_MS = 400L

            /** Long enough to read across the item change; short enough to be gone before the next answer. */
            const val SKIP_NOTICE_MS = 1_600L

            /** One second: the finest granularity a thin, unlabeled time bar can meaningfully show. */
            const val TIME_TICK_MS = 1_000L
        }
    }
