package com.tonic.feature.diagnostic.engine

import com.tonic.core.audio.player.AudioPlayer
import com.tonic.core.audio.synth.PcmBuffer
import com.tonic.core.curriculum.generators.M0ItemGenerators
import com.tonic.core.data.repository.DiagnosticRepository
import com.tonic.core.engine.diagnostic.PitchDifficultyLadder
import com.tonic.core.engine.diagnostic.PlacementCalculator
import com.tonic.core.engine.session.SessionComposer
import com.tonic.core.engine.staircase.DPrime
import com.tonic.core.engine.staircase.Staircase
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.TimbreId
import com.tonic.core.model.state.DiagnosticResult
import com.tonic.core.model.state.StaircaseState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.random.Random

/**
 * The headless M0 diagnostic loop — docs/09-BUILD-PLAN.md Stage 8, mirroring Stage 6's
 * `PracticeLoopEngine` shape (generate -> play -> answer -> adapt -> advance) but scoped to M0's much
 * smaller surface: four short sub-tests, no per-item persistence (docs/05-DATA-MODEL.md §1 - only the
 * single completed [DiagnosticResult] is saved, not an attempt log), and no pre-rendering, since an M0
 * item is one to a handful of short tones - cheap enough to synthesize on demand that a background
 * pre-render buys nothing a real device would notice.
 *
 * Deliberately gives **no per-item correctness feedback** - unlike M2's practice loop, this is a
 * calibration measurement, not a training exercise; revealing "right/wrong" mid-test would let a
 * participant's later answers be influenced by what they inferred about the true values other trials
 * were probing, contaminating the very thresholds being measured. docs/08-UI-SPEC.md §5: "No score is
 * shown at any point during or after."
 */
class DiagnosticLoopEngine
    @Inject
    constructor(
        private val audioPlayer: AudioPlayer,
        private val diagnosticRepository: DiagnosticRepository,
        private val clock: Clock,
    ) {
        private val _state = MutableStateFlow(DiagnosticLoopState())
        val state: StateFlow<DiagnosticLoopState> = _state.asStateFlow()

        private var rootSeed = 0L
        private var nextItemIndex = 0

        /** Starts a new diagnostic run. [rootSeed] is the caller's responsibility, same contract as `PracticeLoopEngine.start` (docs/04-ARCHITECTURE.md §4). */
        suspend fun start(rootSeed: Long) {
            this.rootSeed = rootSeed
            this.nextItemIndex = 0
            _state.value = DiagnosticLoopState()

            val pitchDirectionCents = runPitchDirection()
            val discriminationDPrime = runSameDifferent()
            val tonalMemorySpan = runTonalMemory()
            val amusiaDPrime = runAmusiaScreen()

            val placement =
                PlacementCalculator.compute(pitchDirectionCents, discriminationDPrime, tonalMemorySpan, amusiaDPrime)
            val result =
                DiagnosticResult(
                    pitchDirectionThresholdCents = pitchDirectionCents,
                    discriminationDPrime = discriminationDPrime,
                    tonalMemorySpan = tonalMemorySpan,
                    amusiaIndicatorFlag = placement.amusiaIndicatorFlag,
                    recommendedEntry = placement.recommendedEntry,
                    initialAxisLevels = placement.initialAxisLevels,
                    completedAt = clock.now(),
                    seed = rootSeed,
                )
            diagnosticRepository.save(result)

            _state.value =
                _state.value.copy(currentItem = null, inputEnabled = false, isFinished = true, result = result)
        }

        /** Records the response to the currently pending item. A no-op if nothing is pending or input isn't enabled yet. */
        suspend fun submitAnswer(responseLabel: String) {
            if (!_state.value.inputEnabled) return
            val answer = pendingAnswer ?: return
            _state.value = _state.value.copy(inputEnabled = false)
            answer.complete(responseLabel)
        }

        /** Replays the current item's already-rendered audio - unlimited, same contract as `:feature:practice`'s replay button. */
        suspend fun replay() {
            val buffer = pendingBuffer ?: return
            audioPlayer.play(buffer).awaitCompletion()
        }

        fun close() {
            // No background scope to cancel - see the class KDoc on why this engine has none.
        }

        // --- Sub-test 1: PITCH_DIR ---

        private suspend fun runPitchDirection(): Int {
            enterSubTest(M0SubTest.PITCH_DIRECTION)
            var staircase = StaircaseState(level = 0)
            var itemCount = 0
            while (!staircase.hasConverged && itemCount < PITCH_DIRECTION_MAX_ITEMS) {
                val cents = PitchDifficultyLadder.centsFor(staircase.level).toDouble()
                val item =
                    M0ItemGenerators.generatePitchDirection(SkillIds.M0_PITCH_DIR, cents, TIMBRE, nextSeed())
                val response = presentAndAwaitAnswer(item)
                val correctLabel =
                    if (item.secondCentsOffset >
                        0
                    ) {
                        AnswerAlphabet.HigherLower.HIGHER
                    } else {
                        AnswerAlphabet.HigherLower.LOWER
                    }
                staircase = PitchDifficultyLadder.update(staircase, response == correctLabel)
                itemCount++
            }
            return PitchDifficultyLadder.estimatedThresholdCents(staircase)
        }

        // --- Sub-test 2: SAME_DIFF ---

        private suspend fun runSameDifferent(): Double {
            enterSubTest(M0SubTest.SAME_DIFFERENT)
            var hits = 0
            var falseAlarms = 0
            var signalTrials = 0
            var noiseTrials = 0
            repeat(SAME_DIFFERENT_ITEM_COUNT) {
                val item =
                    M0ItemGenerators.generateSameDifferent(
                        SkillIds.M0_SAME_DIFF,
                        SAME_DIFFERENT_CENTS,
                        TIMBRE,
                        nextSeed(),
                    )
                val response = presentAndAwaitAnswer(item)
                val saidDifferent = response == AnswerAlphabet.SameDifferent.DIFFERENT
                if (item.isCatchTrial) {
                    noiseTrials++
                    if (saidDifferent) falseAlarms++
                } else {
                    signalTrials++
                    if (saidDifferent) hits++
                }
            }
            return DPrime.compute(hits, signalTrials, falseAlarms, noiseTrials)
        }

        // --- Sub-test 3: TONAL_MEMORY ---

        private suspend fun runTonalMemory(): Int {
            enterSubTest(M0SubTest.TONAL_MEMORY)
            var staircase = StaircaseState(level = TONAL_MEMORY_MIN_LENGTH)
            var itemCount = 0
            while (!staircase.hasConverged && itemCount < TONAL_MEMORY_MAX_ITEMS) {
                val item =
                    M0ItemGenerators.generateTonalMemory(
                        SkillIds.M0_TONAL_MEMORY,
                        staircase.level,
                        TONAL_MEMORY_ALTERATION_CENTS,
                        TIMBRE,
                        nextSeed(),
                    )
                val response = presentAndAwaitAnswer(item)
                val wasAltered = item.alteredIndex != null
                val correctLabel =
                    if (wasAltered) AnswerAlphabet.SameDifferent.DIFFERENT else AnswerAlphabet.SameDifferent.SAME
                staircase = Staircase.update(staircase, response == correctLabel, TONAL_MEMORY_BOUNDS)
                itemCount++
            }
            val estimate = staircase.estimatedThreshold?.let { Math.round(it).toInt() } ?: staircase.level
            return estimate.coerceIn(TONAL_MEMORY_BOUNDS)
        }

        // --- Sub-test 4: AMUSIA_SCREEN ---

        private suspend fun runAmusiaScreen(): Double {
            enterSubTest(M0SubTest.AMUSIA_SCREEN)
            val order =
                (List(AMUSIA_INTACT_COUNT) { false } + List(AMUSIA_ALTERED_COUNT) { true })
                    .shuffled(Random(rootSeed xor AMUSIA_SHUFFLE_SALT))
            var hits = 0
            var falseAlarms = 0
            var signalTrials = 0
            var noiseTrials = 0
            for (isAltered in order) {
                val item =
                    M0ItemGenerators.generateAmusiaScreen(
                        SkillIds.M0_AMUSIA_SCREEN,
                        isAltered,
                        AMUSIA_ALTERATION_SEMITONES,
                        TIMBRE,
                        nextSeed(),
                    )
                val response = presentAndAwaitAnswer(item)
                val saidAltered = response == AnswerAlphabet.IntactAltered.ALTERED
                if (isAltered) {
                    signalTrials++
                    if (saidAltered) hits++
                } else {
                    noiseTrials++
                    if (saidAltered) falseAlarms++
                }
            }
            return DPrime.compute(hits, signalTrials, falseAlarms, noiseTrials)
        }

        // --- Shared plumbing ---

        private var pendingAnswer: CompletableDeferred<String>? = null
        private var pendingBuffer: PcmBuffer? = null

        private fun enterSubTest(subTest: M0SubTest) {
            _state.value =
                _state.value.copy(currentSubTest = subTest, subTestIndex = M0SubTest.entries.indexOf(subTest))
        }

        /** Renders, plays, exposes the item, and suspends until [submitAnswer] delivers a response. */
        private suspend fun presentAndAwaitAnswer(item: Item): String {
            val buffer = M0AudioRenderer.render(item)
            pendingBuffer = buffer
            _state.value = _state.value.copy(currentItem = item, inputEnabled = false)
            audioPlayer.play(buffer).awaitCompletion()
            _state.value = _state.value.copy(inputEnabled = true)

            val deferred = CompletableDeferred<String>()
            pendingAnswer = deferred
            return deferred.await()
        }

        private fun nextSeed(): Long = SessionComposer.itemSeed(rootSeed, nextItemIndex++)

        private companion object {
            // A single neutral timbre throughout - docs/02-PEDAGOGY.md §5's "train across all four
            // timbres" is a functional-hearing (M2) requirement; M0 is measuring a raw psychophysical
            // threshold, where varying timbre would add noise to the very thing being measured.
            val TIMBRE = TimbreId.PURE

            const val PITCH_DIRECTION_MAX_ITEMS = 24 // docs/03-CURRICULUM.md §3: "24 items, whichever first."

            // docs/03-CURRICULUM.md §3 doesn't name SAME_DIFF's own item count or test difficulty (only
            // PITCH_DIR's ladder and AMUSIA_SCREEN's 16 are given explicit numbers) - this build's own
            // tuning, same category as Stage 3's axis pools: a fixed block at one representative
            // near-threshold difficulty, since SAME_DIFF's job is measuring discrimination *sensitivity*
            // (d-prime) rather than *finding* a threshold, which is PITCH_DIR's job.
            const val SAME_DIFFERENT_ITEM_COUNT = 20
            const val SAME_DIFFERENT_CENTS = 50.0

            // Also undocumented exact numbers, same rationale: a representative mid-ladder alteration
            // magnitude, a length range matching the generator's own documented 2..8 bound, and a
            // termination cap sized to fit the whole diagnostic's under-6-minute budget.
            const val TONAL_MEMORY_ALTERATION_CENTS = 50.0
            const val TONAL_MEMORY_MIN_LENGTH = 2
            const val TONAL_MEMORY_MAX_LENGTH = 8
            const val TONAL_MEMORY_MAX_ITEMS = 16
            val TONAL_MEMORY_BOUNDS = TONAL_MEMORY_MIN_LENGTH..TONAL_MEMORY_MAX_LENGTH

            const val AMUSIA_INTACT_COUNT = 8
            const val AMUSIA_ALTERED_COUNT = 8

            // mid-range of the documented 1..3 "clearly supra-threshold" band.
            const val AMUSIA_ALTERATION_SEMITONES = 2
            const val AMUSIA_SHUFFLE_SALT = -0x2545f4914f6cdd1eL
        }
    }
