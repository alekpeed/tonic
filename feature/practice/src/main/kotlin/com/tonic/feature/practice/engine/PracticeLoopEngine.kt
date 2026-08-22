package com.tonic.feature.practice.engine

import com.tonic.core.audio.focus.AudioInterruptionEvent
import com.tonic.core.audio.focus.AudioInterruptions
import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.data.repository.AttemptRepository
import com.tonic.core.data.repository.ConfusionRepository
import com.tonic.core.data.repository.SessionRepository
import com.tonic.core.data.repository.SkillStateRepository
import com.tonic.core.engine.mastery.IndependenceCheck
import com.tonic.core.engine.session.DueReview
import com.tonic.core.engine.session.SessionComposer
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.attempts.Attempt
import com.tonic.core.model.ids.SkillId
import com.tonic.core.model.items.AxisChange
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.PlannedSlot
import com.tonic.core.model.state.ResumeState
import com.tonic.core.model.state.Session
import com.tonic.core.model.state.SessionPlan
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * The headless practice loop — docs/09-BUILD-PLAN.md Stage 6, wiring
 * docs/04-ARCHITECTURE.md §4's control flow end to end: generate → render
 * → (pre-render the next item while awaiting an answer) → play → answer →
 * record → adapt. No Compose, no ViewModel — [state] is a plain
 * [StateFlow] any caller (Stage 7's `PracticeViewModel`, or a JVM test
 * harness driving a simulated learner the same way `:core:engine`'s
 * `SimulationHarness` does) can collect and drive.
 *
 * Concurrency: user-driven calls ([submitAnswer]/[replay]/[abandonCurrentItem]/[proceedToNextItem])
 * are naturally sequential, but interruption handling (docs/06-AUDIO-ENGINE.md §8) is not — an audio
 * focus loss or a headphone unplug arrives from the platform at an arbitrary moment, including
 * mid-answer. Every public entry point therefore takes [loopMutex], which serializes them against each
 * other and against the interruption collector. Private helpers assume the lock is already held.
 */
class PracticeLoopEngine
    @Inject
    constructor(
        private val attemptRepository: AttemptRepository,
        private val skillStateRepository: SkillStateRepository,
        private val confusionRepository: ConfusionRepository,
        private val sessionRepository: SessionRepository,
        private val audioPlayer: AudioPlayer,
        private val audioInterruptions: AudioInterruptions,
        private val clock: Clock,
    ) {
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val _state = MutableStateFlow(PracticeLoopState())
        val state: StateFlow<PracticeLoopState> = _state.asStateFlow()

        /** Serializes user-driven calls against platform-driven interruptions. See the class KDoc. */
        private val loopMutex = Mutex()

        private val queue = ArrayDeque<UpcomingWork>()
        private var history = GenerationHistory()
        private var rootSeed = 0L
        private var nextItemIndex = 0
        private var sessionId = 0L
        private var itemsPlanned = 0
        private var itemsCompleted = 0

        private var pending: RenderedSlot? = null
        private var preRendered: Deferred<RenderedSlot?>? = null
        private var replayCountForCurrent = 0

        /** The plan this session is walking, kept so an interruption can persist it as [ResumeState]. */
        private var sessionPlan: SessionPlan? = null

        /**
         * Wall-clock end of this session, or null when no budget applies (a resumed session runs its
         * remaining plan). The user's `session_length_minutes` is honored as a real bound, not only as
         * SessionComposer's item-count estimate: the estimate assumes docs/07-ADAPTIVE-ENGINE.md §8's
         * ~9 items/minute, which a deliberate beginner does not hit, and a "3 minute" session was
         * observed from live use running long past 3 minutes at under half its planned items. Checked
         * only at item boundaries - an item in flight always finishes, because ending mid-stimulus is
         * time pressure, which docs/02-PEDAGOGY.md §6 prohibits.
         */
        private var sessionDeadline: Instant? = null

        /** Index into [SessionPlan.plannedSlots] of the last slot that produced a *scored* attempt. -1 before any. */
        private var lastScoredPlannedIndex = -1

        /** The in-flight persistence chain — see [enqueuePersist]. Joined wherever read-your-writes matters. */
        private var persistJob: Job? = null

        private var focusJob: Job? = null

        /**
         * Effective axis levels of the last item actually *presented*, and which skill they belonged to.
         * Diffed against the next presented item to detect any axis movement worth announcing
         * (docs/11-ONBOARDING-CLARITY.md §9.3). Effective, not raw: these are the levels the item was
         * generated at, so the scheduled warmup reduction is already folded in and the warmup-to-normal
         * transition at item 6 falls out of the same comparison as a staircase step, with no special case.
         */
        private var lastPresentedLevels: Map<DifficultyAxis, Int>? = null
        private var lastPresentedSkill: SkillId? = null

        /** Only a transient focus loss restores automatically on regain (docs/06-AUDIO-ENGINE.md §8). */
        private var pausedByTransientLoss = false

        private val independenceCheckAttempts = mutableListOf<Attempt>()

        /**
         * Answered attempts whose *adaptive* consequences haven't been applied yet — see
         * [applyPendingAdaptations], which is the only drainer and runs under [loopMutex].
         *
         * Still a concurrent collection: [submitAnswer] appends under the mutex, but the queue is also
         * cleared by [resetSessionState] and read on teardown paths. It is deliberately **not** drained
         * from the pre-render coroutine any more — an earlier revision did, and that overlap is exactly
         * what made generation non-deterministic.
         */
        private val pendingAdaptations = java.util.concurrent.ConcurrentLinkedQueue<Attempt>()

        /**
         * Suppresses playback of the first item only — docs/08-UI-SPEC.md §3a.
         *
         * The explanation screen is shown *over* a session that has already started, so that dismissing
         * it lands on a ready item rather than a spinner. That was right about the loading and wrong
         * about the sound: the first item's audio played underneath the screen, so a learner reading
         * "you'll hear a short sequence of chords" heard exactly that while still reading the sentence
         * explaining it. The explanation and the thing it explains arrived at the same moment, which is
         * the one arrangement guaranteed to teach neither.
         *
         * So the session still starts, renders, and pre-renders behind the screen. Only the playing
         * waits, until [releaseHeldPlayback].
         */
        private var playbackHeld = false

        /**
         * The buffer withheld by [playbackHeld], waiting for [releaseHeldPlayback].
         *
         * Separate from the flag because the two answer different questions: the flag says "withhold the
         * next item you prepare", and this says "here is the one that was withheld". Collapsing them
         * into the flag alone is what the first attempt did, and it silenced the *whole session* rather
         * than the first item - the flag stayed set, so every later item was suppressed too. Caught by
         * an existing test that counts buffers through a contrast sequence.
         */
        private var heldBuffer: PcmBuffer? = null

        /** Starts a new session. [rootSeed] is the caller's responsibility (docs/04-ARCHITECTURE.md §4) - freshly randomized for a new session, fixed for a replay. */
        suspend fun start(
            currentNode: SkillWorkContext,
            dueReviews: List<DueReview>,
            sessionLengthMinutes: Int,
            rootSeed: Long,
            now: Instant,
            holdPlayback: Boolean = false,
        ) = loopMutex.withLock {
            resetSessionState()
            this.playbackHeld = holdPlayback
            this.rootSeed = rootSeed

            val plan = SessionComposer.compose(currentNode, dueReviews, sessionLengthMinutes, rootSeed, now)
            this.sessionPlan = plan
            this.sessionDeadline = now.plusSeconds(sessionLengthMinutes * 60L)
            this.itemsPlanned = plan.plannedSlots.size
            plan.plannedSlots.forEachIndexed { index, slot -> queue.addLast(UpcomingWork.Regular(slot, index)) }

            val session = sessionRepository.create(rootSeed, itemsPlanned, now)
            sessionId = requireNotNull(session.id) { "SessionRepository.create must return a persisted id" }
            _state.update { it.copy(sessionId = sessionId, sessionStartedAt = now, sessionEndsAt = sessionDeadline) }

            beginAudioSession()
            advance()
        }

        /**
         * Continues an interrupted session — docs/10-TESTING.md §11's "force stop mid-session -> resume
         * offered, no data loss". [session] must be one [SessionRepository.findResumable] returned, i.e.
         * it carries a [ResumeState]. Replays the *remaining* planned slots from the stored plan; the
         * slot that was interrupted is re-presented rather than skipped, because its attempt was recorded
         * `isAbandoned` and deliberately never scored (docs/06-AUDIO-ENGINE.md §8).
         */
        suspend fun resume(
            session: Session,
            holdPlayback: Boolean = false,
        ) = loopMutex.withLock {
            val resumeState =
                requireNotNull(session.resumeState) { "resume() needs a session carrying a ResumeState" }
            resetSessionState()
            this.playbackHeld = holdPlayback

            val plan = resumeState.plan
            this.rootSeed = plan.rootSeed
            this.sessionPlan = plan
            this.itemsPlanned = plan.plannedSlots.size
            this.itemsCompleted = session.completedItemCount
            this.lastScoredPlannedIndex = resumeState.completedSlotIndex

            val firstRemaining = resumeState.completedSlotIndex + 1
            plan.plannedSlots.drop(firstRemaining).forEachIndexed { offset, slot ->
                queue.addLast(UpcomingWork.Regular(slot, firstRemaining + offset))
            }
            // Item seeds are a pure function of (rootSeed, index), so continuing the index sequence
            // reproduces exactly the items the interrupted run would have played next.
            this.nextItemIndex = firstRemaining
            this.sessionId = requireNotNull(session.id) { "a resumable session must be persisted" }

            // The session-length promise survives the interruption: the stored remaining budget picks
            // up where it left off. Rows written before the field existed fall back to an estimate
            // from the remaining plan at the composer's own planning rate.
            val now = clock.now()
            val remainingSeconds =
                resumeState.budgetRemainingSeconds
                    ?: ((plan.plannedSlots.size - firstRemaining) * SECONDS_PER_PLANNED_ITEM_ESTIMATE)
            sessionDeadline = now.plusSeconds(remainingSeconds.coerceAtLeast(MIN_RESUMED_BUDGET_SECONDS))
            _state.update {
                it.copy(
                    sessionId = sessionId,
                    itemsCompleted = itemsCompleted,
                    sessionStartedAt = now,
                    sessionEndsAt = sessionDeadline,
                )
            }

            beginAudioSession()
            advance()
        }

        /**
         * Called by the UI when the app is genuinely backgrounded (a real `ON_STOP`, not a configuration
         * change). Non-suspending and self-scheduling on [engineScope] deliberately: the caller is a
         * lifecycle callback whose own scope is about to be torn down, and the discarded-attempt write
         * must survive that.
         */
        fun onBackgrounded() {
            engineScope.launch { loopMutex.withLock { interrupt(InterruptionReason.BACKGROUNDED) } }
        }

        /** Continues after any pause. A no-op unless the loop is actually paused. */
        suspend fun resumeAfterPause() =
            loopMutex.withLock {
                if (!_state.value.isPaused) return@withLock
                pausedByTransientLoss = false
                audioInterruptions.requestFocus()
                _state.update { it.copy(isPaused = false) }
                advance()
            }

        private fun resetSessionState() {
            history = GenerationHistory()
            nextItemIndex = 0
            itemsCompleted = 0
            pending = null
            preRendered = null
            replayCountForCurrent = 0
            lastScoredPlannedIndex = -1
            pausedByTransientLoss = false
            independenceCheckAttempts.clear()
            pendingAdaptations.clear()
            lastPresentedLevels = null
            lastPresentedSkill = null
            sessionDeadline = null
            playbackHeld = false
            heldBuffer = null
            queue.clear()
        }

        /**
         * docs/06-AUDIO-ENGINE.md §8: focus is requested for the duration of a session, and every
         * interruption the platform reports discards the current item rather than scoring it.
         */
        private fun beginAudioSession() {
            audioInterruptions.requestFocus()
            focusJob?.cancel()
            focusJob =
                engineScope.launch {
                    audioInterruptions.events.collect { event ->
                        val reason =
                            when (event) {
                                // A duck request is deliberately handled as a pause, never a duck:
                                // "ducking changes the loudness of a stimulus mid-item, which corrupts
                                // the trial" - AudioFocusManager already folds it into TransientLoss.
                                AudioInterruptionEvent.TransientLoss -> InterruptionReason.TRANSIENT_FOCUS_LOSS
                                AudioInterruptionEvent.PermanentLoss -> InterruptionReason.PERMANENT_FOCUS_LOSS
                                AudioInterruptionEvent.BecomingNoisy -> InterruptionReason.BECOMING_NOISY
                                AudioInterruptionEvent.FocusRegained -> null
                            }
                        loopMutex.withLock {
                            if (reason != null) {
                                interrupt(reason)
                            } else if (pausedByTransientLoss && _state.value.isPaused) {
                                // "restore on regain" - only for a transient loss.
                                pausedByTransientLoss = false
                                _state.update { it.copy(isPaused = false) }
                                advance()
                            }
                        }
                    }
                }
        }

        /**
         * The one path every interruption funnels through — docs/06-AUDIO-ENGINE.md §8: "any interruption
         * mid-item marks the attempt `abandoned` and it does not enter the confusion matrix or the mastery
         * window." Deliberately does NOT advance (unlike [abandonCurrentItem], which is the user pressing
         * Skip): the point of an interruption is that playback must stop, so starting the next item's audio
         * immediately would be exactly wrong. Persists resume state so a process death here is recoverable.
         */
        private suspend fun interrupt(reason: InterruptionReason) {
            if (_state.value.isPaused || _state.value.isFinished) return
            pausedByTransientLoss = reason == InterruptionReason.TRANSIENT_FOCUS_LOSS
            discardPending()
            persistResumeState()
            _state.update { it.copy(currentItem = null, isPaused = true) }
        }

        /** Stops audio and records the pending item as abandoned, if there is one. Leaves the queue untouched. */
        private fun discardPending() {
            val current = pending ?: return
            audioPlayer.stop()
            pending = null
            val attempt =
                buildAttempt(current, responseLabel = null, correct = false, isAbandoned = true, latencyMs = 0)
            enqueuePersist { attemptRepository.record(attempt) }
        }

        /**
         * Writes `resumeStateJson` (docs/05-DATA-MODEL.md §1) so an interrupted session can be offered back
         * to the user. Uses [lastScoredPlannedIndex], not the pending item's index: the interrupted item
         * was discarded, so resume must re-present it rather than skip past it.
         */
        private fun persistResumeState() {
            val plan = sessionPlan ?: return
            val id = sessionId
            val completed = itemsCompleted
            val remainingSeconds =
                sessionDeadline?.let {
                    java.time.Duration
                        .between(clock.now(), it)
                        .seconds
                        .coerceAtLeast(0)
                }
            val resumeState =
                ResumeState(
                    plan = plan,
                    completedSlotIndex = lastScoredPlannedIndex,
                    budgetRemainingSeconds = remainingSeconds,
                )
            enqueuePersist { sessionRepository.updateResumeState(id, completed, resumeState) }
        }

        /**
         * Records [responseLabel] against the currently pending item, adapts state, and — unless
         * [autoAdvance] is false — advances immediately. A caller with its own feedback timing to run
         * first (docs/08-UI-SPEC.md §3: on an incorrect answer, a visual flash and then the
         * [playIncorrectContrast] audio sequence must complete *before* the next item appears) passes
         * `autoAdvance = false`, does that work, then calls [proceedToNextItem] itself. [pending] stays
         * valid the whole time — only [advance] clears it — so [playIncorrectContrast] can still reach
         * the item that was just answered.
         */
        suspend fun submitAnswer(
            responseLabel: String,
            latencyMs: Long = 0,
            autoAdvance: Boolean = true,
        ) = loopMutex.withLock {
            val current = pending ?: return@withLock
            val correctLabel = PracticeItems.correctLabel(current.item)
            // Not `responseLabel == correctLabel`: M12.PREDICT_TRIAD scores a mismatch without
            // requiring its direction (docs/20-PHASE-2-SPEC.md §8.1 decision 3). Identical to equality
            // for every other node and item type.
            val correct = PracticeItems.isCorrect(current.item, responseLabel)
            val attempt = buildAttempt(current, responseLabel, correct, isAbandoned = false, latencyMs)

            // docs/04-ARCHITECTURE.md §5: "Persist attempts asynchronously and do not block the loop on
            // them." Only the durable write leaves the loop. The adaptive half is deferred to the next
            // [advance], which is a fixed point in program order under the mutex - not to the pre-render
            // coroutine, whose scheduling is not. See [applyPendingAdaptations].
            enqueuePersist { writeAttempt(attempt) }
            pendingAdaptations += attempt

            itemsCompleted++
            current.plannedIndex?.let { lastScoredPlannedIndex = it }
            _state.update {
                it.copy(
                    lastFeedback = AnswerFeedback(correct, correctLabel),
                    itemsCompleted = itemsCompleted,
                )
            }
            if (autoAdvance) advance()
        }

        /**
         * Chains one persistence unit behind the previous one and runs it [NonCancellable].
         *
         * Chained, because ordering is load-bearing: `SkillStateReducer` replays the whole attempt log,
         * so attempt N must be written before N's rebuild, and N's rebuild before N+1's.
         * [NonCancellable], because "do not drop attempts" outranks prompt cancellation — a session torn
         * down mid-write must still land the write it already started. Scoped to [engineScope] rather
         * than a global scope (CLAUDE.md §7 forbids `GlobalScope`), so it dies with the session at the
         * latest.
         */
        private fun enqueuePersist(block: suspend () -> Unit) {
            val previous = persistJob
            persistJob =
                engineScope.launch {
                    previous?.join()
                    withContext(NonCancellable) { block() }
                }
        }

        /**
         * Suspends until every queued write has landed. Called internally wherever a subsequent read
         * depends on a prior write, and exposed so a caller (or a test) can establish the same
         * happens-before relationship without sleeping.
         */
        suspend fun awaitPersistence() {
            persistJob?.join()
        }

        /**
         * Replays the current item. Unlimited and unpenalized — docs/08-UI-SPEC.md §4. For an item whose
         * own reference is silent (an L1 group item, an L6/L7 block item), the replay plays the item's
         * [Item.FunctionalRecognitionItem.homeReminder] in front of the target: replay exists for "I
         * didn't catch that", and on a silent item the thing not caught is home itself — replaying the
         * bare note again answers nothing, which was reported from live use in exactly those words. The
         * *first* presentation stays silent (the retention demand is the level's point), and every
         * replay still increments [Attempt.replayCount], so reliance on the reminder is visible in
         * diagnostics rather than penalized.
         */

        suspend fun replay() =
            loopMutex.withLock {
                val current = pending ?: return@withLock
                replayCountForCurrent++
                // Only a recognition item has a "way back home" to replay - see the KDoc above. Every
                // other item type replays exactly what it played the first time.
                val reminder = (current.item as? Item.FunctionalRecognitionItem)?.homeReminder
                val buffer =
                    if (reminder != null) {
                        val m2 = current.item as Item.FunctionalRecognitionItem
                        SynthEngine.renderItem(
                            referencePlan = reminder,
                            gapAfterReferenceMs = m2.timing.gapAfterReferenceMs,
                            targetMidi = m2.targetMidi,
                            targetTimbre = m2.timbre,
                            targetDurationMs = m2.timing.targetDurationMs,
                            seed = m2.seed,
                        )
                    } else {
                        current.buffer
                    }
                audioPlayer.play(buffer)
                Unit
            }

        /**
         * Plays the item that was prepared while [playbackHeld] suppressed it, and lifts the hold.
         *
         * Not counted as a replay: [Attempt.replayCount] means "how many times the user asked to hear it
         * again", and this is the first time they are hearing it at all. Idempotent and a no-op when
         * nothing is held, so a second dismiss cannot start the audio twice.
         */
        suspend fun releaseHeldPlayback() =
            loopMutex.withLock {
                val buffer = heldBuffer ?: return@withLock
                heldBuffer = null
                audioPlayer.play(buffer)
            }

        /**
         * docs/02-PEDAGOGY.md §6: "On an incorrect answer, the app replays the target note in its tonal
         * context, then plays the note the learner chose, then the target again. Discrimination is
         * trained by contrast, not by being told a label." Deliberately bypasses [replay] rather than
         * calling it: this is a system-triggered corrective playback, not the user pulling the replay
         * button, so it must not inflate [Attempt.replayCount] — that field means "how many times the
         * user asked to hear it again," and conflating the two would corrupt that diagnostic signal.
         * Must be called with [responseLabel] still [pending] (i.e. after `submitAnswer(autoAdvance =
         * false)`, before [proceedToNextItem]) — a no-op otherwise.
         */
        suspend fun playIncorrectContrast(responseLabel: String) =
            loopMutex.withLock {
                val current = pending ?: return@withLock
                // docs/02-PEDAGOGY.md §6's contrast sequence is about *which degree* was chosen versus
                // which was correct, so it applies to recognition items only. A mode-identification
                // answer has no "note you picked" to sound back.
                val item = current.item as? Item.FunctionalRecognitionItem ?: return@withLock
                audioPlayer.play(current.buffer).awaitCompletion()

                val chosenDegree =
                    requireNotNull(item.activeDegrees.firstOrNull { it.canonicalLabel == responseLabel }) {
                        "responseLabel '$responseLabel' is not one of this item's active degrees"
                    }
                val chosenMidi =
                    item.targetMidi - item.targetDegree.semitoneOffset(item.mode) +
                        chosenDegree.semitoneOffset(item.mode)
                audioPlayer
                    .play(
                        SynthEngine.renderNote(
                            chosenMidi,
                            item.timbre,
                            item.timing.targetDurationMs,
                            seed = item.seed + CONTRAST_CHOSEN_NOTE_SEED_OFFSET,
                        ),
                    ).awaitCompletion()

                audioPlayer
                    .play(
                        SynthEngine.renderNote(
                            item.targetMidi,
                            item.timbre,
                            item.timing.targetDurationMs,
                            seed = item.seed + CONTRAST_TARGET_REPEAT_SEED_OFFSET,
                        ),
                    ).awaitCompletion()
            }

        /** Exposes [advance] for a caller that answered with `submitAnswer(autoAdvance = false)`. */
        suspend fun proceedToNextItem() =
            loopMutex.withLock {
                // An interruption may have landed between the answer and this call - it already stopped
                // playback and persisted resume state, so advancing now would restart audio the user
                // can't hear.
                if (!_state.value.isPaused) advance()
            }

        /**
         * Discards the current item rather than scoring it - docs/09-BUILD-PLAN.md Stage 6: "incoming
         * call, headphone unplug, app backgrounded, device rotated. In every case the current item is
         * discarded rather than scored." Still persisted (isAbandoned=true) so the attempt log stays
         * complete, but excluded from every adaptive computation (see [com.tonic.core.engine.replay.SkillStateReducer]).
         */
        suspend fun abandonCurrentItem() =
            loopMutex.withLock {
                if (pending == null) return@withLock
                discardPending()
                advance()
            }

        /**
         * The user is leaving the practice screen mid-session - docs/08-UI-SPEC.md §2a's "way out".
         * Rides the same path as a platform interruption: the in-flight item is discarded as abandoned
         * (never scored), and the session plan plus position land in `resumeStateJson` so the next
         * launch offers this exact session back (docs/05-DATA-MODEL.md §1). Suspends until the writes
         * are durably queued past cancellation, because the caller navigates away - and the ViewModel
         * teardown that follows - immediately after this returns. Safe to call in any state; a finished
         * or never-started session is a no-op beyond releasing focus.
         */
        suspend fun leaveSession() {
            loopMutex.withLock {
                interrupt(InterruptionReason.USER_EXIT)
                audioInterruptions.releaseFocus()
                focusJob?.cancel()
            }
            awaitPersistence()
        }

        /** Releases audio focus and this engine's background scope. Call when the owning ViewModel/session is torn down. */
        fun close() {
            audioInterruptions.releaseFocus()
            focusJob?.cancel()
            preRendered?.cancel()
            // Writes already in flight finish regardless - see enqueuePersist's NonCancellable body.
            engineScope.cancel()
        }

        /**
         * The durable half of recording an answer: appends to the attempt log and the confusion matrix.
         * Deliberately touches no engine state, which is what makes it safe to run off the loop's own
         * coroutine (docs/04-ARCHITECTURE.md §5).
         */
        private suspend fun writeAttempt(attempt: Attempt) {
            attemptRepository.record(attempt)
            if (attempt.isIndependenceCheckProbe) return
            confusionRepository.record(
                attempt.skillId,
                attempt.targetLabel,
                attempt.responseLabel ?: attempt.targetLabel,
            )
        }

        /**
         * The *adaptive* half — rebuilding skill state and reacting to a mastery transition. Kept off the
         * async write path, because it reads back the attempt it just wrote and so must run after
         * [awaitPersistence] regardless.
         *
         * Called **only** from [advance]: under [loopMutex], at a fixed point in program order, and only
         * once the in-flight pre-render has been awaited. All three matter. An earlier revision ran this
         * at the head of `renderNext`, i.e. on the pre-render coroutine, which put it in a race with
         * [submitAnswer]'s append and made item generation non-deterministic — the same seed produced
         * CADENCE_FADE 3, 4 and 7 across three runs, violating CLAUDE.md §5 (see [axisLevelSnapshot]).
         * It also mutates engine state: [onNewlyMastered] can call [queueIndependenceCheck], which pushes
         * onto the plain `ArrayDeque` work queue that `renderNext` pops from, so it must not overlap a
         * live pre-render either.
         *
         * A mastery reached on the session's final item still extends the session, because the probes it
         * queues are picked up by the next [renderNext] rather than by the one already completed.
         */
        private suspend fun applyPendingAdaptations() {
            // Drain first, then await: [submitAnswer] enqueues an attempt's write *before* adding it here,
            // so anything drained is guaranteed to have its write already on the chain we then join.
            val batch = mutableListOf<Attempt>()
            while (true) batch += pendingAdaptations.poll() ?: break
            awaitPersistence()

            for (attempt in batch) {
                if (attempt.isIndependenceCheckProbe) {
                    independenceCheckAttempts += attempt
                    if (independenceCheckAttempts.size >= IndependenceCheck.REQUIRED_ITEMS) {
                        finishIndependenceCheck()
                    }
                    continue
                }

                val before = skillStateRepository.observe(attempt.skillId).first()
                skillStateRepository.rebuildFromAttempts(attempt.skillId)
                val after = skillStateRepository.observe(attempt.skillId).first()

                if (before.masteryState != MasteryState.MASTERED && after.masteryState == MasteryState.MASTERED) {
                    onNewlyMastered(attempt.skillId, after)
                }
            }
        }

        /** docs/07-ADAPTIVE-ENGINE.md §7: "unlock the successor node... hand the current node to the review scheduler." */
        private suspend fun onNewlyMastered(
            skillId: SkillId,
            state: SkillState,
        ) {
            // Whichever chain's final node this is - major or minor. Hardcoding M2 here meant minor
            // could be mastered without ever being asked to hold a key unaided, which is the one thing
            // the check exists to establish.
            if (SkillGraph.triggersIndependenceCheck(skillId)) {
                queueIndependenceCheck(skillId, state.axisLevels)
                return
            }
            val successor = SkillGraph.successorOf(skillId) ?: return
            val successorState = skillStateRepository.observe(successor).first()
            if (successorState.masteryState == MasteryState.LOCKED) {
                skillStateRepository.update(
                    SkillState.initial(successor).copy(
                        masteryState = MasteryState.AVAILABLE,
                        axisLevels = reducedCadenceFade(state.axisLevels),
                    ),
                )
            }
        }

        /**
         * docs/03-CURRICULUM.md §5.6: "Runs automatically once M2.FULL_DIATONIC is mastered. 30 items at
         * CADENCE_FADE L6, all axes at the user's current level otherwise." Jumped to the front of the
         * queue so it starts on the very next item.
         *
         * No longer discards an in-flight pre-render, because there can't be one: this now runs from
         * [applyPendingAdaptations] at the head of [renderNext], *before* any slot is dequeued, so the
         * very same call goes on to pick up the first probe queued here. (Cancelling the pre-render from
         * inside the pre-render coroutine cancelled that coroutine itself.)
         */
        private fun queueIndependenceCheck(
            skillId: SkillId,
            currentAxisLevels: Map<DifficultyAxis, Int>,
        ) {
            val forcedAxes = currentAxisLevels + (DifficultyAxis.CADENCE_FADE to INDEPENDENCE_CHECK_CADENCE_LEVEL)
            independenceCheckAttempts.clear()
            val probes = List(IndependenceCheck.REQUIRED_ITEMS) { UpcomingWork.IndependenceProbe(skillId, forcedAxes) }
            // addFirst repeatedly would reverse the order - insert back-to-front so the first probe in
            // the list is the first one dequeued.
            for (probe in probes.asReversed()) queue.addFirst(probe)
        }

        /**
         * docs/03-CURRICULUM.md §5.6: "Failure is not punitive: it lowers the fade axis and schedules
         * more work." The actual pass/fail evaluation and the resulting fade-axis adjustment both live
         * in [com.tonic.core.engine.replay.SkillStateReducer] now, not here: a rebuild replays the
         * whole attempt log - including these 30 probes, now that they're persisted - so the
         * adjustment is re-derived the same way on every future rebuild instead of being a one-off
         * write that a later ordinary review attempt's own rebuild would silently recompute away.
         */
        private suspend fun finishIndependenceCheck() {
            val certified = independenceCheckAttempts.firstOrNull()?.skillId
            independenceCheckAttempts.clear()
            certified?.let { skillStateRepository.rebuildFromAttempts(it) }
        }

        /** The one way a session completes, whether the plan ran out or the wall-clock budget did. */
        private suspend fun finishSession() {
            // Every queued write must land before the session row is closed - `complete` clears
            // resumeStateJson, and the attempt log is what all skill state replays from.
            awaitPersistence()
            sessionRepository.complete(sessionId, itemsCompleted, clock.now())
            audioInterruptions.releaseFocus()
            focusJob?.cancel()
            _state.update {
                it.copy(
                    currentItem = null,
                    isFinished = true,
                    itemsCompleted = itemsCompleted,
                    itemsPlanned = itemsPlanned,
                )
            }
        }

        private suspend fun advance() {
            pending = null
            // Await the in-flight pre-render *first*. That coroutine mutates `queue`, `nextItemIndex`
            // and `history`, none of which are thread-safe, and [applyPendingAdaptations] can mutate
            // `queue` too (a newly reached mastery queues independence probes via `addFirst`). Applying
            // adaptations before this await would put those two on the same ArrayDeque concurrently -
            // the same class of bug as the generation race below, found by the targeted sweep for it.
            val preRenderedSlot = preRendered?.await()
            preRendered = null

            // The one deterministic point at which deferred adaptations are applied: here, under
            // [loopMutex], in program order, with no pre-render in flight - never inside the pre-render
            // coroutine itself. See [applyPendingAdaptations] for what that race cost.
            applyPendingAdaptations()

            // The session budget is a real bound, not advisory (see [sessionDeadline]) - but it only
            // ever cuts at an item boundary, and the remaining *plan* is what gets cancelled: the bar
            // reads honestly full rather than reporting a finished session as an abandoned one.
            // Never severs an independence-check block: its 30 probes only evaluate as a complete block
            // (docs/03-CURRICULUM.md §5.6), and the queue-the-probes moment fires exactly once, at the
            // mastery transition - a block cut in half would be lost for good, not resumed.
            val deadline = sessionDeadline
            if (deadline != null &&
                !clock.now().isBefore(deadline) &&
                queue.firstOrNull() !is UpcomingWork.IndependenceProbe
            ) {
                itemsPlanned = itemsCompleted
                finishSession()
                return
            }

            val rendered = preRenderedSlot ?: renderNext(axisLevelSnapshot())

            if (rendered == null) {
                finishSession()
                return
            }

            // Detected before the presented-levels bookkeeping is updated, so the diff still has the
            // previous item to compare against - docs/11-ONBOARDING-CLARITY.md §9.3.
            val axisChange = detectAxisChange(rendered)
            lastPresentedLevels = rendered.slot.axisLevels
            lastPresentedSkill = rendered.slot.skillId

            pending = rendered
            replayCountForCurrent = 0
            if (playbackHeld) {
                // Consumed here: the hold covers the item it was armed for and no other.
                playbackHeld = false
                heldBuffer = rendered.buffer
            } else {
                audioPlayer.play(rendered.buffer)
            }
            // Snapshot taken *here*, at a fixed point in program order under the mutex, and handed to
            // the coroutine - so what the next item generates from no longer depends on when that
            // coroutine happens to get scheduled.
            val snapshot = axisLevelSnapshot()
            preRendered = engineScope.async { renderNext(snapshot) }

            _state.update {
                it.copy(
                    currentItem = rendered.item,
                    isIndependenceCheckProbe = rendered.isIndependenceProbe,
                    itemsCompleted = itemsCompleted,
                    itemsPlanned = itemsPlanned,
                    // Null on every item that didn't move an axis, so the announcement clears itself.
                    axisChange = axisChange,
                )
            }
        }

        /**
         * Every skill's current axis levels, read at one fixed point in program order under [loopMutex].
         *
         * Taken by [advance] and handed to [renderNext] rather than read inside it. Reading live state
         * from inside the pre-render coroutine made generation depend on scheduling: the pre-render for
         * item N+1 is launched while item N is still on screen, so whether it saw N's answer came down
         * to which coroutine ran first. Same seed, same code, three runs produced CADENCE_FADE 3, 4 and
         * 7 - a direct violation of CLAUDE.md §5's "the same seed and state must produce a byte-identical
         * item on every run and every device," and it silently corrupted every measurement taken against
         * the practice loop until it was found.
         */
        private suspend fun axisLevelSnapshot(): Map<SkillId, Map<DifficultyAxis, Int>> =
            skillStateRepository.observeAll().first().mapValues { it.value.axisLevels }

        /**
         * Non-review work slots use the node's axis levels as of [axisLevels] rather than the static
         * snapshot [SessionComposer] baked into the plan at compose time - the staircase should keep
         * moving within a session, not just between them, which is exactly what `:core:engine`'s own
         * primary evidence (`SimulationHarness`) exercises: one continuous per-attempt fold, no session
         * boundaries in it at all. Fully replayable - axis levels are a deterministic function of the
         * response history up to the snapshot point ([com.tonic.core.engine.scheduling.AxisScheduler] is
         * a pure fold), and the snapshot point itself is fixed in program order, so the same rootSeed
         * plus the same sequence of recorded answers reproduces the same levels, and therefore the same
         * items, every time.
         *
         * Because the snapshot is taken when the *previous* item is presented, adaptivity is one item
         * behind - the intended, previously documented behavior, and the price of pre-rendering ahead so
         * the answer-to-next-item gap stays instant (docs/06-AUDIO-ENGINE.md §7).
         *
         * Review slots keep the plan's static levels: a mastered node's axis levels are a fixed
         * reference point by definition, not something a review should let drift.
         */
        private suspend fun renderNext(axisLevels: Map<SkillId, Map<DifficultyAxis, Int>>): RenderedSlot? {
            val work = queue.removeFirstOrNull() ?: return null
            val index = nextItemIndex++
            val seed = SessionComposer.itemSeed(rootSeed, index)

            return when (work) {
                is UpcomingWork.Regular -> {
                    val effectiveLevels =
                        if (work.slot.isReview) {
                            work.slot.axisLevels
                        } else {
                            val snapshotted = axisLevels[work.slot.skillId] ?: work.slot.axisLevels
                            if (work.slot.isWarmup) reducedCadenceFade(snapshotted) else snapshotted
                        }
                    val effectiveSlot = work.slot.copy(axisLevels = effectiveLevels)
                    val result = PracticeItems.generate(effectiveSlot.skillId, effectiveLevels, seed, history)
                    history = result.updatedHistory
                    RenderedSlot(
                        effectiveSlot,
                        result.item,
                        renderItemAudio(result.item),
                        isIndependenceProbe = false,
                        plannedIndex = work.plannedIndex,
                    )
                }
                is UpcomingWork.IndependenceProbe -> {
                    val result = PracticeItems.generate(work.skillId, work.axisLevels, seed, history)
                    history = result.updatedHistory
                    val slot =
                        PlannedSlot(work.skillId, work.axisLevels, isWarmup = false, isReview = false)
                    RenderedSlot(
                        slot,
                        result.item,
                        renderItemAudio(result.item),
                        isIndependenceProbe = true,
                        plannedIndex = null,
                    )
                }
            }
        }

        private fun renderItemAudio(item: Item): PcmBuffer = PracticeItems.renderAudio(item)

        private fun buildAttempt(
            rendered: RenderedSlot,
            responseLabel: String?,
            correct: Boolean,
            isAbandoned: Boolean,
            latencyMs: Long,
        ): Attempt =
            Attempt(
                skillId = rendered.slot.skillId,
                sessionId = sessionId,
                itemSeed = rendered.item.seed,
                axisLevels = rendered.slot.axisLevels,
                targetLabel = PracticeItems.correctLabel(rendered.item),
                responseLabel = responseLabel,
                correct = correct,
                latencyMs = latencyMs,
                replayCount = replayCountForCurrent,
                keyPitchClass = PracticeItems.keyPitchClass(rendered.item),
                targetMidi = PracticeItems.targetMidi(rendered.item),
                timbreId = PracticeItems.timbreId(rendered.item),
                cadenceFadeLevel = PracticeItems.cadenceFadeLevel(rendered.item, rendered.slot.axisLevels),
                timestamp = clock.now(),
                isWarmup = rendered.slot.isWarmup,
                isAbandoned = isAbandoned,
                isIndependenceCheckProbe = rendered.isIndependenceProbe,
            )

        /**
         * The one axis worth announcing on this item, or null. Compares only within the same skill: a
         * review item of a different node sits at that node's own frozen levels, and diffing across
         * nodes would report a change the user's own performance did not cause.
         *
         * At most one is reported even if several moved. The scheduler moves one axis at a time
         * (docs/07-ADAPTIVE-ENGINE.md §3) so more than one is already unexpected, and a burst of
         * simultaneous messages is noise rather than clarity - [DifficultyAxis.SCHEDULING_PRIORITY]
         * breaks the tie toward the axis the engine itself considers most consequential.
         */
        private fun detectAxisChange(rendered: RenderedSlot): AxisChange? {
            val previous = lastPresentedLevels ?: return null
            if (lastPresentedSkill != rendered.slot.skillId) return null
            return DifficultyAxis.SCHEDULING_PRIORITY
                .firstNotNullOfOrNull { axis ->
                    val from = previous[axis] ?: return@firstNotNullOfOrNull null
                    val to = rendered.slot.axisLevels[axis] ?: return@firstNotNullOfOrNull null
                    if (from == to) null else AxisChange(axis, from, to)
                }
        }

        private fun reducedCadenceFade(axisLevels: Map<DifficultyAxis, Int>): Map<DifficultyAxis, Int> {
            val cadence = axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0
            return axisLevels + (DifficultyAxis.CADENCE_FADE to (cadence - 1).coerceAtLeast(0))
        }

        private sealed interface UpcomingWork {
            data class Regular(
                val slot: PlannedSlot,
                /** This slot's index in [SessionPlan.plannedSlots] — what resume state is expressed in. */
                val plannedIndex: Int,
            ) : UpcomingWork

            data class IndependenceProbe(
                /** The node being certified — its own degree set and mode are what the probes test. */
                val skillId: SkillId,
                val axisLevels: Map<DifficultyAxis, Int>,
            ) : UpcomingWork
        }

        private data class RenderedSlot(
            val slot: PlannedSlot,
            val item: Item,
            val buffer: PcmBuffer,
            val isIndependenceProbe: Boolean,
            /** Null for an independence-check probe — probes aren't part of the session plan. */
            val plannedIndex: Int?,
        )

        companion object {
            private const val INDEPENDENCE_CHECK_CADENCE_LEVEL = 6

            /**
             * Fallback pace for resumed sessions persisted before budgets were stored: the composer's
             * own planning rate of ~9 items/minute (docs/07-ADAPTIVE-ENGINE.md §8) — generous is fine,
             * the plan itself still bounds the session.
             */
            private const val SECONDS_PER_PLANNED_ITEM_ESTIMATE = 7L

            /** A resumed session always gets at least one meaningful stretch of practice. */
            private const val MIN_RESUMED_BUDGET_SECONDS = 60L

            // Distinct from renderItemAudio's own `seed + 999_999L` target offset so a contrast-sequence
            // render never collides with the item's original rendering.
            private const val CONTRAST_CHOSEN_NOTE_SEED_OFFSET = 5_000_001L
            private const val CONTRAST_TARGET_REPEAT_SEED_OFFSET = 5_000_002L
        }
    }
