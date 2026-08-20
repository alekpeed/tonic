package com.tonic.feature.practice.engine

import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * docs/07-ADAPTIVE-ENGINE.md §8: the minutes setting is a wall-clock bound the loop enforces at item
 * boundaries, not only an item-count estimate. Found through live use — the estimate assumes ~9
 * items/minute, a pace a deliberate beginner does not hit, so a "3 minute" session ran far past 3
 * minutes at under half its planned items.
 */
class SessionDeadlineTest {
    private class Fixture(
        var now: Instant = Instant.EPOCH,
    ) {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val confusionRepository = FakeConfusionRepository()
        val sessionRepository = FakeSessionRepository()
        val audioPlayer = FakeAudioPlayer()
        val audioInterruptions = FakeAudioInterruptions()
        val clock = Clock { now }
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
    }

    @Test
    fun `a session ends at its wall-clock budget, at an item boundary, with the bar reading complete`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                SkillWorkContext(
                    SkillIds.M2_DEG_SET_1,
                    axisLevels = DifficultyAxis.entries.associateWith { 0 },
                    totalAttempts = 0,
                ),
                dueReviews = emptyList(),
                sessionLengthMinutes = 3,
                rootSeed = 77L,
                now = fixture.now,
            )
            val planned = fixture.engine.state.value.itemsPlanned
            assertTrue(planned > 10, "a 3-minute plan should hold well over 10 items - got $planned")

            // A deliberate beginner: ~30 seconds per item, i.e. one third of the planned pace.
            var answered = 0
            while (!fixture.engine.state.value.isFinished && answered < planned) {
                val item = fixture.engine.state.value.currentItem ?: break
                fixture.now = fixture.now.plusSeconds(30)
                fixture.engine.submitAnswer(item.targetDegree.degree.toString())
                answered++
            }

            val state = fixture.engine.state.value
            assertTrue(state.isFinished, "the session must end once the budget is spent")
            assertEquals(6, answered, "3 minutes at 30s/item is 6 items, not the 27-item plan")
            assertEquals(
                state.itemsCompleted,
                state.itemsPlanned,
                "the cancelled remainder must not be reported as unfinished work - the budget was met",
            )

            val session = fixture.sessionRepository.findById(state.sessionId!!)
            assertEquals(answered, session?.completedItemCount)
            assertEquals(null, session?.resumeState, "a budget-completed session is complete, not resumable")
        }

    @Test
    fun `an interrupted session resumes with its remaining budget, not a fresh one`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                SkillWorkContext(
                    SkillIds.M2_DEG_SET_1,
                    axisLevels = DifficultyAxis.entries.associateWith { 0 },
                    totalAttempts = 0,
                ),
                dueReviews = emptyList(),
                sessionLengthMinutes = 3,
                rootSeed = 79L,
                now = fixture.now,
            )

            // Two items at 30 seconds each - one minute of the three spent.
            repeat(2) {
                val item = fixture.engine.state.value.currentItem!!
                fixture.now = fixture.now.plusSeconds(30)
                fixture.engine.submitAnswer(item.targetDegree.degree.toString())
            }
            fixture.engine.leaveSession()

            val saved = fixture.sessionRepository.findResumable()
            assertEquals(
                120L,
                saved?.resumeState?.budgetRemainingSeconds,
                "the session-length promise survives the interruption: 3 minutes minus the 1 spent",
            )

            // Hours later, the same session is resumed - the clock gap in between must not count.
            fixture.now = fixture.now.plusSeconds(3_600)
            val resumedAt = fixture.now
            fixture.engine.resume(saved!!)
            assertEquals(
                resumedAt.plusSeconds(120),
                fixture.engine.state.value.sessionEndsAt,
                "the resumed deadline is now + the stored remainder",
            )

            // The same deliberate pace spends the remaining 2 minutes in 4 items, then the budget ends it.
            var answered = 0
            while (!fixture.engine.state.value.isFinished && answered < 50) {
                val item = fixture.engine.state.value.currentItem ?: break
                fixture.now = fixture.now.plusSeconds(30)
                fixture.engine.submitAnswer(item.targetDegree.degree.toString())
                answered++
            }
            assertTrue(fixture.engine.state.value.isFinished)
            assertEquals(4, answered, "2 remaining minutes at 30s/item is 4 items")
        }

    @Test
    fun `a session finishing its plan before the budget is untouched by the deadline`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                SkillWorkContext(
                    SkillIds.M2_DEG_SET_1,
                    axisLevels = DifficultyAxis.entries.associateWith { 0 },
                    totalAttempts = 0,
                ),
                dueReviews = emptyList(),
                sessionLengthMinutes = 3,
                rootSeed = 78L,
                now = fixture.now,
            )
            val planned = fixture.engine.state.value.itemsPlanned

            // A quick responder: 2 seconds per item, well inside the budget.
            var answered = 0
            while (!fixture.engine.state.value.isFinished && answered < planned + 5) {
                val item = fixture.engine.state.value.currentItem ?: break
                fixture.now = fixture.now.plusSeconds(2)
                fixture.engine.submitAnswer(item.targetDegree.degree.toString())
                answered++
            }
            fixture.engine.awaitPersistence()

            assertTrue(fixture.engine.state.value.isFinished)
            assertEquals(planned, answered, "every planned item runs when the pace fits the budget")
        }
}
