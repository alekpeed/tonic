package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.audio.capture.CapturedAudio
import com.tonic.core.curriculum.graph.SkillGraph
import com.tonic.core.engine.debug.DebugMasterySeeder
import com.tonic.core.model.attempts.InputMethod
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.AnswerAlphabet
import com.tonic.core.model.items.Item
import com.tonic.core.model.music.Tuning
import com.tonic.core.model.state.AppSettings
import com.tonic.core.ui.components.PlaybackPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sung prediction in `M12` — Stage 3.4's production-wiring trace, docs/30-PHASE-3-SPEC.md §5.4.
 *
 * Two claims are being checked here, and they are the stage's two acceptance criteria.
 *
 * **"Sung answer captured during the gap, before the target plays."** Everything §5.4 argues for this
 * feature rests on the ordering: a pitch produced before the note is audible came from the learner's
 * head, and one produced after it might have come from their ears. Nothing downstream can tell the two
 * apart, so if the ordering is wrong the recorded evidence is worthless *and* looks fine. It is
 * asserted at the instant the microphone opens, through [com.tonic.feature.practice.engine.FakeMicrophoneSource.onRecord].
 *
 * **"Cannot be gamed by guessing."** Not, on its own, a property of the sung path — §5.4 decided the
 * sung pitch supplements the button rather than replacing it, so the guessing that d-prime already
 * catches is still caught by d-prime (`PredictionMasteryEvaluatorTest`). What Stage 3.4 adds is that
 * the sung record cannot be *manufactured*: it is committed before the answer sounds, and it never
 * moves the score in either direction. The tests below pin both halves — singing the right note does
 * not rescue a wrong button, and singing nonsense does not spoil a right one.
 *
 * Only the microphone is faked, and it returns synthesized audio at an exactly known frequency rather
 * than anything the fake invents.
 */
@RunWith(AndroidJUnit4::class)
class SungPredictionTest {
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun fixture(sungEnabled: Boolean = true): PracticeFixture {
        val fixture =
            PracticeFixture(
                AppSettings(
                    module2IntroSeen = true,
                    sungResponseEnabled = sungEnabled,
                    sungResponseIntroSeen = true,
                ),
            )
        fixture.microphoneSource.isAvailable = sungEnabled
        // Every node ahead of M12 marked done, so SkillGraph.currentNodeFor hands the loop a prediction
        // node — the same route DebugSkillJumper takes, rather than a test-only door into the engine.
        runBlocking {
            for (node in SkillGraph.practiceChain) {
                if (node.id == SkillIds.M12_PREDICT_TRIAD) break
                fixture.skillStateRepository.update(
                    DebugMasterySeeder.masteredStateFor(node.id, Instant.EPOCH),
                )
            }
        }
        return fixture
    }

    /** A sustained tone at [midi], long enough to survive the analyzer's onset discard. */
    private fun tone(
        midi: Int,
        durationMs: Int = 1_200,
    ): CapturedAudio {
        val frequency = Tuning.DEFAULT_A4_HZ * 2.0.pow((midi - A4_MIDI) / 12.0)
        val sampleRate = 48_000
        val samples =
            FloatArray((sampleRate * durationMs / 1000.0).toInt()) { i ->
                (kotlin.math.sin(2.0 * Math.PI * frequency * i / sampleRate) * 0.5).toFloat()
            }
        return CapturedAudio(samples, sampleRate)
    }

    /**
     * Starts a session and returns the first prediction item, past every explanation screen.
     *
     * The loop interleaves review of mastered nodes, and this fixture has just mastered two dozen of
     * them, so the first item on screen is often a recognition review rather than the prediction node
     * being tested. Waiting for the item type rather than for the first item is what makes that
     * irrelevant instead of flaky.
     */
    private suspend fun PracticeFixture.livePredictionItem(): Item.PredictionItem {
        viewModel.startIfNeeded()
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.item != null } }
        return withTimeout(TIMEOUT_MS) {
            var found: Item.PredictionItem? = null
            while (found == null) {
                if (viewModel.uiState.value.showIntro) viewModel.onIntroDismissed()
                val state = viewModel.uiState.value
                found = state.predictionItem?.takeIf { state.inputEnabled }
                if (found == null) delay(20)
            }
            found
        }
    }

    /**
     * Makes the fake microphone answer with the degree the item actually named, whatever item comes up.
     *
     * Deferred to the moment of capture rather than set beforehand, because the gap runs before the
     * test can see which item it is: by the time an item is on screen and answerable, the microphone
     * has already opened and closed. Reading the live item from inside [onRecord] is the only point at
     * which the item is known and the capture has not yet happened.
     */
    private fun PracticeFixture.singTheStatedDegree() {
        microphoneSource.onRecord = {
            val stated =
                viewModel.uiState.value.predictionItem
                    ?.statedMidi
            if (stated != null) microphoneSource.nextCapture = tone(stated)
        }
    }

    private suspend fun PracticeFixture.awaitAttempt() {
        withTimeout(TIMEOUT_MS) {
            while (attemptRepository.all.isEmpty()) delay(10)
        }
    }

    /**
     * The ordering the whole feature rests on: the microphone opens inside the silence, and closes
     * before the note it is a prediction *of* can be heard.
     *
     * Both halves are asserted, because either alone would pass while the feature was broken. The
     * phase check alone would accept a window that opened in the gap and ran on through the note; the
     * arithmetic alone would accept a correctly-sized window opened at the wrong moment.
     */
    @Test
    fun `the microphone opens inside the gap and closes before the note sounds`() =
        runBlocking {
            val fixture = fixture()
            val phasesAtCapture = mutableListOf<PlaybackPhase>()
            fixture.microphoneSource.onRecord = {
                phasesAtCapture += fixture.viewModel.uiState.value.phase
            }
            val item = fixture.livePredictionItem()

            assertEquals(listOf(PlaybackPhase.AUDIATION_GAP), phasesAtCapture, "capture ran outside the silent gap")

            val window = fixture.microphoneSource.requestedWindowsMs.single()
            val leadIn = PracticeViewModel.AUDIATION_CAPTURE_LEAD_IN_MS
            val tailGuard = PracticeViewModel.AUDIATION_CAPTURE_TAIL_GUARD_MS
            assertTrue(
                leadIn + window + tailGuard <= item.gapBeforeSoundedNoteMs,
                "capture window $window ms plus its guards does not fit inside a ${item.gapBeforeSoundedNoteMs} ms gap",
            )
            assertTrue(window > 0, "a gap this long should still have been listened to")
        }

    /**
     * The sung pitch reaches the attempt as `sungCents`, measured from the degree that was *named* —
     * and the attempt is still recorded as tapped, because the button is what scored it.
     *
     * `inputMethod` answers "how did the scoring answer arrive," and on a prediction item that is
     * always the three-button control (§5.4). Recording `SUNG` here would make the column mean one
     * thing on `M2` and a different thing on `M12`, and would suggest a sung mastery path that §2
     * forbids from existing.
     */
    @Test
    fun `the audiated pitch rides along on the attempt without becoming the answer`() =
        runBlocking {
            val fixture = fixture()
            fixture.singTheStatedDegree()
            val item = fixture.livePredictionItem()
            // Captured during the gap, before this line could run. What is asserted here is what the
            // learner committed to while the screen was silent.
            val audiated =
                assertNotNull(fixture.viewModel.uiState.value.audiatedPitch, "nothing was captured to ride along")
            assertTrue(audiated.heldStatedDegree, "the named degree was sung but not recognized")

            fixture.viewModel.onLabelSelected(item.correctLabel)
            fixture.awaitAttempt()

            val attempt = fixture.attemptRepository.all.last { it.skillId == item.skill }
            assertEquals(InputMethod.TAP, attempt.inputMethod)
            assertEquals(audiated.centsFromStated, attempt.sungCents)
        }

    /**
     * Singing the named degree perfectly does not rescue a wrong button.
     *
     * This is the load-bearing half of "supplement, not replace." If sung evidence could lift a wrong
     * judgment to correct, `M12` mastery would mean "produced the pitch" for a singer and "spotted the
     * mismatch" for everyone else — two skills under one node, which §2 rules out because it would make
     * a tap-only learner's mastery worth less than a singer's.
     */
    @Test
    fun `holding the right note does not make a wrong judgment correct`() =
        runBlocking {
            val fixture = fixture()
            fixture.singTheStatedDegree()
            val item = fixture.livePredictionItem()
            assertNotNull(fixture.viewModel.uiState.value.audiatedPitch, "this test is vacuous without a sung note")

            val wrong =
                AnswerAlphabet.MatchDirection.labels.first { label ->
                    !isScoredCorrect(item, label)
                }
            fixture.viewModel.onLabelSelected(wrong)
            fixture.awaitAttempt()

            val attempt = fixture.attemptRepository.all.last { it.skillId == item.skill }
            assertEquals(false, attempt.correct, "a sung pitch changed the score")
        }

    /**
     * And the mirror: nonsense in the gap does not spoil a right judgment.
     *
     * §6.5's "an unusable signal produces 'unclear,' never 'wrong'", applied at the one place it could
     * silently fail. A learner practicing in a noisy room, or one who simply chooses not to sing, must
     * be scored exactly as a Phase 2 learner was — so this drives the whole item with the microphone
     * returning silence and expects an ordinary correct attempt with no sung evidence attached.
     */
    @Test
    fun `an unreadable gap is scored exactly as a tap-only attempt`() =
        runBlocking {
            val fixture = fixture()
            // The default capture is empty, which is what a silent learner and a failed mic both produce.
            val item = fixture.livePredictionItem()
            assertNull(fixture.viewModel.uiState.value.audiatedPitch)

            fixture.viewModel.onLabelSelected(item.correctLabel)
            fixture.awaitAttempt()

            val attempt = fixture.attemptRepository.all.last { it.skillId == item.skill }
            assertEquals(true, attempt.correct)
            assertNull(attempt.sungCents, "silence was recorded as if it were a sung note")
            assertEquals(InputMethod.TAP, attempt.inputMethod)
        }

    /**
     * A learner who never turned singing on is never listened to — §6.1, and Stage 3.3's "tap-only path
     * fully unaffected" carried into the module Stage 3.4 touches.
     */
    @Test
    fun `no microphone opens when singing is off`() =
        runBlocking {
            val fixture = fixture(sungEnabled = false)
            val item = fixture.livePredictionItem()

            assertTrue(fixture.microphoneSource.requestedWindowsMs.isEmpty(), "recorded without being asked to")

            fixture.viewModel.onLabelSelected(item.correctLabel)
            fixture.awaitAttempt()
            val attempt = fixture.attemptRepository.all.last { it.skillId == item.skill }
            assertNull(attempt.sungCents)
        }

    /** Mirrors `PracticeItems.isCorrect` for the collapsed scoring at `M12.PREDICT_TRIAD`. */
    private fun isScoredCorrect(
        item: Item.PredictionItem,
        label: String,
    ): Boolean =
        if (SkillGraph.scoresDirection(item.skill)) {
            label == item.correctLabel
        } else {
            val chosen = AnswerAlphabet.MatchDirection.matchedVsNot(label)
            chosen == AnswerAlphabet.MatchDirection.matchedVsNot(item.correctLabel)
        }

    private companion object {
        const val TIMEOUT_MS = 30_000L
        const val A4_MIDI = 69
    }
}
