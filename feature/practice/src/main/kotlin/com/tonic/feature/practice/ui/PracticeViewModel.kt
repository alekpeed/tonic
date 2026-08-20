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
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.time.Clock
import com.tonic.core.ui.components.PlaybackPhase
import com.tonic.core.ui.labels.displayLabel
import com.tonic.feature.practice.engine.PracticeItems
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

        /** The minor counterpart, for [IntroKind.M10]'s screen. */
        private val minorWorkedExample by lazy { WorkedExample.generateMinor() }

        /** The audiation counterpart, for [IntroKind.M12]'s screen. */
        private val predictionWorkedExample by lazy { M12WorkedExample.generate() }

        /** The mixed-mode counterpart — deliberately a minor item, see [MixedModeWorkedExample]. */
        private val mixedModeWorkedExample by lazy { MixedModeWorkedExample.generate() }

        /** The mixed-mode example's answer, read off the real item and rendered in the learner's own style. */
        val mixedModeExampleAnswer: String
            get() = mixedModeWorkedExample.targetDegree.displayLabel(_uiState.value.labelStyle)

        /** What the `M12` example names on screen, read off the real item rather than hardcoded. */
        val predictionExampleLabel: String get() =
            predictionWorkedExample.statedDegree.displayLabel(
                _uiState.value.labelStyle,
            )

        /** Whether the `M12` example's sounded note was the one named, and if not, which way it went. */
        val predictionExampleMatched: Boolean get() = predictionWorkedExample.matches

        val predictionExampleWasLower: Boolean
            get() = predictionWorkedExample.correctLabel == AnswerAlphabet.MatchDirection.TOO_LOW

        /** What the worked example's correct answer is, for the reveal - read off the real item, never hardcoded. */
        val workedExampleAnswer: String get() = workedExample.targetDegree.canonicalLabel

        /** Idempotent - a rotation or process restart re-collecting this ViewModel must not start a second session. */
        fun startIfNeeded() {
            if (started) return
            started = true
            viewModelScope.launch {
                // docs/11-ONBOARDING-CLARITY.md §1/§5 and docs/08-UI-SPEC.md §3a: shown automatically
                // exactly once per task shape, before that shape's first item ever plays. Which one is
                // decided by the node the session is about to resolve to, not by module 2 alone -
                // otherwise a learner reaching minor would be handed the major explanation, or none.
                // The session underneath still starts, so dismissing lands on a ready item.
                val settings = settingsRepository.settings.first()
                val kind = introKindFor(resolveSessionStart().first.skillId, settings)
                if (kind != IntroKind.NONE) {
                    _uiState.update { it.copy(showIntro = true, introKind = kind) }
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
            // Whichever shape is being explained - playing the major example on the minor screen would
            // demonstrate the wrong thing at exactly the moment the learner is forming their idea of it.
            // M12's example is a prediction item, which renders through a different path entirely -
            // its silent gap is *inside* the buffer. Routing it through the same PracticeItems
            // renderer the live loop uses means the example is audibly the exercise, gap and all,
            // rather than a reconstruction of it that could drift.
            if (_uiState.value.introKind == IntroKind.M12) {
                viewModelScope.launch { audioPlayer.play(PracticeItems.renderAudio(predictionWorkedExample)) }
                return
            }
            val example =
                when (_uiState.value.introKind) {
                    IntroKind.M10 -> minorWorkedExample
                    IntroKind.MIXED_MODE -> mixedModeWorkedExample
                    else -> workedExample
                }
            viewModelScope.launch {
                audioPlayer.play(
                    SynthEngine.renderItem(
                        referencePlan = example.referencePlan,
                        gapAfterReferenceMs = example.timing.gapAfterReferenceMs,
                        targetMidi = example.targetMidi,
                        targetTimbre = example.timbre,
                        targetDurationMs = example.timing.targetDurationMs,
                        seed = example.seed,
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
            val kind = _uiState.value.introKind
            _uiState.update { it.copy(showIntro = false, introAnswerRevealed = false) }
            viewModelScope.launch {
                // Only the shape that was actually shown is marked seen. Marking both would silently
                // rob the learner of an explanation they never received.
                when (kind) {
                    IntroKind.M2 -> settingsRepository.setModule2IntroSeen(true)
                    IntroKind.M10 -> settingsRepository.setModule10IntroSeen(true)
                    IntroKind.M11 -> settingsRepository.setModule11IntroSeen(true)
                    IntroKind.M12 -> settingsRepository.setModule12IntroSeen(true)
                    IntroKind.MIXED_MODE -> settingsRepository.setMixedModeIntroSeen(true)
                    IntroKind.NONE -> Unit
                }
            }
        }

        /**
         * Which explanation a node needs, or none. Recalling one on demand ([onOpenIntro]) deliberately
         * does not consult this — docs/11-ONBOARDING-CLARITY.md §5: recall neither depends on nor
         * changes the seen-once flag.
         */
        private fun introKindFor(
            skillId: com.tonic.core.model.ids.SkillId,
            settings: com.tonic.core.model.state.AppSettings,
        ): IntroKind =
            when {
                skillId == SkillIds.M10_MIXED_MODE ->
                    if (settings.mixedModeIntroSeen) IntroKind.NONE else IntroKind.MIXED_MODE
                skillId in SkillGraph.m12Nodes.map { it.id } ->
                    if (settings.module12IntroSeen) IntroKind.NONE else IntroKind.M12
                skillId in SkillGraph.m11Nodes.map { it.id } ->
                    if (settings.module11IntroSeen) IntroKind.NONE else IntroKind.M11
                skillId in SkillGraph.m10Nodes.map { it.id } ->
                    if (settings.module10IntroSeen) IntroKind.NONE else IntroKind.M10
                settings.module2IntroSeen -> IntroKind.NONE
                else -> IntroKind.M2
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
                            selectedAnswerLabel = if (itemChanged) null else it.selectedAnswerLabel,
                            correctAnswerLabel = if (itemChanged) null else it.correctAnswerLabel,
                            revealedMode = if (itemChanged) null else it.revealedMode,
                            inputEnabled = if (itemChanged) false else it.inputEnabled,
                        )
                    }
                    if (itemChanged) {
                        loopState.currentItem?.let(::runPlaybackPhaseTimer)
                    }
                }
            }
        }

        /**
         * docs/08-UI-SPEC.md §4's reference -> gap -> target -> "your turn" sequence, timed from the
         * item's own timing since the engine reports *what* is playing, not when each phase ends.
         *
         * An `M9` item has no target note: the passage being judged *is* the whole item, so it runs one
         * phase and then opens for an answer. Modeling that as a zero-length target phase would flash
         * "one note, alone" over silence, which is precisely the kind of caption-versus-audio mismatch
         * that got reported in live use.
         */
        private fun runPlaybackPhaseTimer(item: Item) {
            when (item) {
                is Item.FunctionalRecognitionItem -> runRecognitionPhaseTimer(item)
                is Item.ModeIdentificationItem -> runModePhaseTimer(item)
                is Item.PredictionItem -> runPredictionPhaseTimer(item)
                else -> Unit
            }
        }

        private fun runModePhaseTimer(item: Item.ModeIdentificationItem) {
            phaseJob?.cancel()
            phaseJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(phase = PlaybackPhase.REFERENCE, inputEnabled = false) }
                    delay(item.timing.referenceDurationMs)
                    _uiState.update { it.copy(phase = PlaybackPhase.AWAITING_ANSWER, inputEnabled = true) }
                }
        }

        /**
         * `M12`'s four phases — docs/20-PHASE-2-SPEC.md §2.3: cadence, then the named degree over
         * silence, then the note, then the answer.
         *
         * The gap gets its own [PlaybackPhase.AUDIATION_GAP] rather than being folded into the
         * reference phase, and that is the whole point of the screen: several seconds of silence with
         * "setting the key" still on screen would read as the app having stalled, which is exactly how
         * a learner ends up tapping past the part they were supposed to be doing. What the phase shows
         * is a calm indicator and an instruction, never a countdown (§5.3, docs/02-PEDAGOGY.md §6).
         */
        private fun runPredictionPhaseTimer(item: Item.PredictionItem) {
            phaseJob?.cancel()
            phaseJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(phase = PlaybackPhase.REFERENCE, inputEnabled = false) }
                    delay(item.referencePlan.sequentialDurationMs + item.timing.gapAfterReferenceMs)
                    _uiState.update { it.copy(phase = PlaybackPhase.AUDIATION_GAP) }
                    delay(item.gapBeforeSoundedNoteMs)
                    _uiState.update { it.copy(phase = PlaybackPhase.TARGET) }
                    delay(item.timing.targetDurationMs)
                    _uiState.update { it.copy(phase = PlaybackPhase.AWAITING_ANSWER, inputEnabled = true) }
                }
        }

        private fun runRecognitionPhaseTimer(item: Item.FunctionalRecognitionItem) {
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
            // The ladder is only ever on screen for a recognition item; narrowing here rather than at
            // every use below keeps the rest of this path exactly as it was.
            val item = _uiState.value.recognitionItem ?: return
            _uiState.update { it.copy(selectedDegree = degree, inputEnabled = false) }

            viewModelScope.launch {
                engine.submitAnswer(degree.canonicalLabel, autoAdvance = false)
                val feedback = engine.state.value.lastFeedback ?: return@launch
                val correctDegree = item.activeDegrees.first { it.canonicalLabel == feedback.correctLabel }
                // docs/20-PHASE-2-SPEC.md §5.4: at M10.MIXED_MODE the mode is withheld until here and
                // then stated. Null everywhere else, where naming a mode the learner was already told
                // would be noise on every single item.
                val revealedMode = item.mode.takeIf { SkillGraph.randomizesMode(item.skill) }
                _uiState.update { it.copy(correctDegree = correctDegree, revealedMode = revealedMode) }
                if (_uiState.value.hapticsEnabled) _hapticEvents.tryEmit(Unit)

                if (feedback.correct) {
                    delay(CORRECT_FEEDBACK_MS)
                } else {
                    // docs/02-PEDAGOGY.md §6: replay target-in-context, the chosen note, target again -
                    // "do not advance until it completes" (docs/08-UI-SPEC.md §3).
                    delay(INCORRECT_FLASH_SETTLE_MS)
                    engine.playIncorrectContrast(degree.canonicalLabel)
                }
                delay(INTER_ITEM_PAUSE_MS)
                engine.proceedToNextItem()
            }
        }

        /**
         * A non-degree answer — `M9`'s major/minor, `M12`'s matched/too-low/too-high.
         *
         * Deliberately not routed through [onDegreeSelected]: there is no degree to select, no ladder
         * button to flash, and docs/02-PEDAGOGY.md §6's contrast sequence has nothing to contrast — a
         * mode judgment has no "note you picked", and a prediction item's contrast is between the note
         * the learner *held* and the one that sounded, neither of which is a chosen button. So the
         * incorrect-answer path here is a plain pause rather than audio.
         */
        fun onLabelSelected(label: String) {
            if (!_uiState.value.inputEnabled) return
            _uiState.update { it.copy(selectedAnswerLabel = label, inputEnabled = false) }

            viewModelScope.launch {
                engine.submitAnswer(label, autoAdvance = false)
                val feedback = engine.state.value.lastFeedback ?: return@launch
                _uiState.update { it.copy(correctAnswerLabel = feedback.correctLabel) }
                if (_uiState.value.hapticsEnabled) _hapticEvents.tryEmit(Unit)

                delay(if (feedback.correct) CORRECT_FEEDBACK_MS else INCORRECT_MODE_SETTLE_MS)
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
            // Major, then minor, then the chromatic degrees. Minor only opens once the whole major
            // chain is mastered: M10's real prerequisite is M9.MODE_ID_TRIAD (docs/20-PHASE-2-SPEC.md
            // §3), and until the M9 gate is reachable from Home this is the conservative stand-in - it
            // never routes a learner into minor before they can hold a key in major, which is the
            // property that matters. M11 sits last for the same reason: its declared prerequisite is
            // M2.INDEPENDENCE_CHECK, which this stand-in cannot observe, so it waits for strictly more
            // than the spec requires rather than less.
            val chain =
                SkillGraph.m2Nodes + SkillGraph.m10Nodes + listOf(SkillGraph.m10MixedModeNode) +
                    SkillGraph.m11Nodes + SkillGraph.m12Nodes
            val currentNodeId =
                chain.firstOrNull { states[it.id]?.masteryState != MasteryState.MASTERED }?.id
                    ?: chain.last().id
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

            /**
             * Longer than the correct-answer pause: on a wrong answer the user needs a beat to see
             * which one it actually was. Shorter than the recognition path's total, which also plays
             * an audio contrast sequence this task has no equivalent for.
             */
            const val INCORRECT_MODE_SETTLE_MS = 900L

            // "Between items: a short, consistent pause (~400 ms). Do not vary it randomly" - docs/08-UI-SPEC.md §4.
            const val INTER_ITEM_PAUSE_MS = 400L

            /** Long enough to read across the item change; short enough to be gone before the next answer. */
            const val SKIP_NOTICE_MS = 1_600L

            /** One second: the finest granularity a thin, unlabeled time bar can meaningfully show. */
            const val TIME_TICK_MS = 1_000L
        }
    }
