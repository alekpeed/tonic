package com.tonic.feature.practice.engine

import com.tonic.core.engine.session.SkillWorkContext
import com.tonic.core.model.ids.SkillIds
import com.tonic.core.model.items.DifficultyAxis
import com.tonic.core.model.state.MasteryState
import com.tonic.core.model.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/09-BUILD-PLAN.md Stage 6: wires generate -> render -> pre-render ->
 * play -> answer -> record -> adapt end to end with no real UI. Uses the
 * real `:core:curriculum`/`:core:engine` pieces throughout - only the
 * repositories are in-memory fakes (Stage 5 already proved the real ones
 * correct against a database) and [FakeAudioPlayer] stands in for actual
 * device audio output, which cannot be exercised in this environment.
 */
class PracticeLoopEngineTest {
    private class Fixture {
        val attemptRepository = FakeAttemptRepository()
        val skillStateRepository = FakeSkillStateRepository(attemptRepository)
        val confusionRepository = FakeConfusionRepository()
        val sessionRepository = FakeSessionRepository()
        val audioPlayer = FakeAudioPlayer()
        val clock = Clock { Instant.EPOCH }
        val engine =
            PracticeLoopEngine(
                attemptRepository,
                skillStateRepository,
                confusionRepository,
                sessionRepository,
                audioPlayer,
                clock,
            )
    }

    private fun freshNode(skillId: com.tonic.core.model.ids.SkillId = SkillIds.M2_DEG_SET_1) =
        SkillWorkContext(skillId, axisLevels = DifficultyAxis.entries.associateWith { 0 }, totalAttempts = 0)

    /** Drives the engine with a responder that answers correctly with probability [accuracy], seeded for determinism. */
    private suspend fun drive(
        fixture: Fixture,
        accuracy: Double,
        seed: Long,
        thinkTimeMs: Long = 0,
    ) {
        val random = Random(seed)
        while (!fixture.engine.state.value.isFinished) {
            val item = fixture.engine.state.value.currentItem ?: break
            if (thinkTimeMs > 0) delay(thinkTimeMs)
            val correct = random.nextDouble() < accuracy
            val label =
                if (correct) {
                    item.targetDegree.degree.toString()
                } else {
                    item.activeDegrees
                        .filterNot { it == item.targetDegree }
                        .randomOrNull(random)
                        ?.degree
                        ?.toString()
                        ?: item.targetDegree.degree.toString()
                }
            fixture.engine.submitAnswer(label)
        }
    }

    @Test
    fun `starting a session generates and plays the first item`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )

            val state = fixture.engine.state.value
            assertNotNull(state.currentItem)
            assertEquals(1, fixture.audioPlayer.playedBuffers.size)
            assertFalse(state.isFinished)
        }

    @Test
    fun `submitAnswer records exactly one attempt per item and advances to a new item`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )
            val firstItem = fixture.engine.state.value.currentItem!!

            fixture.engine.submitAnswer(firstItem.targetDegree.degree.toString())

            assertEquals(1, fixture.attemptRepository.all.size)
            assertTrue(
                fixture.attemptRepository.all
                    .single()
                    .correct,
            )
            val secondItem = fixture.engine.state.value.currentItem
            assertNotNull(secondItem)
            assertTrue(fixture.audioPlayer.playedBuffers.size >= 2)
        }

    @Test
    fun `a session completes fully - every planned item gets exactly one scored attempt`() =
        runBlocking {
            val fixture = Fixture()
            // ~9 items/minute (SessionComposer) * 6 minutes clears 50 items comfortably.
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 6,
                rootSeed = 42L,
                now = Instant.EPOCH,
            )
            val planned = fixture.engine.state.value.itemsPlanned
            assertTrue(planned >= 50, "expected at least 50 planned items for a 6-minute session, got $planned")

            drive(fixture, accuracy = 0.7, seed = 42L)

            assertTrue(fixture.engine.state.value.isFinished)
            assertEquals(planned, fixture.attemptRepository.all.size)
            assertEquals(planned, fixture.engine.state.value.itemsCompleted)
            assertTrue(fixture.attemptRepository.all.none { it.isAbandoned })
        }

    @Test
    fun `a session is fully replayable from rootSeed - same seed and same responses reproduce identical items`() =
        runBlocking {
            val fixtureA = Fixture()
            fixtureA.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 4,
                rootSeed = 777L,
                now = Instant.EPOCH,
            )
            drive(fixtureA, accuracy = 0.65, seed = 999L)

            val fixtureB = Fixture()
            fixtureB.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 4,
                rootSeed = 777L,
                now = Instant.EPOCH,
            )
            drive(fixtureB, accuracy = 0.65, seed = 999L)

            val seedsA = fixtureA.attemptRepository.all.map { it.itemSeed }
            val seedsB = fixtureB.attemptRepository.all.map { it.itemSeed }
            assertEquals(
                seedsA,
                seedsB,
                "the same rootSeed and the same response sequence must generate identical items",
            )

            val targetsA = fixtureA.attemptRepository.all.map { it.targetLabel }
            val targetsB = fixtureB.attemptRepository.all.map { it.targetLabel }
            assertEquals(targetsA, targetsB)
        }

    @Test
    fun `pre-rendering keeps the visible cost of advancing to the next item well under the user's think time`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 3,
                rootSeed = 5L,
                now = Instant.EPOCH,
            )

            val thinkTimeMs = 30L
            val submitDurationsMs = mutableListOf<Long>()
            var guard = 0
            while (!fixture.engine.state.value.isFinished && guard < 30) {
                val item = fixture.engine.state.value.currentItem ?: break
                delay(thinkTimeMs) // simulated user think time - this is when pre-rendering has to finish
                val t0 = System.nanoTime()
                fixture.engine.submitAnswer(item.targetDegree.degree.toString())
                submitDurationsMs += (System.nanoTime() - t0) / 1_000_000
                guard++
            }

            val average = submitDurationsMs.average()
            // Report the measured value regardless of pass/fail, same as docs/10-TESTING.md §5's convention.
            assertTrue(
                average < thinkTimeMs,
                "expected submitAnswer's own cost (average ${average}ms) to stay well under the " +
                    "${thinkTimeMs}ms think-time window that pre-rendering had to work with - " +
                    "measured per-call: $submitDurationsMs",
            )
        }

    @Test
    fun `abandoning the current item discards it - recorded as abandoned, never scored or adapted`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )

            fixture.engine.abandonCurrentItem()

            val recorded = fixture.attemptRepository.all.single()
            assertTrue(recorded.isAbandoned)
            assertNull(recorded.responseLabel)
            assertFalse(recorded.correct)
            assertEquals(1, fixture.audioPlayer.stopCount)

            // Never adapted: the skill state is still exactly SkillState.initial(), as if this attempt
            // had never happened.
            val state = fixture.skillStateRepository.observe(SkillIds.M2_DEG_SET_1)
            assertEquals(MasteryState.LOCKED, state.first().masteryState)
            assertEquals(0, state.first().totalAttempts)
        }

    @Test
    fun `replay increments the replay count on the eventual attempt without recording its own attempt`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )
            val item = fixture.engine.state.value.currentItem!!

            fixture.engine.replay()
            fixture.engine.replay()
            assertTrue(fixture.attemptRepository.all.isEmpty(), "replay must not record an attempt")
            assertEquals(3, fixture.audioPlayer.playedBuffers.size, "1 auto-play on arrival + 2 replays")

            fixture.engine.submitAnswer(item.targetDegree.degree.toString())
            assertEquals(
                2,
                fixture.attemptRepository.all
                    .single()
                    .replayCount,
            )
        }

    @Test
    fun `submitAnswer with autoAdvance=false does not advance until proceedToNextItem is called`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )
            val item = fixture.engine.state.value.currentItem!!
            val wrongLabel =
                item.activeDegrees
                    .first { it != item.targetDegree }
                    .degree
                    .toString()

            fixture.engine.submitAnswer(wrongLabel, autoAdvance = false)

            assertEquals(1, fixture.attemptRepository.all.size, "the attempt is still recorded immediately")
            assertEquals(item, fixture.engine.state.value.currentItem, "must not have advanced past the answered item")

            fixture.engine.proceedToNextItem()
            assertTrue(
                fixture.engine.state.value.currentItem != item || fixture.engine.state.value.isFinished,
                "proceedToNextItem should now advance",
            )
        }

    @Test
    fun `playIncorrectContrast plays target-in-context, chosen note, then target again - not via replayCount`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 5,
                rootSeed = 1L,
                now = Instant.EPOCH,
            )
            val item = fixture.engine.state.value.currentItem!!
            val wrongDegree = item.activeDegrees.first { it != item.targetDegree }
            val wrongLabel = wrongDegree.degree.toString()

            fixture.engine.submitAnswer(wrongLabel, autoAdvance = false)
            val buffersBefore = fixture.audioPlayer.playedBuffers.size
            fixture.engine.playIncorrectContrast(wrongLabel)

            assertEquals(
                buffersBefore + 3,
                fixture.audioPlayer.playedBuffers.size,
                "target-in-context, chosen note, target again - three plays",
            )
            assertEquals(
                0,
                fixture.attemptRepository.all
                    .single()
                    .replayCount,
                "a system-triggered contrast replay must not be counted as a user replay",
            )

            fixture.engine.proceedToNextItem()
            assertTrue(fixture.engine.state.value.currentItem != item)
        }

    @Test
    fun `mastering a node unlocks its successor, seeded from its axis levels with cadence fade reduced one step`() =
        runBlocking {
            val fixture = Fixture()
            fixture.engine.start(
                freshNode(),
                dueReviews = emptyList(),
                sessionLengthMinutes = 90,
                rootSeed = 100L,
                now = Instant.EPOCH,
            )

            drive(fixture, accuracy = 0.95, seed = 100L)

            val degSet1 = fixture.skillStateRepository.observe(SkillIds.M2_DEG_SET_1).first()
            assertEquals(
                MasteryState.MASTERED,
                degSet1.masteryState,
                "expected mastery within a 90-minute session at 95% accuracy",
            )

            val degSet2 = fixture.skillStateRepository.observe(SkillIds.M2_DEG_SET_2).first()
            assertEquals(MasteryState.AVAILABLE, degSet2.masteryState)
            val expectedCadence = (degSet1.axisLevels.getValue(DifficultyAxis.CADENCE_FADE) - 1).coerceAtLeast(0)
            assertEquals(expectedCadence, degSet2.axisLevels[DifficultyAxis.CADENCE_FADE])
        }

    @Test
    fun `a cadence-dependent learner masters M2_FULL_DIATONIC, triggers the independence check, and fails it`() =
        runBlocking {
            val fixture = Fixture()
            val activeDegreeCount = 7
            fixture.engine.start(
                freshNode(SkillIds.M2_FULL_DIATONIC),
                dueReviews = emptyList(),
                sessionLengthMinutes = 200,
                rootSeed = 600L,
                now = Instant.EPOCH,
            )

            val random = Random(600L)
            var guard = 0
            var independenceCheckSeen = false
            var cadenceBeforeCheck: Int? = null
            while (!fixture.engine.state.value.isFinished && guard < 3000) {
                val state = fixture.engine.state.value
                val item = state.currentItem ?: break
                if (state.isIndependenceCheckProbe) {
                    if (!independenceCheckSeen) {
                        cadenceBeforeCheck =
                            fixture.skillStateRepository
                                .observe(SkillIds.M2_FULL_DIATONIC)
                                .first()
                                .axisLevels[DifficultyAxis.CADENCE_FADE]
                    }
                    independenceCheckSeen = true
                }
                val cadence = item.referencePlan.cadenceFadeLevel.level
                val p = if (cadence < 6) 0.92 else 1.0 / activeDegreeCount
                val correct = random.nextDouble() < p
                val label =
                    if (correct) {
                        item.targetDegree.degree.toString()
                    } else {
                        item.activeDegrees
                            .filterNot { it == item.targetDegree }
                            .random(random)
                            .degree
                            .toString()
                    }
                fixture.engine.submitAnswer(label)
                guard++
            }

            assertTrue(
                independenceCheckSeen,
                "expected the independence check to run automatically once M2_FULL_DIATONIC was mastered",
            )

            val finalState = fixture.skillStateRepository.observe(SkillIds.M2_FULL_DIATONIC).first()
            assertEquals(
                MasteryState.MASTERED,
                finalState.masteryState,
                "mastery itself is not revoked by failing the independence check",
            )
            assertEquals(6, cadenceBeforeCheck, "the check should have run at the node's mastered CADENCE_FADE level")
            assertEquals(
                5,
                finalState.axisLevels[DifficultyAxis.CADENCE_FADE],
                "a cadence-dependent learner (chance-level at L6) must fail the check, lowering the fade axis one step",
            )
        }
}
