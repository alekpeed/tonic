package com.tonic.feature.practice.engine

import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.audio.synth.SynthEngine
import com.tonic.core.curriculum.generators.GenerationHistory
import com.tonic.core.curriculum.generators.M2ItemGenerator
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
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.items.Item
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.state.PlannedSlot
import com.tonic.core.model.state.SkillState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * Single-caller contract: like a real conversation, calls are sequential -
 * [submitAnswer]/[replay]/[abandonCurrentItem] must not be invoked
 * concurrently with each other or with [start]. Pre-rendering runs one
 * background render at a time and is always awaited before the engine's
 * mutable session state (the work queue, generation history) is touched
 * again, so this sequential contract is what keeps that state race-free
 * without needing a lock.
 */
class PracticeLoopEngine
    @Inject
    constructor(
        private val attemptRepository: AttemptRepository,
        private val skillStateRepository: SkillStateRepository,
        private val confusionRepository: ConfusionRepository,
        private val sessionRepository: SessionRepository,
        private val audioPlayer: AudioPlayer,
        private val clock: Clock,
    ) {
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val _state = MutableStateFlow(PracticeLoopState())
        val state: StateFlow<PracticeLoopState> = _state.asStateFlow()

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

        private val independenceCheckAttempts = mutableListOf<Attempt>()

        /** Starts a new session. [rootSeed] is the caller's responsibility (docs/04-ARCHITECTURE.md §4) - freshly randomized for a new session, fixed for a replay. */
        suspend fun start(
            currentNode: SkillWorkContext,
            dueReviews: List<DueReview>,
            sessionLengthMinutes: Int,
            rootSeed: Long,
            now: Instant,
        ) {
            this.rootSeed = rootSeed
            this.history = GenerationHistory()
            this.nextItemIndex = 0
            this.itemsCompleted = 0
            this.pending = null
            this.preRendered = null
            this.replayCountForCurrent = 0
            this.independenceCheckAttempts.clear()
            this.queue.clear()

            val plan = SessionComposer.compose(currentNode, dueReviews, sessionLengthMinutes, rootSeed, now)
            this.itemsPlanned = plan.plannedSlots.size
            plan.plannedSlots.forEach { queue.addLast(UpcomingWork.Regular(it)) }

            val session = sessionRepository.create(rootSeed, itemsPlanned, now)
            sessionId = requireNotNull(session.id) { "SessionRepository.create must return a persisted id" }

            advance()
        }

        /** Records [responseLabel] against the currently pending item, adapts state, and advances. */
        suspend fun submitAnswer(
            responseLabel: String,
            latencyMs: Long = 0,
        ) {
            val current = pending ?: return
            val correctLabel =
                current.item.targetDegree.degree
                    .toString()
            val correct = responseLabel == correctLabel
            val attempt = buildAttempt(current, responseLabel, correct, isAbandoned = false, latencyMs)
            persistAndAdapt(attempt)
            itemsCompleted++
            _state.update { it.copy(lastFeedback = AnswerFeedback(correct, correctLabel)) }
            advance()
        }

        /** Replays the current item's already-rendered audio. Unlimited and unpenalized — docs/08-UI-SPEC.md §? / docs/02-PEDAGOGY.md. */
        suspend fun replay() {
            val current = pending ?: return
            replayCountForCurrent++
            audioPlayer.play(current.buffer)
        }

        /**
         * Discards the current item rather than scoring it - docs/09-BUILD-PLAN.md Stage 6: "incoming
         * call, headphone unplug, app backgrounded, device rotated. In every case the current item is
         * discarded rather than scored." Still persisted (isAbandoned=true) so the attempt log stays
         * complete, but excluded from every adaptive computation (see [com.tonic.core.engine.replay.SkillStateReducer]).
         */
        suspend fun abandonCurrentItem() {
            val current = pending ?: return
            audioPlayer.stop()
            val attempt =
                buildAttempt(current, responseLabel = null, correct = false, isAbandoned = true, latencyMs = 0)
            attemptRepository.record(attempt)
            advance()
        }

        /** Releases this engine's background rendering scope. Call when the owning ViewModel/session is torn down. */
        fun close() {
            preRendered?.cancel()
            engineScope.cancel()
        }

        private suspend fun persistAndAdapt(attempt: Attempt) {
            attemptRepository.record(attempt)

            if (attempt.isIndependenceCheckProbe) {
                independenceCheckAttempts += attempt
                if (independenceCheckAttempts.size >= IndependenceCheck.REQUIRED_ITEMS) {
                    finishIndependenceCheck()
                }
                return
            }

            confusionRepository.record(
                attempt.skillId,
                attempt.targetLabel,
                attempt.responseLabel ?: attempt.targetLabel,
            )

            val before = skillStateRepository.observe(attempt.skillId).first()
            skillStateRepository.rebuildFromAttempts(attempt.skillId)
            val after = skillStateRepository.observe(attempt.skillId).first()

            if (before.masteryState != MasteryState.MASTERED && after.masteryState == MasteryState.MASTERED) {
                onNewlyMastered(attempt.skillId, after)
            }
        }

        /** docs/07-ADAPTIVE-ENGINE.md §7: "unlock the successor node... hand the current node to the review scheduler." */
        private suspend fun onNewlyMastered(
            skillId: SkillId,
            state: SkillState,
        ) {
            if (skillId == SkillIds.M2_FULL_DIATONIC) {
                queueIndependenceCheck(state.axisLevels)
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
         * queue so it starts on the very next item; any in-flight pre-render for what would have been the
         * next *ordinary* item is now for the wrong thing and gets discarded.
         */
        private fun queueIndependenceCheck(currentAxisLevels: Map<DifficultyAxis, Int>) {
            val forcedAxes = currentAxisLevels + (DifficultyAxis.CADENCE_FADE to INDEPENDENCE_CHECK_CADENCE_LEVEL)
            independenceCheckAttempts.clear()
            val probes = List(IndependenceCheck.REQUIRED_ITEMS) { UpcomingWork.IndependenceProbe(forcedAxes) }
            // addFirst repeatedly would reverse the order - insert back-to-front so the first probe in
            // the list is the first one dequeued.
            for (probe in probes.asReversed()) queue.addFirst(probe)
            preRendered?.cancel()
            preRendered = null
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
            independenceCheckAttempts.clear()
            skillStateRepository.rebuildFromAttempts(SkillIds.M2_FULL_DIATONIC)
        }

        private suspend fun advance() {
            pending = null
            val rendered = preRendered?.await() ?: renderNext()
            preRendered = null

            if (rendered == null) {
                sessionRepository.complete(sessionId, itemsCompleted, clock.now())
                _state.update { it.copy(currentItem = null, isFinished = true, itemsCompleted = itemsCompleted) }
                return
            }

            pending = rendered
            replayCountForCurrent = 0
            audioPlayer.play(rendered.buffer)
            preRendered = engineScope.async { renderNext() }

            _state.update {
                it.copy(
                    currentItem = rendered.item,
                    isIndependenceCheckProbe = rendered.isIndependenceProbe,
                    itemsCompleted = itemsCompleted,
                    itemsPlanned = itemsPlanned,
                )
            }
        }

        /**
         * Non-review work slots re-fetch the node's *live* axis levels here rather than trusting the
         * static snapshot [SessionComposer] baked into the plan at compose time - the staircase should
         * keep moving within a session, not just between them, which is exactly what
         * `:core:engine`'s own primary evidence (`SimulationHarness`) exercises: one continuous
         * per-attempt fold, no session boundaries in it at all. This stays fully replayable regardless -
         * axis levels are a deterministic function of the response history up to this point
         * ([com.tonic.core.engine.scheduling.AxisScheduler] is a pure fold), so the same rootSeed plus
         * the same sequence of recorded answers reproduces the same levels, and therefore the same items,
         * every time. Review slots keep the plan's static levels: a mastered node's axis levels are a
         * fixed reference point by definition, not something a review should let drift.
         */
        private suspend fun renderNext(): RenderedSlot? {
            val work = queue.removeFirstOrNull() ?: return null
            val index = nextItemIndex++
            val seed = SessionComposer.itemSeed(rootSeed, index)

            return when (work) {
                is UpcomingWork.Regular -> {
                    val axisLevels =
                        if (work.slot.isReview) {
                            work.slot.axisLevels
                        } else {
                            val live = skillStateRepository.observe(work.slot.skillId).first().axisLevels
                            if (work.slot.isWarmup) reducedCadenceFade(live) else live
                        }
                    val effectiveSlot = work.slot.copy(axisLevels = axisLevels)
                    val result = M2ItemGenerator.generate(effectiveSlot.skillId, axisLevels, seed, history)
                    history = result.updatedHistory
                    RenderedSlot(effectiveSlot, result.item, renderItemAudio(result.item), isIndependenceProbe = false)
                }
                is UpcomingWork.IndependenceProbe -> {
                    val result = M2ItemGenerator.generate(SkillIds.M2_FULL_DIATONIC, work.axisLevels, seed, history)
                    history = result.updatedHistory
                    val slot =
                        PlannedSlot(SkillIds.M2_FULL_DIATONIC, work.axisLevels, isWarmup = false, isReview = false)
                    RenderedSlot(slot, result.item, renderItemAudio(result.item), isIndependenceProbe = true)
                }
            }
        }

        private fun renderItemAudio(item: Item.FunctionalRecognitionItem): PcmBuffer =
            SynthEngine.renderItem(
                referencePlan = item.referencePlan,
                gapAfterReferenceMs = item.timing.gapAfterReferenceMs,
                targetMidi = item.targetMidi,
                targetTimbre = item.timbre,
                targetDurationMs = item.timing.targetDurationMs,
                seed = item.seed,
            )

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
                targetLabel =
                    rendered.item.targetDegree.degree
                        .toString(),
                responseLabel = responseLabel,
                correct = correct,
                latencyMs = latencyMs,
                replayCount = replayCountForCurrent,
                keyPitchClass = rendered.item.key.value,
                targetMidi = rendered.item.targetMidi,
                timbreId = rendered.item.timbre.name,
                cadenceFadeLevel = rendered.slot.axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0,
                timestamp = clock.now(),
                isWarmup = rendered.slot.isWarmup,
                isAbandoned = isAbandoned,
                isIndependenceCheckProbe = rendered.isIndependenceProbe,
            )

        private fun reducedCadenceFade(axisLevels: Map<DifficultyAxis, Int>): Map<DifficultyAxis, Int> {
            val cadence = axisLevels[DifficultyAxis.CADENCE_FADE] ?: 0
            return axisLevels + (DifficultyAxis.CADENCE_FADE to (cadence - 1).coerceAtLeast(0))
        }

        private inline fun MutableStateFlow<PracticeLoopState>.update(
            transform: (PracticeLoopState) -> PracticeLoopState,
        ) {
            value = transform(value)
        }

        private sealed interface UpcomingWork {
            data class Regular(
                val slot: PlannedSlot,
            ) : UpcomingWork

            data class IndependenceProbe(
                val axisLevels: Map<DifficultyAxis, Int>,
            ) : UpcomingWork
        }

        private data class RenderedSlot(
            val slot: PlannedSlot,
            val item: Item.FunctionalRecognitionItem,
            val buffer: PcmBuffer,
            val isIndependenceProbe: Boolean,
        )

        companion object {
            private const val INDEPENDENCE_CHECK_CADENCE_LEVEL = 6
        }
    }
