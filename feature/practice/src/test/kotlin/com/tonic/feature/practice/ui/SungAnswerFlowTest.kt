package com.tonic.feature.practice.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonic.core.audio.capture.CapturedAudio
import com.tonic.core.model.attempts.InputMethod
import com.tonic.core.model.music.Tuning
import com.tonic.core.model.state.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sung answer through the real ViewModel, engine and analyzer — docs/30-PHASE-3-SPEC.md §5.2.
 *
 * Only the microphone is faked, and what it returns is synthesized audio at an exactly known
 * frequency rather than anything the fake invents. That is the whole reason
 * [com.tonic.core.audio.capture.MicrophoneSource] is an interface: everything from the buffer onward
 * is a pure function, so the answer path can be driven end to end on the JVM and only the capture
 * itself is left for a device.
 */
@RunWith(AndroidJUnit4::class)
class SungAnswerFlowTest {
    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun fixture(): PracticeFixture =
        PracticeFixture(AppSettings(module2IntroSeen = true, sungResponseEnabled = true)).also {
            it.microphoneSource.isAvailable = true
        }

    /** A sustained tone at [midi], long enough to survive the analyzer's onset discard. */
    private fun tone(
        midi: Int,
        centsOff: Double = 0.0,
        durationMs: Int = 1_200,
    ): CapturedAudio {
        val frequency = Tuning.DEFAULT_A4_HZ * 2.0.pow((midi - A4_MIDI + centsOff / 100.0) / 12.0)
        val sampleRate = 48_000
        val samples =
            FloatArray((sampleRate * durationMs / 1000.0).toInt()) { i ->
                (kotlin.math.sin(2.0 * Math.PI * frequency * i / sampleRate) * 0.5).toFloat()
            }
        return CapturedAudio(samples, sampleRate)
    }

    /** Waits for the answer to land in the log - the write is deliberately off the loop's critical path. */
    private suspend fun PracticeFixture.awaitAttempt() {
        withTimeout(TIMEOUT_MS) {
            while (attemptRepository.all.isEmpty()) kotlinx.coroutines.delay(10)
        }
    }

    private suspend fun PracticeFixture.liveItem(): com.tonic.core.model.items.Item.FunctionalRecognitionItem {
        startPastIntro()
        return withTimeout(TIMEOUT_MS) {
            viewModel.uiState.first { it.item != null && it.inputEnabled }.recognitionItem!!
        }
    }

    /**
     * The happy path, and the one thing about it that matters most: the attempt is recorded as sung,
     * carries its deviation, and is scored on the degree — not on how close the pitch was.
     */
    @Test
    fun `a sung note is scored as the degree it resolves to and recorded as sung`() =
        runBlocking {
            val fixture = fixture()
            val item = fixture.liveItem()
            // 40 cents flat of the right answer: comfortably a wrong *pitch*, unambiguously the right
            // *degree*. §3 mitigation 2 is exactly this case.
            fixture.microphoneSource.nextCapture = tone(item.targetMidi, centsOff = -40.0)

            fixture.viewModel.onSingAnswer()
            fixture.awaitAttempt()

            val attempt = fixture.attemptRepository.all.first()
            assertEquals(InputMethod.SUNG, attempt.inputMethod)
            assertTrue(attempt.correct, "40 cents flat of the target degree is that degree, answered right")
            assertTrue(
                (attempt.sungCents ?: 0) < -20,
                "the deviation is recorded as information - got ${attempt.sungCents}",
            )
        }

    /**
     * §5.2, the rule this phase most needs to be true: "a mumble, a cough, silence, or background
     * noise produces a retry prompt, never a recorded incorrect attempt. This matters enormously — a
     * false 'wrong' corrupts the staircase and the confusion matrix."
     *
     * Silence, in the most literal form there is. Nothing may reach the attempt log, and the item must
     * still be answerable — the learner has not answered yet, and the app must not decide otherwise in
     * either direction.
     */
    @Test
    fun `an unreadable answer records nothing and leaves the item live`() =
        runBlocking {
            val fixture = fixture()
            fixture.liveItem()
            fixture.microphoneSource.nextCapture = CapturedAudio.empty(48_000)

            fixture.viewModel.onSingAnswer()
            val state =
                withTimeout(TIMEOUT_MS) {
                    fixture.viewModel.uiState.first { it.sungCapture == SungCaptureState.UNCLEAR }
                }

            assertTrue(fixture.attemptRepository.all.isEmpty(), "§5.2: unclear is never a recorded attempt")
            assertTrue(state.inputEnabled, "the item is still waiting; it has not been answered")
            assertEquals(SungCaptureState.UNCLEAR, state.sungCapture)
        }

    /** And the retry works, landing a real answer where the unclear one left the item untouched. */
    @Test
    fun `singing again after an unclear answer records exactly one attempt`() =
        runBlocking {
            val fixture = fixture()
            val item = fixture.liveItem()

            fixture.microphoneSource.nextCapture = CapturedAudio.empty(48_000)
            fixture.viewModel.onSingAnswer()
            withTimeout(TIMEOUT_MS) {
                fixture.viewModel.uiState.first { it.sungCapture == SungCaptureState.UNCLEAR }
            }

            fixture.microphoneSource.nextCapture = tone(item.targetMidi)
            fixture.viewModel.onSingAnswer()
            fixture.awaitAttempt()

            assertEquals(
                1,
                fixture.attemptRepository.all.size,
                "the refused attempt must not have been counted alongside the real one",
            )
        }

    /**
     * §3 mitigation 3, through the whole pipeline rather than at the resolver: the same degree sung an
     * octave down is the same answer. Vocal range varies enormously, and forcing a register would test
     * range rather than hearing.
     */
    @Test
    fun `singing the answer an octave low is the same answer`() =
        runBlocking {
            val fixture = fixture()
            val item = fixture.liveItem()
            fixture.microphoneSource.nextCapture = tone(item.targetMidi - OCTAVE_SEMITONES)

            fixture.viewModel.onSingAnswer()
            fixture.awaitAttempt()

            assertTrue(
                fixture.attemptRepository.all
                    .first()
                    .correct,
            )
        }

    /** Capture is bounded — §7's "no raw audio is ever persisted" is easiest to keep when it cannot run on. */
    @Test
    fun `capture asks for a bounded window`() =
        runBlocking {
            val fixture = fixture()
            val item = fixture.liveItem()
            fixture.microphoneSource.nextCapture = tone(item.targetMidi)

            fixture.viewModel.onSingAnswer()
            fixture.awaitAttempt()

            assertTrue(
                fixture.microphoneSource.requestedWindowsMs.all { it in 1..MAX_REASONABLE_WINDOW_MS },
                "got ${fixture.microphoneSource.requestedWindowsMs}",
            )
        }

    /**
     * Stage 3.3's first acceptance criterion at the ViewModel: with singing switched off, the control
     * is not offered and pressing it does nothing. A learner who never opts in cannot reach this path
     * even by accident.
     */
    @Test
    fun `singing does nothing at all when it is switched off`() =
        runBlocking {
            val fixture = PracticeFixture(AppSettings(module2IntroSeen = true, sungResponseEnabled = false))
            val item = fixture.liveItem()
            fixture.microphoneSource.nextCapture = tone(item.targetMidi)

            fixture.viewModel.onSingAnswer()

            assertTrue(!fixture.viewModel.uiState.value.sungResponseAvailable)
            assertTrue(fixture.microphoneSource.requestedWindowsMs.isEmpty(), "the mic must never be opened")
            assertTrue(fixture.attemptRepository.all.isEmpty())
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val A4_MIDI = 69
        const val OCTAVE_SEMITONES = 12

        /** Generous, but finite: the point is that *some* bound is asked for, not which. */
        const val MAX_REASONABLE_WINDOW_MS = 30_000L
    }
}
