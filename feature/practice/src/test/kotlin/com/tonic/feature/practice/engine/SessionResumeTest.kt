package com.tonic.feature.practice.engine

import com.tonic.core.audio.focus.AudioInterruptionEvent
import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/10-TESTING.md §11: "force stop mid-session -> resume offered, no data loss", and
 * docs/05-DATA-MODEL.md §1's `resumeStateJson` — the column that makes it possible. Before this pass
 * the whole mechanism existed and was unit-tested at the repository level but was never written or read
 * by production code, so an interrupted session was silently unrecoverable.
 */
class SessionResumeTest {
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
                rootSeed = 4242L,
                now = Instant.EPOCH,
            )

        suspend fun answerCurrentCorrectly() {
            val item = engine.state.value.recognitionItem ?: return
            engine.submitAnswer(item.targetDegree.degree.toString())
        }

        suspend fun awaitPaused() = withTimeout(5_000L) { engine.state.first { it.isPaused } }
    }

    @Test
    fun `no session is resumable before anything has been interrupted`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()
            fixture.engine.awaitPersistence()
            assertNull(
                fixture.sessionRepository.findResumable(),
                "an in-progress session with no interruption must not be offered for resume",
            )
        }

    @Test
    fun `an interruption persists resume state, making the session findable afterwards`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()
            fixture.answerCurrentCorrectly()
            fixture.answerCurrentCorrectly()

            fixture.audioInterruptions.emit(AudioInterruptionEvent.PermanentLoss)
            fixture.awaitPaused()
            fixture.engine.awaitPersistence()

            val resumable = assertNotNull(fixture.sessionRepository.findResumable())
            val resumeState = assertNotNull(resumable.resumeState)
            assertEquals(2, resumable.completedItemCount, "progress so far is recorded on the session row")
            assertTrue(resumeState.plan.plannedSlots.isNotEmpty(), "the plan itself is stored, not just a counter")
            assertEquals(4242L, resumeState.plan.rootSeed, "the plan's seed is what makes the rest reproducible")
            assertEquals(
                1,
                resumeState.completedSlotIndex,
                "two scored slots (0 and 1) means the last completed index is 1 - the interrupted slot is NOT counted",
            )
        }

    @Test
    fun `resuming continues from the interrupted slot rather than skipping it`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()
            fixture.answerCurrentCorrectly()
            fixture.answerCurrentCorrectly()

            // The item that was on screen when the interruption hit - it was discarded, never scored,
            // so a correct resume must present it again.
            val interruptedItem = assertNotNull(fixture.engine.state.value.recognitionItem)

            fixture.audioInterruptions.emit(AudioInterruptionEvent.BecomingNoisy)
            fixture.awaitPaused()
            fixture.engine.awaitPersistence()

            val resumable = assertNotNull(fixture.sessionRepository.findResumable())

            // A second engine instance - the process died, nothing carried over in memory.
            val second = Fixture()
            second.sessionRepository.adopt(resumable)
            second.engine.resume(resumable)

            val firstItemAfterResume = assertNotNull(second.engine.state.value.recognitionItem)
            assertEquals(
                interruptedItem.seed,
                firstItemAfterResume.seed,
                "the discarded item must come back, not be skipped past",
            )
            assertEquals(2, second.engine.state.value.itemsCompleted, "progress carries over, not reset to zero")
        }

    @Test
    fun `a resumed session plays only the remaining slots, not the whole plan again`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()
            val plannedTotal = fixture.engine.state.value.itemsPlanned
            repeat(3) { fixture.answerCurrentCorrectly() }

            fixture.audioInterruptions.emit(AudioInterruptionEvent.PermanentLoss)
            fixture.awaitPaused()
            fixture.engine.awaitPersistence()
            val resumable = assertNotNull(fixture.sessionRepository.findResumable())

            val second = Fixture()
            second.sessionRepository.adopt(resumable)
            second.engine.resume(resumable)

            var guard = 0
            while (!second.engine.state.value.isFinished && guard < plannedTotal * 2) {
                second.answerCurrentCorrectly()
                guard++
            }
            second.engine.awaitPersistence()

            assertTrue(second.engine.state.value.isFinished)
            // 3 scored before the interruption + 1 abandoned; the resumed run replays from slot 3.
            assertEquals(
                plannedTotal - 3,
                second.attemptRepository.all.count { !it.isAbandoned },
                "a resumed session must not re-ask the slots already answered",
            )
        }

    @Test
    fun `completing a session clears its resume state so it is never offered again`() =
        runBlocking {
            val fixture = Fixture()
            fixture.startSession()
            fixture.answerCurrentCorrectly()

            fixture.audioInterruptions.emit(AudioInterruptionEvent.PermanentLoss)
            fixture.awaitPaused()
            fixture.engine.awaitPersistence()
            assertNotNull(fixture.sessionRepository.findResumable())

            fixture.engine.resumeAfterPause()
            var guard = 0
            while (!fixture.engine.state.value.isFinished && guard < 200) {
                fixture.answerCurrentCorrectly()
                guard++
            }
            fixture.engine.awaitPersistence()

            assertTrue(fixture.engine.state.value.isFinished)
            assertNull(
                fixture.sessionRepository.findResumable(),
                "a session that ran to completion must not keep offering itself for resume",
            )
        }
}
