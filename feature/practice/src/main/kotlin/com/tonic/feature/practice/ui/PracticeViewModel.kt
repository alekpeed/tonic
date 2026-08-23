package com.tonic.feature.practice.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonic.core.audio.capture.MicrophoneSource
import com.tonic.core.audio.pitch.SungResponseAnalyzer
import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.rhythm.RhythmRenderer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.data.settings.SettingsRepository
import com.tonic.core.engine.session.DueReview
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.attempts.InputMethod
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.AudiatedPitch
import com.tonic.core.model.music.ScaleDegree
import com.tonic.core.model.music.SungAnswer
import com.tonic.core.model.music.UnclearReason
import com.tonic.core.model.rhythm.AudioOutputRoute
import com.tonic.core.model.rhythm.RhythmQuestion
import com.tonic.core.model.state.AppSettings
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.PlannedSlot
import com.tonic.core.model.state.PracticeTrack
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.roundToLong
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
        private val microphoneSource: MicrophoneSource,
        private val clock: Clock,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) : ViewModel() {
        /**
         * Which curriculum this session works through — docs/40-PHASE-4-SPEC.md §2.
         *
         * Read once, at construction. A session's track cannot change part-way through: the plan, the
         * axes and the resumable state all belong to one chain, and switching would leave a session
         * composed against nodes it is no longer walking.
         */
        private val track: PracticeTrack = PracticeTrack.parse(savedStateHandle[PracticeTrack.ROUTE_ARG])
        private val _uiState = MutableStateFlow(PracticeUiState())
        val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

        private val _hapticEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val hapticEvents: SharedFlow<Unit> = _hapticEvents.asSharedFlow()

        /** Raw tap instants for the item on screen, on the monotonic clock the pointer events carry. */
        private val taps = mutableListOf<Long>()

        /** When the backing's pattern began, on the same clock. Null until the backing starts. */
        private var tapOriginUptimeMs: Long? = null

        private var phaseJob: Job? = null
        private var skipNoticeJob: Job? = null
        private var timeTickerJob: Job? = null
        private var started = false

        /**
         * Explanation kinds already put on screen during this visit to Practice — the *only* thing
         * limiting how often an explanation appears, now that the module half of [introKindFor] no
         * longer consults a persisted flag.
         *
         * In memory, and that is the design rather than a shortcut. Entering a module means seeing its
         * explanation, every time (docs/08-UI-SPEC.md §3a): this set makes "entering" mean leaving
         * Practice and coming back, so the screen does not reappear between items of a module already
         * entered, and does reappear next time — after ending a session, after a resume, after
         * anything. It is claimed at decide-time (`add` returning true *is* the claim) so two items
         * cannot both hold for the same screen.
         */
        private val introShownThisSession: MutableSet<IntroKind> = ConcurrentHashMap.newKeySet()

        /**
         * Set by [shouldHoldPlaybackFor] (on the engine's dispatcher, under its mutex) and consumed by
         * the state collector (on the main dispatcher) — the handoff that turns "this item's audio was
         * withheld" into the explanation screen actually appearing. Volatile for exactly that
         * cross-thread pair.
         */
        @Volatile
        private var pendingIntroKind: IntroKind? = null

        /** The freshest settings snapshot, for [shouldHoldPlaybackFor], which cannot suspend to read the flow. */
        @Volatile
        private var gateSettings = AppSettings()

        /** The one worked example, generated once and reused for replays so the audio never changes under the narration. */
        private val workedExample by lazy { WorkedExample.generate() }

        /** The minor counterpart, for [IntroKind.M10]'s screen. */
        private val minorWorkedExample by lazy { WorkedExample.generateMinor() }

        /** The audiation counterpart, for [IntroKind.M12]'s screen. */
        private val predictionWorkedExample by lazy { M12WorkedExample.generate() }

        /** The mixed-mode counterpart — deliberately a minor item, see [MixedModeWorkedExample]. */
        private val mixedModeWorkedExample by lazy { MixedModeWorkedExample.generate() }

        /** The mode-identification contrast pair, for [IntroKind.M9]'s screen — see [M9WorkedExample]. */
        private val m9WorkedPair by lazy { M9WorkedExample.pair() }

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
                // No explanation is decided here, deliberately. It is decided by
                // [shouldHoldPlaybackFor] when the loop actually reaches an item, which is the only
                // moment the *real* node is known: resolveSessionStart() answers "where would a fresh
                // session begin", and a resumed session begins wherever it left off instead. Choosing
                // here also stacked the explanation on top of the resume offer, so a returning learner
                // read about an exercise before choosing whether to continue into it.
                gateSettings = settingsRepository.settings.first()
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

        /**
         * Whether [item] can be answered by singing — docs/30-PHASE-3-SPEC.md §5.3's table.
         *
         * Three independent conditions, and all of them must hold. The learner opted in; a microphone
         * is actually available (§6.1 makes a denial ordinary, not an error — no prompt, no nagging,
         * no degraded experience); and the item has a pitch to sing at all. `M9` fails the last one:
         * its answer is major-or-minor, so there is nothing to produce, and offering to listen would
         * be asking for something that cannot be an answer.
         *
         * `M12` passes it, and means something different by it. On a recognition item singing *is* the
         * answer; on a prediction item it is evidence gathered during the gap while the answer stays
         * the three-button judgment (§5.4). Both are "this item has a pitch to sing," which is why one
         * flag serves both, but the two paths diverge immediately after — see [captureAudiation].
         */
        private fun sungAvailableFor(item: Item?): Boolean =
            gateSettings.sungResponseEnabled &&
                microphoneSource.isAvailable &&
                (item is Item.FunctionalRecognitionItem || item is Item.PredictionItem)

        /**
         * The engine's per-item playback gate — called under its mutex just before an item would play,
         * and the single place an explanation is decided.
         *
         * Two reasons to hold. An explanation screen is already up (one this gate raised, or a recall
         * via [onOpenIntro]): nothing may start sounding underneath it. Or this item's node needs an
         * explanation not shown yet on this visit ([introKindFor]): then this call *claims* it —
         * records the pending kind for the state collector to put on screen — and the item waits
         * behind it for [onIntroDismissed].
         *
         * [introShownThisSession] is the whole gate on repetition, and it is deliberately in-memory:
         * entering a module means seeing its explanation, every time, and only leaving and returning
         * counts as entering again (docs/08-UI-SPEC.md §3a). Twenty questions deep in `M12` nothing
         * reappears, because `M12`'s kind is already in the set.
         *
         * **A review slot never raises one.** Up to 40% of a session is spaced-repetition review of
         * older, already-mastered nodes, interleaved among the current node's items
         * (`SessionComposer`). Those are not the learner entering a module — they are being checked on
         * one they finished. Treating them as entry would stop practice partway through to re-explain
         * major-scale degrees to someone working on audiation, once per old module the session happens
         * to draw from, which is interruption rather than teaching.
         */
        private fun shouldHoldPlaybackFor(slot: PlannedSlot): Boolean {
            if (slot.isReview) return _uiState.value.showIntro
            val kind = introKindFor(slot.skillId, gateSettings, introShownThisSession)
            if (kind != IntroKind.NONE && introShownThisSession.add(kind)) {
                pendingIntroKind = kind
                return true
            }
            return _uiState.value.showIntro
        }

        /** The user accepted the resume offer — continue the interrupted session from its stored plan. */
        fun onResumeSession() {
            val session = _uiState.value.resumableSession ?: return
            _uiState.update { it.copy(resumableSession = null, isLoading = true) }
            viewModelScope.launch {
                engine.resume(session, holdPlaybackFor = ::shouldHoldPlaybackFor)
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

        /**
         * `M9`'s two labeled examples, each through the same rendering path as a live `M9` item. Two
         * separate entry points rather than one, because the screen labels each press with the word it
         * demonstrates - the pairing of word to sound *is* the lesson (see [M9WorkedExample]).
         */
        fun onPlayM9MajorExample() {
            viewModelScope.launch { audioPlayer.play(PracticeItems.renderAudio(m9WorkedPair.first)) }
        }

        fun onPlayM9MinorExample() {
            viewModelScope.launch { audioPlayer.play(PracticeItems.renderAudio(m9WorkedPair.second)) }
        }

        /** The reveal is user-driven: the answer is never shown before they've had the chance to listen. */
        fun onRevealWorkedExampleAnswer() {
            _uiState.update { it.copy(introAnswerRevealed = true) }
        }

        /**
         * Dismisses the explanation and starts the item waiting behind it.
         *
         * Only the sung-response flag is persisted. A module's screen is governed by
         * [introShownThisSession] alone, so there is nothing durable to write for it — see
         * [introKindFor] for why that rule changed. The `module*IntroSeen` fields still exist in
         * [AppSettings] and DataStore (docs/05-DATA-MODEL.md §3) but are no longer written or read;
         * they are kept rather than migrated away so the stored schema stays stable.
         */
        fun onIntroDismissed() {
            val kind = _uiState.value.introKind
            _uiState.update { it.copy(showIntro = false, introAnswerRevealed = false) }
            viewModelScope.launch {
                // The item was prepared but deliberately not played while the screen was up. This is
                // where the exercise actually begins - on Start, not on arrival.
                audioPlayer.stop()
                if (engine.releaseHeldPlayback()) {
                    // The playback captions started with the (silent) item; the learner is only now
                    // hearing it, so they restart from zero or they narrate the wrong moment.
                    _uiState.value.item?.let(::runPlaybackPhaseTimer)
                }
                // Only the sung screen is once-ever, so it is the only one with anything to record.
                if (kind == IntroKind.SUNG) settingsRepository.setSungResponseIntroSeen(true)
            }
        }

        /**
         * The help affordance - docs/11-ONBOARDING-CLARITY.md §5: "always reachable on demand... the
         * exact same explanation and worked example, not an abbreviated version."
         *
         * "The exact same explanation" means the one for the exercise on screen, so the kind is
         * recomputed from the node the loop is actually on — not left at whatever the session opened
         * with, which is how recall on an `M12` item once produced the major-recognition screen.
         */
        fun onOpenIntro() {
            audioPlayer.stop()
            val currentKind =
                engine.state.value.currentSkillId
                    ?.let(::moduleIntroKindFor)
            _uiState.update {
                it.copy(
                    showIntro = true,
                    introKind = currentKind ?: it.introKind,
                    introAnswerRevealed = false,
                )
            }
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
            gateSettings = settings
            val (currentNode, dueReviews) = resolveSessionStart()
            engine.start(
                currentNode = currentNode,
                dueReviews = dueReviews,
                sessionLengthMinutes = settings.sessionLengthMinutes,
                rootSeed = Random.nextLong(),
                now = clock.now(),
                holdPlaybackFor = ::shouldHoldPlaybackFor,
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
                    gateSettings = settings
                    _uiState.update {
                        it.copy(
                            labelStyle = settings.labelStyle,
                            reduceMotion = settings.reduceMotion,
                            hapticsEnabled = settings.hapticsEnabled,
                            audibleTaps = settings.audibleTapsEnabled,
                        )
                    }
                }
            }
            viewModelScope.launch {
                engine.state.collect { loopState ->
                    val itemChanged = _uiState.value.item?.seed != loopState.currentItem?.seed
                    // Claimed before the update, so the explanation this item was held for lands in
                    // the same emission as the item. See the comment on showIntro below.
                    val dueIntro = pendingIntroKind
                    pendingIntroKind = null
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
                            // Re-evaluated per item rather than once: M9 has no pitch to sing, so the
                            // control must disappear when the loop reaches one and come back after.
                            sungResponseAvailable = sungAvailableFor(loopState.currentItem),
                            sungCapture = if (itemChanged) SungCaptureState.IDLE else it.sungCapture,
                            lastSungCents = if (itemChanged) null else it.lastSungCents,
                            // Evidence belongs to the item it was sung into. Carrying it across would
                            // attach one item's audiation to the next item's attempt.
                            audiatedPitch = if (itemChanged) null else it.audiatedPitch,
                            // Taps belong to the item they were entered for. Carrying them across
                            // would score one pattern with another pattern's performance.
                            tapCount = if (itemChanged) 0 else it.tapCount,
                            // Raised in the *same* update as the item it explains, not one after.
                            //
                            // The playback gate held this item silent for an explanation the learner
                            // has not seen; this is where that explanation goes up. Doing it in a
                            // second _uiState.update published an intermediate state - item present,
                            // explanation not yet - and a StateFlow collector can observe it. That is
                            // the same defect in state form that the original report was about in
                            // audio form: the exercise arriving before the screen explaining it.
                            //
                            // pendingIntroKind is read and cleared above, before the update, because
                            // update's lambda can re-run under contention and must stay pure.
                            showIntro = dueIntro != null || it.showIntro,
                            introKind = dueIntro ?: it.introKind,
                            introAnswerRevealed = if (dueIntro != null) false else it.introAnswerRevealed,
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
                is Item.RhythmItem -> runRhythmPhaseTimer(item)
                else -> Unit
            }
        }

        /**
         * `M3`'s two phases — docs/40-PHASE-4-SPEC.md §3.3.
         *
         * Every rhythm item begins by *sounding*: the pattern for a production item, the target and
         * then every choice for a recognition one. What follows depends on what is being asked. A
         * recognition item simply opens its buttons. A production item plays a second time — the same
         * timeline with the pattern silent — and the learner taps into it, which is what §3.2's fade
         * table is describing when it says the metronome "stops for the pattern" at L4.
         *
         * The length comes from [RhythmRenderer.durationMs] rather than from the item's own timing
         * fields, because a rhythm item has none: how long it lasts is a property of its pattern, its
         * tempo and its metronome plan together, and computing it anywhere but beside the renderer is
         * how a caption comes to outlive the audio it describes.
         */
        private fun runRhythmPhaseTimer(item: Item.RhythmItem) {
            phaseJob?.cancel()
            phaseJob =
                viewModelScope.launch {
                    taps.clear()
                    tapOriginUptimeMs = null
                    _uiState.update {
                        it.copy(phase = PlaybackPhase.LISTENING, inputEnabled = false, tapCount = 0)
                    }
                    delay(RhythmRenderer.durationMs(item).toLong())
                    when (item.question) {
                        is RhythmQuestion.TapItBack -> runTapWindow(item)

                        is RhythmQuestion.WhichPattern, is RhythmQuestion.WhichBeatIsOne ->
                            _uiState.update {
                                it.copy(phase = PlaybackPhase.AWAITING_ANSWER, inputEnabled = true)
                            }
                    }
                }
        }

        /**
         * The production half: play the backing, let the learner tap into it, then score what they
         * played — docs/40-PHASE-4-SPEC.md §6.
         *
         * Input opens *before* the backing starts rather than after. The count-in is part of what the
         * learner is tapping against and a surface that only became live once it had finished would
         * swallow anyone who came in early — and coming in early on the count-in is a timing error the
         * app should record, not one it should silently delete.
         *
         * The window closes when the backing ends. There is no "done" button, deliberately: a pattern
         * has a length, the learner has just heard it twice, and asking them to also press something
         * afterwards would put a motor task between the performance and its scoring.
         */
        private suspend fun runTapWindow(item: Item.RhythmItem) {
            val backing = RhythmRenderer.renderBacking(item)
            _uiState.update { it.copy(phase = PlaybackPhase.AWAITING_ANSWER, inputEnabled = true) }
            val startedAtUptimeMs = android.os.SystemClock.uptimeMillis()
            val handle = audioPlayer.play(backing.buffer)
            tapOriginUptimeMs = startedAtUptimeMs + backing.countInDurationMs.roundToLong()
            handle.awaitCompletion()

            // Scored on its own coroutine, not this one. [phaseJob] is cancelled the moment the next
            // item arrives, and the next item arrives because of the proceedToNextItem() at the end of
            // submitTaps - so scoring inside this job would have it cancel itself part-way through
            // advancing, and whether the advance survived would depend on where the suspension
            // happened to be. Every other answer path in this class launches separately for the same
            // reason; this one is the only one that reaches submission from the phase timer at all.
            viewModelScope.launch { submitTaps() }
        }

        /**
         * Hands the recorded taps to the loop, which scores them.
         *
         * The rebasing happens here and the *scoring* does not — §4.4 puts scoring in one pure
         * function of `(pattern, taps, calibration, tolerance)`, and only the loop holds three of
         * those four. What this owes is the fourth: taps in milliseconds from the moment the pattern
         * began.
         *
         * ⚠️ **The origin ignores output latency, and that is a known gap rather than an oversight.**
         * §4.1 is explicit that the interval between `play()` being called and the sound being heard
         * is tens of milliseconds and enough to score a well-timed learner as rushing.
         * `PlaybackTimebaseSource` exists to close it and `RhythmCalibration` exists to measure it, and
         * neither is consumed here: the calibration screen is not built, so the constant is absent for
         * every learner, and a timebase reading taken the instant playback starts is either null or
         * left over from the previous track. Reading a stale one would place the origin far in the
         * past, which is worse than the offset it would be correcting. Until the calibration screen
         * lands, tapping is honest at the forgiving end of `TIMING_TOLERANCE` — L0's window is a
         * quarter of a beat, 150 ms at 100 BPM — and progressively less so as it tightens.
         */
        private suspend fun submitTaps() {
            val origin = tapOriginUptimeMs ?: return
            val relative = taps.map { (it - origin).toDouble() }
            _uiState.update { it.copy(inputEnabled = false) }

            engine.submitTaps(
                tapTimesMs = relative,
                calibrationOffsetMs = calibrationOffsetMs(),
                autoAdvance = false,
            )
            val feedback = engine.state.value.lastFeedback ?: return
            _uiState.update { it.copy(correctAnswerLabel = feedback.correctLabel) }
            if (_uiState.value.hapticsEnabled) _hapticEvents.tryEmit(Unit)

            delay(if (feedback.correct) CORRECT_FEEDBACK_MS else INCORRECT_MODE_SETTLE_MS)
            delay(INTER_ITEM_PAUSE_MS)
            engine.proceedToNextItem()
        }

        /**
         * The learner's measured tap latency for the route they are on, or zero when they have none.
         *
         * Zero is the honest reading of "never calibrated", not a default worth having: §4.3 derives
         * this from the learner's own taps and there is no screen yet to run that. See [submitTaps].
         */
        private fun calibrationOffsetMs(): Double =
            gateSettings.rhythmCalibrations.forRoute(AudioOutputRoute.SPEAKER)?.offsetMs ?: 0.0

        /**
         * One tap — docs/40-PHASE-4-SPEC.md §7.2.
         *
         * Stored raw and rebased at submission rather than converted here, because the origin is not
         * known until the backing starts and a tap may legitimately arrive before it: the count-in is
         * part of what the learner is playing against, and someone who comes in a beat early has made
         * a timing error the log should carry rather than one the screen should discard.
         */
        fun onRhythmTap(uptimeMillis: Long) {
            if (!_uiState.value.inputEnabled) return
            if (_uiState.value.rhythmItem?.question !is RhythmQuestion.TapItBack) return
            taps += uptimeMillis
            _uiState.update { it.copy(tapCount = taps.size) }
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
                    // Launched rather than awaited, so the gap keeps its own schedule: the note must
                    // sound when the rendered buffer says it does, whatever the microphone is doing.
                    // A child of this job, so skipping the item or an audio interruption cancels the
                    // capture with everything else rather than leaving a microphone running.
                    if (sungAvailableFor(item)) launch { captureAudiation(item) }
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

        fun onDegreeSelected(degree: ScaleDegree) = answerWithDegree(degree, InputMethod.TAP, sungCents = null)

        /**
         * Scores a degree answer, whichever way it arrived — docs/30-PHASE-3-SPEC.md §2.
         *
         * One path, deliberately, rather than a sung path beside a tapped one. §2 requires that "sung
         * and tapped attempts are not separate skill states," and the surest way to honor that is for
         * there to be no second implementation that could drift: the flash, the contrast sequence, the
         * mode reveal and the pacing are the same code for both, and [inputMethod] and [sungCents] are
         * carried through to the attempt without being consulted on the way.
         */
        private fun answerWithDegree(
            degree: ScaleDegree,
            inputMethod: InputMethod,
            sungCents: Int?,
        ) {
            if (!_uiState.value.inputEnabled) return
            // The ladder is only ever on screen for a recognition item; narrowing here rather than at
            // every use below keeps the rest of this path exactly as it was.
            val item = _uiState.value.recognitionItem ?: return
            _uiState.update {
                it.copy(
                    selectedDegree = degree,
                    inputEnabled = false,
                    sungCapture = SungCaptureState.IDLE,
                    lastSungCents = sungCents,
                )
            }

            viewModelScope.launch {
                engine.submitAnswer(
                    degree.canonicalLabel,
                    autoAdvance = false,
                    inputMethod = inputMethod,
                    sungCents = sungCents,
                )
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
         * Captures a sung answer and scores it as a degree — docs/30-PHASE-3-SPEC.md §5.2.
         *
         * The whole of §5.2 steps 1–7 in order: capture a bounded window, resolve it through
         * [SungResponseAnalyzer], and hand the resulting degree to the same scoring path a tap uses.
         *
         * **An unreadable answer records nothing at all.** §5.2: "a mumble, a cough, silence, or
         * background noise produces a retry prompt, never a recorded incorrect attempt. This matters
         * enormously — a false 'wrong' corrupts the staircase and the confusion matrix." So the
         * unclear branch leaves the item live, leaves input enabled, and writes no attempt: the
         * learner has not answered yet, and the app must not pretend otherwise in either direction.
         */
        fun onSingAnswer() {
            val state = _uiState.value
            if (!state.inputEnabled || !state.sungResponseAvailable) return
            if (state.sungCapture == SungCaptureState.LISTENING) return
            val item = state.recognitionItem ?: return

            _uiState.update { it.copy(sungCapture = SungCaptureState.LISTENING) }
            viewModelScope.launch {
                val captured = microphoneSource.record(SUNG_CAPTURE_WINDOW_MS)
                // Empty capture and unreadable capture are one case here on purpose: both mean the app
                // could not tell what was sung, and both owe the learner another go rather than a mark.
                val answer =
                    if (captured.isEmpty) {
                        SungAnswer.Unclear(UnclearReason.NO_VOICED_SIGNAL)
                    } else {
                        SungResponseAnalyzer.analyze(
                            buffer = captured.samples,
                            sampleRate = captured.sampleRate,
                            tonic = item.key,
                            mode = item.mode,
                            alphabet = item.activeDegrees,
                            a4Hz = gateSettings.referenceA4Hz.toDouble(),
                        )
                    }

                when (answer) {
                    is SungAnswer.Unclear ->
                        _uiState.update { it.copy(sungCapture = SungCaptureState.UNCLEAR) }

                    is SungAnswer.Resolved ->
                        answerWithDegree(
                            answer.degree,
                            InputMethod.SUNG,
                            sungCents = answer.centsFromDegree.roundToInt(),
                        )
                }
            }
        }

        /**
         * Listens for the audiated degree inside the silent gap — docs/30-PHASE-3-SPEC.md §5.4.
         *
         * **The window closes before the note sounds, and that is the entire point of this feature.**
         * §5.4: "sing the degree during the gap, before the note plays... You cannot fake this — either
         * you produced the right pitch from an internal representation or you didn't." A capture that
         * ran a moment past the gap would be recording a learner who has already heard the answer, and
         * the evidence would be worth nothing — it would look exactly like audiation while being
         * imitation. [audiationCaptureWindowMs] is what keeps the two apart, and
         * `SungPredictionCaptureTest` asserts the arithmetic rather than trusting this comment.
         *
         * **It starts on its own**, with no button to press first. Every other sung answer in the app
         * is opened by the learner tapping "Sing", but here the gap is one to five seconds long and is
         * itself the exercise: asking someone to find and press a control during it would replace the
         * thing being measured with a manual task, and at `PREDICT_GAP` level 0 there is not time to do
         * both. The learner has already opted in globally (§6.1), the explanation screen has already
         * said the app listens during the silence, and no audio is kept (§7).
         *
         * **Nothing here can produce a wrong answer.** An unreadable capture leaves [PracticeUiState.audiatedPitch]
         * null and the attempt is recorded exactly as a tap-only learner's would be. §6.5: "an unusable
         * signal produces 'unclear,' never 'wrong'" — and on this path it does not even produce
         * "unclear" to the learner mid-gap, because interrupting an audiation exercise to report a
         * microphone problem would break the silence the exercise is made of.
         */
        private suspend fun captureAudiation(item: Item.PredictionItem) {
            val windowMs = audiationCaptureWindowMs(item.gapBeforeSoundedNoteMs) ?: return
            delay(AUDIATION_CAPTURE_LEAD_IN_MS)
            _uiState.update { it.copy(sungCapture = SungCaptureState.LISTENING) }
            val captured = microphoneSource.record(windowMs)
            _uiState.update { it.copy(sungCapture = SungCaptureState.IDLE) }
            if (captured.isEmpty) return

            val answer =
                SungResponseAnalyzer.analyze(
                    buffer = captured.samples,
                    sampleRate = captured.sampleRate,
                    tonic = item.key,
                    mode = item.mode,
                    alphabet = item.activeDegrees,
                    a4Hz = gateSettings.referenceA4Hz.toDouble(),
                )
            if (answer !is SungAnswer.Resolved) return
            _uiState.update {
                // Guarded on the item still being the one that was sung into. The capture outlives no
                // gap by design, but a skip landing in the microseconds between `record` returning and
                // this update would otherwise staple the old item's audiation to the new one.
                if (it.predictionItem !== item) {
                    it
                } else {
                    it.copy(audiatedPitch = AudiatedPitch.from(answer, item.statedDegree, item.mode))
                }
            }
        }

        /**
         * How long to listen inside a gap of [gapMs], or null when the gap cannot hold a usable window.
         *
         * Both guards are silence, not padding. The lead-in lets the reference chord's release decay
         * before the microphone opens, and the tail guard closes it well before the sounded note begins
         * — docs/30-PHASE-3-SPEC.md §6.5: "reference audio and the answer window should not overlap; if
         * they must, echo cancellation is required." Keeping them apart in time is the version of that
         * which needs no echo cancellation and no device to verify.
         *
         * Returning null rather than a short window matters at `PREDICT_GAP` level 0. If the guards ever
         * grow, or a future level shortens the gap, the honest outcome is no sung evidence for that
         * item — the learner still answers with the buttons and is scored identically. A window too
         * short to hold a sustained note would instead produce a stream of unreadable captures, which
         * costs battery to learn nothing.
         */
        private fun audiationCaptureWindowMs(gapMs: Long): Long? {
            val window = gapMs - AUDIATION_CAPTURE_LEAD_IN_MS - AUDIATION_CAPTURE_TAIL_GUARD_MS
            return window.takeIf { it >= MIN_AUDIATION_CAPTURE_MS }
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
            // Read before the update below, and carried onto the attempt without being consulted:
            // docs/30-PHASE-3-SPEC.md §5.4's "both are recorded; the button answer is what scores."
            // [label] decides `correct`; this only ever lands in a column.
            val audiated = _uiState.value.audiatedPitch
            _uiState.update { it.copy(selectedAnswerLabel = label, inputEnabled = false) }

            viewModelScope.launch {
                engine.submitAnswer(
                    label,
                    autoAdvance = false,
                    // TAP even when the learner sang, and deliberately so. `inputMethod` records how
                    // the *scoring* answer arrived, and on a prediction item that is always the button
                    // — §5.4 rules out the sung pitch replacing it, because a node whose mastery meant
                    // "produce the pitch" for singers and "spot the mismatch" for everyone else would
                    // be two skills wearing one name, which §2 forbids outright. The singing is
                    // recorded beside the answer, in `sungCents`, not as the answer.
                    inputMethod = InputMethod.TAP,
                    sungCents = audiated?.centsFromStated,
                )
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
         * Which node to practice and which mastered nodes are due for review.
         *
         * The node choice is [SkillGraph.currentNodeFor]'s, not this screen's. It used to be a local
         * walk of a locally-declared chain, and so did Home's and the progress screen's — three
         * answers to one question, which had already diverged: two of them walked `M2` alone while
         * this one walked everything, so once the major nodes were mastered Home would report
         * `M2.FULL_DIATONIC` while sessions here ran minor.
         *
         * Deliberately keyed on MASTERED rather than on LOCKED vs. AVAILABLE. The gates a node
         * declares are checked by the resolver itself, which is stricter and more honest than reading
         * an unlock flag: a node opens when the things it actually depends on are done, whatever any
         * placement write happens to have recorded.
         */
        private suspend fun resolveSessionStart(): Pair<SkillWorkContext, List<DueReview>> {
            val states = skillStateRepository.observeAll().first()
            val mastered = { id: SkillId -> states[id]?.masteryState == MasteryState.MASTERED }
            // Two chains, and nothing decides between them for the learner. §2 makes rhythm parallel
            // to pitch rather than downstream of it, and §10 q3 - which track someone should be doing
            // right now - is open. So the track arrives with the route and this only resolves a node
            // within it.
            val currentNodeId =
                when (track) {
                    PracticeTrack.PITCH -> SkillGraph.currentNodeFor(mastered)
                    PracticeTrack.RHYTHM -> SkillGraph.currentRhythmNodeFor(mastered)
                }
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

        // Internal rather than private so tests can assert against the real timing constants instead
        // of restating them. `SungPredictionTest` checks that the audiation capture window plus its
        // guards fits inside the gap; duplicating the numbers there would make that check pass by
        // construction the moment either constant moved, which is exactly when it needs to fail.
        internal companion object {
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

            /**
             * How long one sung answer is captured for — docs/30-PHASE-3-SPEC.md §5.2 step 1's
             * "bounded window."
             *
             * Three seconds is generous for a single sustained note and still short enough that a
             * learner who says nothing is not left staring at a listening indicator. Bounded rather
             * than stopped by the user: a capture that runs until told otherwise is a microphone the
             * app can leave on, which §7's "no raw audio is ever persisted" promise is easier to keep
             * if it is structurally impossible to violate.
             */
            const val SUNG_CAPTURE_WINDOW_MS = 3_000L

            /**
             * Silence held at the start of an `M12` audiation gap before the microphone opens.
             *
             * The rendered item is one contiguous buffer — reference, silence, note (`PracticeItems`)
             * — so the cadence's release is still decaying into the room when the gap's clock starts.
             * Opening the microphone into that tail would hand the analyzer the app's own chord to
             * measure, and it would resolve confidently to a degree the learner never sang.
             */
            const val AUDIATION_CAPTURE_LEAD_IN_MS = 200L

            /**
             * Silence held at the end of the gap, after the microphone closes and before the note sounds.
             *
             * The load-bearing constant of Stage 3.4. §5.4's claim is that a sung prediction "cannot be
             * faked" *because* it is committed before the answer is audible; if capture and playback
             * ever overlapped, the app would be recording imitation and filing it as audiation. Sized
             * for scheduling jitter rather than for acoustics — the two events are ordered by
             * arithmetic, and this is the margin that keeps the ordering true when a coroutine is
             * dispatched late on a busy device.
             */
            const val AUDIATION_CAPTURE_TAIL_GUARD_MS = 250L

            /**
             * The shortest gap capture worth opening a microphone for.
             *
             * `SungResponseAnalyzer` discards [SungResponseAnalyzer.ONSET_SKIP_MS] of approach and then
             * needs [SungResponseAnalyzer.MIN_SUSTAINED_FRAMES] of steady tone, so a window under about
             * a third of a second cannot produce a reading whatever the learner does. At the shortest
             * gap the app generates — one second, `PREDICT_GAP` level 0 — the guards leave 550 ms,
             * which clears this comfortably; the check exists so that a future shorter level degrades
             * to "no sung evidence" instead of to a run of unreadable captures.
             */
            const val MIN_AUDIATION_CAPTURE_MS = 400L

            /** Long enough to read across the item change; short enough to be gone before the next answer. */
            const val SKIP_NOTICE_MS = 1_600L

            /** One second: the finest granularity a thin, unlabeled time bar can meaningfully show. */
            const val TIME_TICK_MS = 1_000L
        }
    }

/**
 * The explanation screen that belongs to [skillId]'s own module, never `NONE` — every node has one
 * task shape, and every task shape has a screen. This is what the help affordance recalls
 * (docs/11-ONBOARDING-CLARITY.md §5): recall answers "what is *this* exercise?", so it must be
 * decided by the node on screen, not by whichever screen a session happened to open with. The first
 * version of [onOpenIntro] reused the start-time kind, and a learner on an `M12` item who asked for
 * help was handed the major-recognition explanation — reported from live use as the instruction
 * screen having been deleted, which from their side is exactly what it looked like.
 *
 * Top-level rather than a member: it reads nothing from the view model, only its argument, and
 * lifting it out is what lets the mapping be asserted directly rather than through a constructed
 * view model and a fake repository.
 */
internal fun moduleIntroKindFor(skillId: SkillId): IntroKind =
    when (skillId) {
        SkillIds.M10_MIXED_MODE -> IntroKind.MIXED_MODE
        in SkillGraph.m12Nodes.map { it.id } -> IntroKind.M12
        in SkillGraph.m11Nodes.map { it.id } -> IntroKind.M11
        in SkillGraph.m10Nodes.map { it.id } -> IntroKind.M10
        in SkillGraph.m9Nodes.map { it.id } -> IntroKind.M9
        else -> IntroKind.M2
    }

/**
 * Which explanation [skillId] calls for. The module's own screen always; the sung-response one when
 * singing is on and its own first-encounter flag is still unset.
 *
 * **The module half deliberately consults no persisted flag.** It used to: each module's screen was
 * shown once ever, marked seen in DataStore, and never offered again. That is a defensible rule for a
 * settled learner and it was wrong for this app in its current state, by the maintainer's direct
 * instruction after live use — the screens became unreachable, in a way nothing on screen explained.
 * Ending a session did not bring them back. Neither did clearing the saved session, since that is a
 * different store entirely, so the app looked broken twice over. Entering a module now means seeing
 * its explanation, every time; [PracticeViewModel.introShownThisSession] keeps it to once per visit,
 * so it does not reappear between items of a module already entered.
 *
 * The sung-response screen keeps its flag. It is not a module — it explains a way of *answering*, can
 * surface on any node, and is reached only by opting in (docs/30-PHASE-3-SPEC.md §6.1), so "every
 * time you enter it" has no meaning to hang the rule on.
 *
 * Recall on demand ([PracticeViewModel.onOpenIntro]) does not consult this at all — it asks
 * [moduleIntroKindFor] directly, since recall must produce the current node's screen whether or not
 * anything is owed.
 */
internal fun introKindFor(
    skillId: SkillId,
    settings: AppSettings,
    alreadyShown: Set<IntroKind> = emptySet(),
): IntroKind {
    val moduleIntro = moduleIntroKindFor(skillId)
    if (moduleIntro !in alreadyShown) return moduleIntro

    // Checked after the module intro, and deliberately so. Singing is not tied to a node, so it could
    // surface anywhere - but a learner who has not yet been told what the *exercise* is should not
    // first be told how to answer it by voice. The module explanation wins; the sung one arrives at
    // the next item. [alreadyShown] is what sequences those two, since the module half no longer has
    // a persisted flag to fall through.
    val sungDue =
        settings.sungResponseEnabled &&
            !settings.sungResponseIntroSeen &&
            IntroKind.SUNG !in alreadyShown
    return if (sungDue) IntroKind.SUNG else IntroKind.NONE
}
