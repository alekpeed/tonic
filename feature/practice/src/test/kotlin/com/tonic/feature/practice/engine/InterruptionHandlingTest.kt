package com.tonic.feature.practice.engine

import com.tonic.core.audio.focus.AudioInterruptionEvent
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/06-AUDIO-ENGINE.md §8 and docs/09-BUILD-PLAN.md Stage 6's interruption acceptance criterion:
 * "incoming call, headphone unplug, app backgrounded, device rotated. In every case the current item
 * is discarded rather than scored."
 *
 * These drive the *real* [PracticeLoopEngine] through the same [com.tonic.core.audio.focus.AudioInterruptions]
 * contract the production [com.tonic.core.audio.focus.AudioFocusManager] implements, so what is under
 * test is the engine's actual reaction to a platform event — not a hand-rolled stand-in for it.
 */
class InterruptionHandlingTest {
    private class Fixture {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val confusionRepository = FakeConfusionRepository()
        val sessionRepository = FakeSessionRepository()
        val audioPlayer = FakeAudioPlayer()
        val audioInterruptions = FakeAudioInterruptions()
        val clock = Clock { Instant.EPOCH }
        val engine =
            PracticeLoopEngine(
                attemptRepository,
                skillStateRepository,
                confusionRepository,
                sessionRepository,
                audioPlayer,
                audioInterruptions,
                clock,
            )

        suspend fun startSession() =
            engine.start(
                SkillWorkContext(
                    SkillIds.M2_DEG_SET_1,
                    axisLevels = DifficultyAxis.entries.associateWith { 0 },
                    totalAttempts = 0,
                ),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )

        suspend fun awaitPaused() = withTimeout(TIMEOUT_MS) { engine.state.first { it.isPaused } }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }

    @Test
    fun `a session requests audio focus on start and releases it on close`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()

            assertEquals(1, fixture.audioInterruptions.focusRequestCount, "focus must be held for the session")
            assertTrue(fixture.audioInterruptions.holdsFocus)

            fixture.engine.close()
            assertFalse(fixture.audioInterruptions.holdsFocus, "focus must be released when the session tears down")
        }

    @Test
    fun `a transient focus loss - an incoming call - discards the current item instead of scoring it`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()
            val interruptedItem = assertNotNull(fixture.engine.state.value.recognitionItem)

            fixture.audioInterruptions.emit(AudioInterruptionEvent.TransientLoss)
            fixture.awaitPaused()
            fixture.engine.awaitPersistence()

            val recorded = fixture.attemptRepository.all.single()
            assertTrue(recorded.isAbandoned, "an item the user could not hear must never be scored")
            assertNull(recorded.responseLabel)
            assertFalse(recorded.correct)
            assertEquals(interruptedItem.seed, recorded.itemSeed)
            assertTrue(fixture.audioPlayer.stopCount >= 1, "playback must stop immediately")
            assertNull(fixture.engine.state.value.recognitionItem, "no item should be live while paused")
            assertFalse(fixture.engine.state.value.isFinished, "a pause is not the end of the session")

            // "does not enter the ... mastery window" - the abandoned attempt leaves skill state untouched.
            val skillState = fixture.skillStateRepository.observe(SkillIds.M2_DEG_SET_1).first()
            assertEquals(0, skillState.totalAttempts)
            assertEquals(MasteryState.LOCKED, skillState.masteryState)
        }

    @Test
    fun `headphones unplugged - becoming noisy - also discards the item and pauses`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()

            fixture.audioInterruptions.emit(AudioInterruptionEvent.BecomingNoisy)
            fixture.awaitPaused()
            fixture.engine.awaitPersistence()

            val recorded = fixture.attemptRepository.all.single()
            assertTrue(recorded.isAbandoned)
            assertTrue(fixture.engine.state.value.isPaused)
        }

    @Test
    fun `backgrounding the app discards the current item`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()

            fixture.engine.onBackgrounded()
            fixture.awaitPaused()
            fixture.engine.awaitPersistence()

            val recorded = fixture.attemptRepository.all.single()
            assertTrue(recorded.isAbandoned)
        }

    @Test
    fun `an interruption does not start the next item - the whole point is that audio stopped`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()
            val playsBefore = fixture.audioPlayer.playedBuffers.size

            fixture.audioInterruptions.emit(AudioInterruptionEvent.TransientLoss)
            fixture.awaitPaused()

            assertEquals(
                playsBefore,
                fixture.audioPlayer.playedBuffers.size,
                "pausing must not auto-play the next item - unlike Skip, which does advance",
            )
        }

    @Test
    fun `regaining focus after a transient loss restores the loop automatically`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()

            fixture.audioInterruptions.emit(AudioInterruptionEvent.TransientLoss)
            fixture.awaitPaused()

            fixture.audioInterruptions.emit(AudioInterruptionEvent.FocusRegained)
            val resumed = withTimeout(TIMEOUT_MS) { fixture.engine.state.first { !it.isPaused } }
            assertNotNull(resumed.recognitionItem, "the loop should be running again on a fresh item")
        }

    @Test
    fun `regaining focus after a permanent loss does NOT auto-restore - only a transient loss does`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()

            fixture.audioInterruptions.emit(AudioInterruptionEvent.PermanentLoss)
            fixture.awaitPaused()

            fixture.audioInterruptions.emit(AudioInterruptionEvent.FocusRegained)
            // Nothing to wait for - assert the state stayed paused rather than racing a non-event.
            fixture.engine.awaitPersistence()
            assertTrue(
                fixture.engine.state.value.isPaused,
                "a permanent loss ends the session cleanly; the user chooses when to continue",
            )
        }

    @Test
    fun `resumeAfterPause continues the session and re-requests focus`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()

            fixture.audioInterruptions.emit(AudioInterruptionEvent.BecomingNoisy)
            fixture.awaitPaused()
            val requestsBefore = fixture.audioInterruptions.focusRequestCount

            fixture.engine.resumeAfterPause()

            assertFalse(fixture.engine.state.value.isPaused)
            assertNotNull(fixture.engine.state.value.recognitionItem)
            assertEquals(
                requestsBefore + 1,
                fixture.audioInterruptions.focusRequestCount,
                "continuing after a pause needs focus back",
            )
        }
}
